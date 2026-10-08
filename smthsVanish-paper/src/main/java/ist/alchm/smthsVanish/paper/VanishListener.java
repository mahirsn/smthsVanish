package ist.alchm.smthsVanish.paper;

import com.destroystokyo.paper.event.server.AsyncTabCompleteEvent;
import com.destroystokyo.paper.event.server.PaperServerListPingEvent;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockReceiveGameEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerAdvancementDoneEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.TabCompleteEvent;
import org.jspecify.annotations.NullMarked;

@NullMarked
final class VanishListener implements Listener {
    private final SmthsVanishPaper plugin;
    private final VanishService vanish;

    VanishListener(SmthsVanishPaper plugin, VanishService vanish) {
        this.plugin = plugin;
        this.vanish = vanish;
    }

    // --- login and quit ---------------------------------------------------------------------------

    /** The state must be in memory before the join event, which runs on the main thread. */
    @EventHandler(priority = EventPriority.LOWEST)
    void preLogin(AsyncPlayerPreLoginEvent event) {
        vanish.preload(event.getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void preLoginDenied(AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            Bukkit.getGlobalRegionScheduler().execute(plugin, () -> {
                if (Bukkit.getPlayer(event.getUniqueId()) == null) vanish.forget(event.getUniqueId());
            });
        }
    }

    /**
     * LOWEST: Paper sends the tab entry and starts entity tracking only after the join event, so
     * hiding here means no viewer gets a single packet about the player.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    void join(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        vanish.join(player);
        plugin.disguises().join(player);
        if (vanish.isVanished(player.getUniqueId()) && plugin.settings().hideJoinQuitMessages()) {
            event.joinMessage(null);
        }
    }

    /** HIGHEST, not MONITOR: plugins that set the message at HIGH must not put it back. */
    @EventHandler(priority = EventPriority.HIGHEST)
    void joinMessage(PlayerJoinEvent event) {
        if (vanish.isVanished(event.getPlayer().getUniqueId()) && plugin.settings().hideJoinQuitMessages()) {
            event.joinMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void quitMessage(PlayerQuitEvent event) {
        if (vanish.isVanished(event.getPlayer().getUniqueId()) && plugin.settings().hideJoinQuitMessages()) {
            event.quitMessage(null);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void quit(PlayerQuitEvent event) {
        plugin.disguises().quit(event.getPlayer());
        vanish.quit(event.getPlayer());
    }

    // --- things a hidden player must not do -------------------------------------------------------

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    void chat(AsyncChatEvent event) {
        Player p = event.getPlayer();
        if (plugin.settings().blockChat() && vanish.isVanished(p.getUniqueId()) && !p.hasPermission("smthsvanish.chat")) {
            event.setCancelled(true);
            plugin.messages().send(p, "chat-blocked");
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void target(EntityTargetLivingEntityEvent event) {
        if (plugin.settings().blockMobTargeting()
                && event.getTarget() instanceof Player p
                && vanish.isVanished(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void gameEvent(BlockReceiveGameEvent event) {
        if (plugin.settings().blockGameEvents()
                && event.getEntity() instanceof Player p
                && vanish.isVanished(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }


    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void hunger(org.bukkit.event.entity.FoodLevelChangeEvent event) {
        if (plugin.settings().invulnerable()
                && event.getEntity() instanceof Player p
                && vanish.isVanished(p.getUniqueId())
                && event.getFoodLevel() < p.getFoodLevel()) {
            event.setCancelled(true);
        }
    }

    /** Frost Walker ice would draw a moving trail under a hidden player. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void frostWalker(org.bukkit.event.block.EntityBlockFormEvent event) {
        if (plugin.settings().blockGameEvents()
                && event.getEntity() instanceof Player p
                && vanish.isVanished(p.getUniqueId())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void advancement(PlayerAdvancementDoneEvent event) {
        if (plugin.settings().hideAdvancements() && vanish.isVanished(event.getPlayer().getUniqueId())) {
            event.message(null);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void death(PlayerDeathEvent event) {
        if (plugin.settings().hideDeathMessages() && vanish.isVanished(event.getPlayer().getUniqueId())) {
            event.deathMessage(null);
        }
    }

    // --- lists that name players ------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST)
    void ping(PaperServerListPingEvent event) {
        if (!plugin.settings().hideFromServerList()) return;
        int hidden = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (vanish.isVanished(p.getUniqueId())) hidden++;
        }
        if (hidden == 0) return;
        event.setNumPlayers(Math.max(0, event.getNumPlayers() - hidden));
        event.getListedPlayers().removeIf(info -> vanish.isVanished(info.id()));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void asyncTab(AsyncTabCompleteEvent event) {
        if (!plugin.settings().hideFromTabComplete() || !event.isHandled()) return;
        Set<String> hidden = hiddenNames(event.getSender());
        if (!hidden.isEmpty()) event.completions().removeIf(c -> hidden.contains(c.suggestion().toLowerCase(Locale.ROOT)));
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    void tab(TabCompleteEvent event) {
        if (!plugin.settings().hideFromTabComplete()) return;
        Set<String> hidden = hiddenNames(event.getSender());
        if (hidden.isEmpty()) return;
        List<String> kept = event.getCompletions().stream()
                .filter(c -> !hidden.contains(c.toLowerCase(Locale.ROOT)))
                .toList();
        event.setCompletions(kept);
    }

    /** Lower-case names this sender must not learn: hidden players, and real names behind disguises. */
    private Set<String> hiddenNames(CommandSender sender) {
        if (!(sender instanceof Player viewer)) return Set.of();
        return Bukkit.getOnlinePlayers().stream()
                .filter(p -> !p.equals(viewer))
                .filter(p -> !vanish.canSee(viewer, p) || plugin.disguises().masksFor(viewer, p))
                .map(p -> p.getName().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }
}
