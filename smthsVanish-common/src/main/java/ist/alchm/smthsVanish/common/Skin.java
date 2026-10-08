package ist.alchm.smthsVanish.common;

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/** A signed Mojang texture property. */
@NullMarked
public record Skin(String value, @Nullable String signature) {}
