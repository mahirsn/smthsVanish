package ist.alchm.smthsVanish.paper.hook;

import ist.alchm.smthsVanish.paper.SmthsVanishPaper;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * %smthsvanish_vanished%, _level%, _see_level%, _interact%, _pickup%, _disguised%, _name% (disguise name when
 * disguised) and _online% (online players this viewer can see, on this server).
 */
@NullMarked
public final class Placeholders extends PlaceholderExpansion {
    private final SmthsVanishPaper plugin;

    public Placeholders(SmthsVanishPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public String getIdentifier() {
        return "smthsvanish";
    }

    @Override
    public String getAuthor() {
        return "mahirsn";
    }

    @Override
    public String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(@Nullable OfflinePlayer offline, String params) {
        if (!(offline instanceof Player player) || !player.isOnline()) return null;
        var vanish = plugin.vanish();
        return switch (params) {
            case "vanished" -> Boolean.toString(vanish.isVanished(player.getUniqueId()));
            case "level" -> Integer.toString(vanish.state(player.getUniqueId()).level());
            case "see_level" -> Integer.toString(vanish.seeLevel(player));
            case "interact" -> Boolean.toString(vanish.state(player.getUniqueId()).interact());
            case "pickup" -> Boolean.toString(vanish.state(player.getUniqueId()).pickup());
            case "disguised" -> Boolean.toString(plugin.disguises().disguise(player.getUniqueId()) != null);
            case "name" -> plugin.disguises().visibleName(player);
            case "online" -> Long.toString(Bukkit.getOnlinePlayers().stream().filter(p -> vanish.canSee(player, p)).count());
            default -> null;
        };
    }
}
