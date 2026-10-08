package ist.alchm.smthsVanish.paper;

import org.bukkit.Bukkit;
import org.bukkit.block.Barrel;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.EnderChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

/**
 * Opening a chest, barrel or shulker runs the lid animation and the sound for everyone near, and
 * a trapped chest sends redstone. A vanished player gets a read-only copy instead, so the block
 * never learns it was opened. PremiumVanish reaches the same view-only result with a spectator
 * mode switch; a copy needs no game mode tricks.
 */
@NullMarked
final class SilentContainers implements Listener {
    private final SmthsVanishPaper plugin;

    SilentContainers(SmthsVanishPaper plugin) {
        this.plugin = plugin;
    }

    /** Marks our copies so clicks in them can be cancelled. */
    private static final class View implements InventoryHolder {
        private @org.jspecify.annotations.Nullable Inventory inventory;

        @Override
        public Inventory getInventory() {
            return java.util.Objects.requireNonNull(inventory);
        }
    }

    @EventHandler(priority = EventPriority.LOW)
    void open(PlayerInteractEvent event) {
        Player p = event.getPlayer();
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK
                || event.getHand() != EquipmentSlot.HAND
                || event.getClickedBlock() == null
                || p.isSneaking()
                || !plugin.settings().silentContainers()
                || !plugin.vanish().isVanished(p.getUniqueId())
                || !p.hasPermission("smthsvanish.silentchest")) {
            return;
        }
        BlockState state = event.getClickedBlock().getState(false);
        if (state instanceof EnderChest) {
            event.setUseInteractedBlock(Event.Result.DENY);
            p.openInventory(p.getEnderChest());
            return;
        }
        Inventory real;
        if (state instanceof Chest chest) real = chest.getInventory();
        else if (state instanceof Barrel barrel) real = barrel.getInventory();
        else if (state instanceof ShulkerBox box) real = box.getInventory();
        else return;

        event.setUseInteractedBlock(Event.Result.DENY);
        View holder = new View();
        Inventory copy = Bukkit.createInventory(holder, real.getSize(), plugin.messages().get("silent-open"));
        holder.inventory = copy;
        ItemStack[] contents = real.getContents();
        for (int i = 0; i < contents.length; i++) {
            if (contents[i] != null) contents[i] = contents[i].clone();
        }
        copy.setContents(contents);
        p.openInventory(copy);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void click(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof View) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    void drag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder(false) instanceof View) event.setCancelled(true);
    }
}
