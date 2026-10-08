package ist.alchm.smthsVanish.common;

import java.util.HashMap;
import java.util.Map;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * What the network knows about one player. It lives in a Redis hash, so every backend and the
 * proxy read the same answer the moment the player arrives.
 *
 * <p>{@code level} is written by the backend that vanished the player, from their
 * {@code smthsvanish.level.N} permissions. Readers without a permission view (the proxy, other
 * plugins) use it as is.
 *
 * <p>{@code interact} and {@code pickup} are switches the vanished player turns on only for the
 * moment they need them. Each new vanish starts with both off.
 */
@NullMarked
public record VanishState(boolean vanished, int level, @Nullable Disguise disguise, boolean interact, boolean pickup) {
    public static final VanishState NONE = new VanishState(false, 0, null, false, false);

    public VanishState(boolean vanished, int level, @Nullable Disguise disguise) {
        this(vanished, level, disguise, false, false);
    }

    public record Disguise(String name, @Nullable String skinValue, @Nullable String skinSignature) {}

    public boolean isEmpty() {
        return !vanished && disguise == null;
    }

    /** A change of vanish turns the switches off; a level change alone keeps them. */
    public VanishState withVanish(boolean vanished, int level) {
        boolean keep = vanished == this.vanished;
        return new VanishState(vanished, level, disguise, keep && interact, keep && pickup);
    }

    public VanishState withDisguise(@Nullable Disguise disguise) {
        return new VanishState(vanished, level, disguise, interact, pickup);
    }

    public VanishState withInteract(boolean interact) {
        return new VanishState(vanished, level, disguise, interact, pickup);
    }

    public VanishState withPickup(boolean pickup) {
        return new VanishState(vanished, level, disguise, interact, pickup);
    }

    /**
     * Every field, empty when unset, so one HSET replaces the whole state. A DEL before the HSET
     * would let two quick saves interleave on the connection.
     */
    Map<String, String> toHash() {
        Map<String, String> out = new HashMap<>();
        out.put("vanished", vanished ? "1" : "0");
        out.put("level", Integer.toString(level));
        out.put("interact", interact ? "1" : "0");
        out.put("pickup", pickup ? "1" : "0");
        out.put("disguise", disguise == null ? "" : disguise.name());
        out.put("skin", disguise == null || disguise.skinValue() == null ? "" : disguise.skinValue());
        out.put("skin_sig", disguise == null || disguise.skinSignature() == null ? "" : disguise.skinSignature());
        return out;
    }

    static VanishState fromHash(Map<String, String> hash) {
        if (hash.isEmpty()) return NONE;
        int level;
        try {
            level = Integer.parseInt(hash.getOrDefault("level", "0"));
        } catch (NumberFormatException e) {
            level = 0;
        }
        String name = emptyToNull(hash.get("disguise"));
        Disguise disguise = name == null
                ? null
                : new Disguise(name, emptyToNull(hash.get("skin")), emptyToNull(hash.get("skin_sig")));
        return new VanishState(
                "1".equals(hash.get("vanished")), level, disguise, "1".equals(hash.get("interact")), "1".equals(hash.get("pickup")));
    }

    private static @Nullable String emptyToNull(@Nullable String s) {
        return s == null || s.isEmpty() ? null : s;
    }
}
