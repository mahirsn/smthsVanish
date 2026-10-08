package ist.alchm.smthsVanish.paper.api;

import java.util.UUID;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;

/**
 * For other plugins: {@code Bukkit.getServicesManager().load(SmthsVanishApi.class)}. Plugins that
 * only need a yes/no can keep reading the {@code "vanished"} metadata instead. Off this server,
 * read the Redis hash {@code smthsvanish:player:<uuid>} (fields vanished, level, disguise).
 */
@NullMarked
public interface SmthsVanishApi {
    boolean isVanished(UUID player);

    /** 0 when not vanished. */
    int level(UUID player);

    /** Vanish levels and the see permission both count. */
    boolean canSee(Player viewer, Player target);

    /** The name {@code viewer} knows {@code target} by: the disguise name, or the real name. */
    String visibleName(Player viewer, Player target);
}
