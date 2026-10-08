package ist.alchm.smthsVanish.paper;

import ist.alchm.smthsVanish.common.Levels;
import ist.alchm.smthsVanish.common.VanishState;
import ist.alchm.smthsVanish.common.VanishStore;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.jspecify.annotations.NullMarked;

/**
 * Who is vanished here, and who may see them. Entity and tab-list hiding uses
 * {@link Player#hidePlayer(org.bukkit.plugin.Plugin, Player)}: Paper keeps a target hidden while
 * any plugin hides it, so a minigame calling showPlayer for its own reasons cannot reveal ours.
 */
@NullMarked
public final class VanishService {
    public static final String USE = "smthsvanish.use";
    /** SuperVanish and PremiumVanish set this; TAB, smthsSMP and many others read it. */
    public static final String METADATA = "vanished";

    private final SmthsVanishPaper plugin;
    private final VanishStore store;
    /** States of players online here, filled at pre-login before the player exists. */
    private final Map<UUID, VanishState> states = new ConcurrentHashMap<>();
    private final Set<UUID> unreadable = ConcurrentHashMap.newKeySet();
    /** Flight we turned on, so unvanish does not take away flight another plugin gave. */
    private final Set<UUID> grantedFlight = ConcurrentHashMap.newKeySet();
    /** States whose Redis write has not landed yet. */
    private final Map<UUID, VanishState> unsaved = new ConcurrentHashMap<>();

    VanishService(SmthsVanishPaper plugin, VanishStore store) {
        this.plugin = plugin;
        this.store = store;
    }

    // --- queries -------------------------------------------------------------------------------

    public VanishState state(UUID id) {
        return states.getOrDefault(id, VanishState.NONE);
    }

    public boolean isVanished(UUID id) {
        return state(id).vanished();
    }

    public int level(Player player) {
        return Levels.highest(player::hasPermission, Levels.LEVEL, plugin.settings().maxLevel());
    }

    public int seeLevel(Player player) {
        return Levels.highest(player::hasPermission, Levels.SEE, plugin.settings().maxLevel());
    }

    public boolean canSee(Player viewer, Player target) {
        if (viewer.getUniqueId().equals(target.getUniqueId())) return true;
        VanishState s = state(target.getUniqueId());
        return !s.vanished() || Levels.canSee(seeLevel(viewer), s.level());
    }

    // --- login flow -----------------------------------------------------------------------------

    /** Runs on the pre-login thread. Blocking Redis is fine there. */
    void preload(UUID id) {
        try {
            states.put(id, store.load(id));
            unreadable.remove(id);
        } catch (RuntimeException e) {
            states.remove(id);
            unreadable.add(id);
            plugin.getLogger().log(Level.WARNING, "Could not read vanish state of " + id + "; Redis down?", e);
        }
    }

    /** Called from PlayerJoinEvent at LOWEST: before Paper sends the tab entry or spawns the entity. */
    void join(Player player) {
        UUID id = player.getUniqueId();
        if (unreadable.remove(id)
                && plugin.settings().vanishStaffWhenRedisDown()
                && player.hasPermission(USE)) {
            // Fail closed for staff only. Not saved: Redis is the thing that failed.
            states.put(id, VanishState.NONE.withVanish(true, Math.max(1, level(player))));
            plugin.messages().send(player, "redis-down-vanished");
        }
        VanishState s = state(id);
        if (s.vanished()) {
            int level = Math.max(1, level(player));
            if (level != s.level()) setState(player, s.withVanish(true, level));
        }
        refreshTarget(player);
        refreshViewer(player);
    }

    void quit(Player player) {
        forget(player.getUniqueId());
    }

    void forget(UUID id) {
        states.remove(id);
        unreadable.remove(id);
        grantedFlight.remove(id);
    }

    // --- changes ----------------------------------------------------------------------------------

    public void setVanished(Player player, boolean vanished) {
        VanishState before = state(player.getUniqueId());
        if (before.vanished() == vanished) return;
        if (plugin.settings().fakeMessagesOnToggle()) {
            var line = plugin.messages().get(vanished ? "fake-quit" : "fake-join", Messages.player(player.getName()));
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                // Those who keep seeing the player get no fake line; they know.
                if (!viewer.equals(player) && !Levels.canSee(seeLevel(viewer), Math.max(1, level(player)))) {
                    viewer.sendMessage(line);
                }
            }
        }
        setState(player, before.withVanish(vanished, vanished ? Math.max(1, level(player)) : 0));
        refreshTarget(player);
        if (vanished && plugin.settings().blockMobTargeting()) {
            for (var mob : player.getWorld().getNearbyEntitiesByType(org.bukkit.entity.Mob.class, player.getLocation(), 64)) {
                if (player.equals(mob.getTarget())) mob.setTarget(null);
            }
        }
    }

    /** Stores a new state for an online player and tells the network. */
    public void setState(Player player, VanishState state) {
        states.put(player.getUniqueId(), state);
        unsaved.put(player.getUniqueId(), state);
        save(player, state, 1);
    }

    /**
     * A lost write is a leak: the next backend would read the old state. So a failed save is
     * retried, also after the player left, until it lands or a newer state replaces it.
     */
    private void save(Player player, VanishState state, int attempt) {
        UUID id = player.getUniqueId();
        store.save(id, state).whenComplete((ok, error) -> {
            if (error == null) {
                unsaved.remove(id, state);
                return;
            }
            if (unsaved.get(id) != state) return;
            if (attempt == 1) {
                plugin.getLogger().log(Level.WARNING, "Could not save vanish state of " + player.getName() + "; retrying", error);
                if (player.isOnline()) player.getScheduler().run(plugin, t -> plugin.messages().send(player, "save-failed"), null);
            }
            plugin.getServer().getAsyncScheduler().runDelayed(
                    plugin, t -> save(player, state, attempt + 1), Math.min(30L, 2L * attempt), TimeUnit.SECONDS);
        });
    }

    /** Another server or the proxy changed this player's state. Runs on the main thread. */
    void remoteUpdate(Player player, VanishState state) {
        if (state.equals(state(player.getUniqueId()))) return;
        states.put(player.getUniqueId(), state);
        refreshTarget(player);
        plugin.disguises().refresh(player);
    }

    /** LuckPerms recalculated this player's permissions: their level, see level or both moved. */
    public void permissionsChanged(Player player) {
        VanishState s = state(player.getUniqueId());
        if (s.vanished()) {
            int level = Math.max(1, level(player));
            if (level != s.level()) setState(player, s.withVanish(true, level));
            refreshTarget(player);
        }
        refreshViewer(player);
    }

    // --- applying ---------------------------------------------------------------------------------

    /** Re-applies how everyone else sees {@code target}, and the effects on the target itself. */
    @SuppressWarnings("deprecation") // Metadata is deprecated, but TAB and smthsSMP still read "vanished" from it.
    public void refreshTarget(Player target) {
        boolean vanished = isVanished(target.getUniqueId());
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(target)) continue;
            if (vanished && !canSee(viewer, target)) viewer.hidePlayer(plugin, target);
            else viewer.showPlayer(plugin, target);
        }
        if (vanished) target.setMetadata(METADATA, new FixedMetadataValue(plugin, true));
        else target.removeMetadata(METADATA, plugin);
        applyEffects(target, vanished);
    }

    /** Re-applies which vanished players {@code viewer} may see. */
    public void refreshViewer(Player viewer) {
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.equals(viewer) || !isVanished(target.getUniqueId())) continue;
            if (canSee(viewer, target)) viewer.showPlayer(plugin, target);
            else viewer.hidePlayer(plugin, target);
        }
    }

    private void applyEffects(Player p, boolean on) {
        Settings s = plugin.settings();
        if (s.ignoreSleep()) p.setSleepingIgnored(on);
        if (s.disableCollision()) p.setCollidable(!on);
        if (s.stopSpawning()) p.setAffectsSpawning(!on);
        if (s.invulnerable()) p.setInvulnerable(on);
        if (s.nightVision()) {
            if (on) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, PotionEffect.INFINITE_DURATION, 0, false, false, false));
            else p.removePotionEffect(PotionEffectType.NIGHT_VISION);
        }
        if (s.flight() && on && !p.getAllowFlight() && p.hasPermission("smthsvanish.fly")) {
            p.setAllowFlight(true);
            grantedFlight.add(p.getUniqueId());
        } else if (!on && grantedFlight.remove(p.getUniqueId())
                && (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE)) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }
    }
}
