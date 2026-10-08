package ist.alchm.smthsVanish.paper;

import com.destroystokyo.paper.event.player.PlayerPickupExperienceEvent;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.hanging.HangingPlaceEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.vehicle.VehicleDamageEvent;
import org.bukkit.event.vehicle.VehicleDestroyEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * A vanished player leaves no trace in the world: every change they could make is cancelled.
 * They turn interaction (or item pickup) on with a command only for the moment they need it, and
 * the next vanish starts with both off again. A permission alone never turns them on for good.
 */
@NullMarked
final class Interactions implements Listener {
    private static final long HINT_EVERY_MS = 3000;

    private final SmthsVanishPaper plugin;
    private final Map<UUID, Long> lastHint = new ConcurrentHashMap<>();

    Interactions(SmthsVanishPaper plugin) {
        this.plugin = plugin;
    }

    private boolean blocked(@Nullable Entity actor) {
        if (!(actor instanceof Player p) || !plugin.settings().blockInteraction()) return false;
        var state = plugin.vanish().state(p.getUniqueId());
        return state.vanished() && !state.interact();
    }

    /** The player, or the player who shot the projectile. */
    private static @Nullable Entity source(Entity entity) {
        return entity instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter ? shooter : entity;
    }

    private void cancel(Cancellable event, @Nullable Entity actor) {
        if (!blocked(actor)) return;
        event.setCancelled(true);
        Player p = (Player) actor;
        long now = System.currentTimeMillis();
        Long last = lastHint.get(p.getUniqueId());
        if (last == null || now - last > HINT_EVERY_MS) {
            lastHint.put(p.getUniqueId(), now);
            p.sendActionBar(plugin.messages().get("interact-blocked"));
        }
    }

    /** Runs after SilentContainers (LOW), so a silent copy is already open when this cancels. */
    @EventHandler(priority = EventPriority.HIGH)
    void interact(PlayerInteractEvent event) {
        // A swing in the air changes nothing.
        if (event.getAction() == Action.LEFT_CLICK_AIR) return;
        if (!blocked(event.getPlayer())) return;
        // Plates, tripwire and farmland: no hint, the player only walked.
        if (event.getAction() == Action.PHYSICAL) {
            event.setCancelled(true);
            return;
        }
        cancel(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void interactEntity(PlayerInteractEntityEvent event) {
        cancel(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void breakBlock(BlockBreakEvent event) {
        cancel(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void placeBlock(BlockPlaceEvent event) {
        cancel(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void bucketEmpty(PlayerBucketEmptyEvent event) {
        cancel(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void bucketFill(PlayerBucketFillEvent event) {
        cancel(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void drop(PlayerDropItemEvent event) {
        cancel(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void damage(EntityDamageByEntityEvent event) {
        cancel(event, source(event.getDamager()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void launch(ProjectileLaunchEvent event) {
        cancel(event, source(event.getEntity()));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void hangingBreak(HangingBreakByEntityEvent event) {
        Entity remover = event.getRemover();
        cancel(event, remover == null ? null : source(remover));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void hangingPlace(HangingPlaceEvent event) {
        cancel(event, event.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void vehicleDamage(VehicleDamageEvent event) {
        Entity attacker = event.getAttacker();
        cancel(event, attacker == null ? null : source(attacker));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void vehicleDestroy(VehicleDestroyEvent event) {
        Entity attacker = event.getAttacker();
        cancel(event, attacker == null ? null : source(attacker));
    }

    // --- pickup has its own switch -----------------------------------------------------------------

    private boolean pickupBlocked(Entity entity) {
        if (!(entity instanceof Player p) || !plugin.settings().blockItemPickup()) return false;
        var state = plugin.vanish().state(p.getUniqueId());
        return state.vanished() && !state.pickup();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void pickup(EntityPickupItemEvent event) {
        if (pickupBlocked(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    void experience(PlayerPickupExperienceEvent event) {
        if (pickupBlocked(event.getPlayer())) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    void quit(org.bukkit.event.player.PlayerQuitEvent event) {
        lastHint.remove(event.getPlayer().getUniqueId());
    }
}
