package ist.alchm.smthsVanish.common;

import java.util.function.Predicate;
import org.jspecify.annotations.NullMarked;

/** Vanish levels: a viewer sees a vanished player only when their see level reaches its level. */
@NullMarked
public final class Levels {
    public static final String LEVEL = "smthsvanish.level.";
    public static final String SEE = "smthsvanish.see.";

    private Levels() {}

    /** The highest {@code prefix + n} the subject has, 0 when none. Level 1 is the floor for vanish. */
    public static int highest(Predicate<String> hasPermission, String prefix, int max) {
        for (int n = max; n >= 1; n--) {
            if (hasPermission.test(prefix + n)) return n;
        }
        return 0;
    }

    public static boolean canSee(int viewerSeeLevel, int targetLevel) {
        return viewerSeeLevel >= Math.max(1, targetLevel);
    }
}
