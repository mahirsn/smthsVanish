package ist.alchm.smthsVanish.common;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.skinsrestorer.api.SkinsRestorer;
import net.skinsrestorer.api.SkinsRestorerProvider;
import net.skinsrestorer.api.property.SkinProperty;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * The skin a player uses on this network: the one they picked with SkinsRestorer, else their own
 * account skin. Only load this class when SkinsRestorer is installed.
 */
@NullMarked
public final class SkinsRestorerLookup {
    private SkinsRestorerLookup() {}

    /** True when the API answers here. On a backend in proxy mode it usually does not. */
    public static boolean available() {
        try {
            SkinsRestorerProvider.get();
            return true;
        } catch (IllegalStateException | NoClassDefFoundError e) {
            return false;
        }
    }

    /**
     * Blocking; may call Mojang. SkinsRestorer keys skins by UUID, and a name can have several:
     * the one this server knows ({@code knownId}), the offline-mode one, the Mojang one. The first
     * with a skin the player picked wins; else the Mojang account's own skin.
     */
    public static Optional<Skin> find(@Nullable UUID knownId, String name) throws Exception {
        SkinsRestorer api = SkinsRestorerProvider.get();
        var players = api.getPlayerStorage();
        List<UUID> candidates = new ArrayList<>();
        if (knownId != null) candidates.add(knownId);
        candidates.add(offlineId(name));
        Optional<UUID> premium = api.getCacheStorage().getUUID(name, false);
        premium.ifPresent(candidates::add);
        for (UUID id : candidates) {
            if (players.getSkinIdOfPlayer(id).isPresent()) return skin(players.getSkinForPlayer(id, name));
        }
        return premium.isPresent() ? skin(players.getSkinForPlayer(premium.get(), name)) : Optional.empty();
    }

    /** The UUID an offline-mode server gives this name. */
    static UUID offlineId(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8));
    }

    private static Optional<Skin> skin(Optional<SkinProperty> property) {
        return property.map(p -> new Skin(p.getValue(), p.getSignature()));
    }
}
