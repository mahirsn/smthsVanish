package ist.alchm.smthsVanish.paper.disguise;

import ist.alchm.smthsVanish.common.VanishState;
import ist.alchm.smthsVanish.paper.Messages;
import ist.alchm.smthsVanish.paper.SmthsVanishPaper;
import ist.alchm.smthsVanish.paper.hook.LuckPermsHook;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Disguise changes only what other clients are told: the profile name and skin in the tab entry
 * and spawn, and the real name in chat, tab, team and entity text (see {@link DisguisePackets}).
 * The server keeps the real profile, so CoreProtect, LiteBans and HuskSync still log the real
 * player. Viewers with {@link #SEE} get the real packets.
 */
@NullMarked
public final class DisguiseService {
    public static final String SEE = "smthsvanish.disguise.see";
    private static final Pattern VALID_NAME = Pattern.compile("[A-Za-z0-9_]{3,16}");

    private final SmthsVanishPaper plugin;
    private final @Nullable LuckPermsHook luckPerms;
    private final SkinFetcher skins = new SkinFetcher();
    private final boolean packetsAvailable;
    /** Disguises of players online here. Read on netty threads by the packet listener. */
    private final Map<UUID, VanishState.Disguise> active = new ConcurrentHashMap<>();
    /** Real name of each disguised online player, for the mask. */
    private final Map<UUID, String> realNames = new ConcurrentHashMap<>();
    private volatile NameMask mask = NameMask.EMPTY;

    public DisguiseService(SmthsVanishPaper plugin, @Nullable LuckPermsHook luckPerms, boolean packetsAvailable) {
        this.plugin = plugin;
        this.luckPerms = luckPerms;
        this.packetsAvailable = packetsAvailable;
    }

    public boolean available() {
        return packetsAvailable && plugin.settings().disguiseEnabled();
    }

    // --- queries used by packets, placeholders and the tab-complete filter -----------------------

    public boolean anyActive() {
        return !active.isEmpty();
    }

    public VanishState.@Nullable Disguise disguise(UUID id) {
        return active.get(id);
    }

    /** True when {@code viewer} must get the disguise instead of {@code target}'s real identity. */
    public boolean masksFor(Player viewer, Player target) {
        return active.containsKey(target.getUniqueId())
                && !viewer.getUniqueId().equals(target.getUniqueId())
                && !viewer.hasPermission(SEE);
    }

    /** The mask for packets sent to {@code viewer}; a disguised viewer still reads their own real name. */
    public NameMask maskFor(UUID viewer) {
        NameMask all = mask;
        if (!active.containsKey(viewer)) return all;
        Map<String, String> others = new HashMap<>();
        realNames.forEach((id, real) -> {
            VanishState.Disguise d = active.get(id);
            if (!id.equals(viewer) && d != null) others.put(real, d.name());
        });
        return new NameMask(others);
    }

    public String visibleName(Player player) {
        VanishState.Disguise d = active.get(player.getUniqueId());
        return d == null ? player.getName() : d.name();
    }

    // --- lifecycle ---------------------------------------------------------------------------------

    public void join(Player player) {
        VanishState.Disguise d = plugin.vanish().state(player.getUniqueId()).disguise();
        if (d == null || !available()) return;
        track(player, d);
        if (luckPerms != null && plugin.settings().maskRank()) luckPerms.maskRank(player, plugin.settings().maskRankGroup());
    }

    /**
     * Paper broadcasts the quit line after the quit event, so the mask has to outlive the event
     * by a tick. A player who is already back (fast reconnect) keeps theirs.
     */
    public void quit(Player player) {
        UUID id = player.getUniqueId();
        Bukkit.getGlobalRegionScheduler().runDelayed(plugin, task -> {
            if (Bukkit.getPlayer(id) == null) untrack(id);
        }, 1L);
    }

    /** The stored state changed elsewhere; bring this server's view in line with it. */
    public void refresh(Player player) {
        VanishState.Disguise want = available() ? plugin.vanish().state(player.getUniqueId()).disguise() : null;
        if (java.util.Objects.equals(want, active.get(player.getUniqueId()))) return;
        if (want == null) {
            untrack(player.getUniqueId());
            if (luckPerms != null) luckPerms.unmaskRank(player);
        } else {
            track(player, want);
            if (luckPerms != null && plugin.settings().maskRank()) luckPerms.maskRank(player, plugin.settings().maskRankGroup());
        }
        resend(player);
    }

    // --- commands ----------------------------------------------------------------------------------

    /** Picks a free name from the config list, or null when every one is taken. */
    public @Nullable String randomName() {
        List<String> names = plugin.settings().randomNames().stream().filter(this::isFree).toList();
        return names.isEmpty() ? null : names.get(ThreadLocalRandom.current().nextInt(names.size()));
    }

    public static boolean isValidName(String name) {
        return VALID_NAME.matcher(name).matches();
    }

    /** Free on this server: no online player has it as real name or disguise. */
    public boolean isFree(String name) {
        if (Bukkit.getPlayerExact(name) != null) return false;
        return active.values().stream().noneMatch(d -> d.name().equalsIgnoreCase(name));
    }

    /** Looks up the skin off-thread, then applies on the player's thread. */
    public void disguise(Player player, String name, String skinOwner) {
        skins.fetch(skinOwner).thenAccept(skin -> player.getScheduler().run(plugin, task -> {
            if (!player.isOnline()) return;
            if (skin.isEmpty()) plugin.messages().send(player, "disguise-skin-failed");
            VanishState.Disguise d = new VanishState.Disguise(
                    name, skin.map(SkinFetcher.Skin::value).orElse(null), skin.map(SkinFetcher.Skin::signature).orElse(null));
            plugin.vanish().setState(player, plugin.vanish().state(player.getUniqueId()).withDisguise(d));
            refresh(player);
            plugin.messages().send(player, "disguise-on", Messages.value("name", name));
        }, null));
    }

    public boolean undisguise(Player player) {
        if (plugin.vanish().state(player.getUniqueId()).disguise() == null) return false;
        plugin.vanish().setState(player, plugin.vanish().state(player.getUniqueId()).withDisguise(null));
        refresh(player);
        return true;
    }

    // --- internals ---------------------------------------------------------------------------------

    private void track(Player player, VanishState.Disguise d) {
        active.put(player.getUniqueId(), d);
        realNames.put(player.getUniqueId(), player.getName());
        rebuildMask();
    }

    private void untrack(UUID id) {
        active.remove(id);
        realNames.remove(id);
        rebuildMask();
    }

    private void rebuildMask() {
        if (!plugin.settings().maskNameInPackets()) {
            mask = NameMask.EMPTY;
            return;
        }
        Map<String, String> map = new HashMap<>();
        realNames.forEach((id, real) -> Optional.ofNullable(active.get(id)).ifPresent(d -> map.put(real, d.name())));
        mask = new NameMask(map);
    }

    /**
     * Clients cache the profile from the first tab entry. Hiding and showing again makes Paper send
     * a fresh tab entry and spawn, which the packet listener rewrites. Only viewers who see the
     * player now are touched, so vanish is never undone here.
     */
    private void resend(Player target) {
        List<Player> viewers = Bukkit.getOnlinePlayers().stream()
                .filter(v -> !v.equals(target) && v.canSee(target) && !v.hasPermission(SEE))
                .map(v -> (Player) v)
                .toList();
        if (viewers.isEmpty()) return;
        viewers.forEach(v -> v.hidePlayer(plugin, target));
        target.getScheduler().runDelayed(plugin, task -> {
            for (Player v : viewers) {
                if (v.isOnline() && target.isOnline() && plugin.vanish().canSee(v, target)) v.showPlayer(plugin, target);
            }
        }, null, 2L);
    }

}
