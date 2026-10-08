package ist.alchm.smthsVanish.paper;

import java.util.List;
import org.bukkit.configuration.file.FileConfiguration;
import org.jspecify.annotations.NullMarked;

/** An immutable view of config.yml. A reload builds a new one. */
@NullMarked
public record Settings(
        String redisUri,
        int redisTimeoutMs,
        int maxLevel,
        boolean vanishStaffWhenRedisDown,
        boolean hideJoinQuitMessages,
        boolean fakeMessagesOnToggle,
        boolean blockChat,
        boolean blockItemPickup,
        boolean blockPhysicalInteract,
        boolean blockMobTargeting,
        boolean blockGameEvents,
        boolean ignoreSleep,
        boolean disableCollision,
        boolean stopSpawning,
        boolean invulnerable,
        boolean hideAdvancements,
        boolean hideDeathMessages,
        boolean silentContainers,
        boolean flight,
        boolean nightVision,
        boolean actionBar,
        boolean hideFromServerList,
        boolean hideFromTabComplete,
        boolean disguiseEnabled,
        boolean maskRank,
        String maskRankGroup,
        boolean maskNameInPackets,
        List<String> randomNames) {

    static Settings from(FileConfiguration c) {
        return new Settings(
                c.getString("redis.uri", "redis://localhost:6379/0"),
                c.getInt("redis.timeout-ms", 2000),
                Math.max(1, c.getInt("levels.max", 10)),
                c.getBoolean("vanish.vanish-staff-when-redis-down", true),
                c.getBoolean("vanish.hide-join-quit-messages", true),
                c.getBoolean("vanish.fake-messages-on-toggle", true),
                c.getBoolean("vanish.block-chat", true),
                c.getBoolean("vanish.block-item-pickup", true),
                c.getBoolean("vanish.block-physical-interact", true),
                c.getBoolean("vanish.block-mob-targeting", true),
                c.getBoolean("vanish.block-game-events", true),
                c.getBoolean("vanish.ignore-sleep", true),
                c.getBoolean("vanish.disable-collision", true),
                c.getBoolean("vanish.stop-spawning", true),
                c.getBoolean("vanish.invulnerable", true),
                c.getBoolean("vanish.hide-advancements", true),
                c.getBoolean("vanish.hide-death-messages", true),
                c.getBoolean("vanish.silent-containers", true),
                c.getBoolean("vanish.flight", true),
                c.getBoolean("vanish.night-vision", false),
                c.getBoolean("vanish.action-bar", true),
                c.getBoolean("vanish.hide-from-server-list", true),
                c.getBoolean("vanish.hide-from-tab-complete", true),
                c.getBoolean("disguise.enabled", true),
                c.getBoolean("disguise.mask-rank", true),
                c.getString("disguise.mask-rank-group", "default"),
                c.getBoolean("disguise.mask-name-in-packets", true),
                List.copyOf(c.getStringList("disguise.random-names")));
    }
}
