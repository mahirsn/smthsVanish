package ist.alchm.smthsVanish.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CommonTest {
    @Test
    void highestPicksTheTopGrantedLevel() {
        Set<String> perms = Set.of("smthsvanish.level.2", "smthsvanish.level.5");
        assertEquals(5, Levels.highest(perms::contains, Levels.LEVEL, 10));
        assertEquals(2, Levels.highest(perms::contains, Levels.LEVEL, 4));
        assertEquals(0, Levels.highest(perms::contains, Levels.SEE, 10));
    }

    @Test
    void seeLevelMustReachTheVanishLevel() {
        assertTrue(Levels.canSee(3, 3));
        assertTrue(Levels.canSee(4, 3));
        assertFalse(Levels.canSee(2, 3));
        // Level 0 is stored for players vanished without a level permission; it still hides them.
        assertFalse(Levels.canSee(0, 0));
        assertTrue(Levels.canSee(1, 0));
    }

    @Test
    void hashRoundTrip() {
        VanishState plain = new VanishState(true, 4, null);
        assertEquals(plain, VanishState.fromHash(plain.toHash()));
        VanishState full = new VanishState(false, 0, new VanishState.Disguise("Steve", "v", "s"));
        assertEquals(full, VanishState.fromHash(full.toHash()));
        VanishState noSkin = new VanishState(true, 1, new VanishState.Disguise("Alex", null, null));
        assertEquals(noSkin, VanishState.fromHash(noSkin.toHash()));
        assertEquals(VanishState.NONE, VanishState.fromHash(Map.of()));
        assertEquals(0, VanishState.fromHash(Map.of("vanished", "1", "level", "x")).level());
        // A full HSET of a state without disguise must clear an older disguise.
        assertEquals(plain, VanishState.fromHash(new VanishState(true, 4, null).toHash()));
        assertEquals("", plain.toHash().get("disguise"));
    }
}
