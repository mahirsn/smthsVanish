package ist.alchm.smthsVanish.velocity;

import com.google.inject.Inject;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.DisconnectEvent;
import com.velocitypowered.api.event.connection.PostLoginEvent;
import com.velocitypowered.api.event.proxy.ProxyInitializeEvent;
import com.velocitypowered.api.event.proxy.ProxyPingEvent;
import com.velocitypowered.api.event.proxy.ProxyShutdownEvent;
import com.velocitypowered.api.plugin.annotation.DataDirectory;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;
import ist.alchm.smthsVanish.common.Levels;
import ist.alchm.smthsVanish.common.Skin;
import ist.alchm.smthsVanish.common.SkinsRestorerLookup;
import ist.alchm.smthsVanish.common.VanishState;
import ist.alchm.smthsVanish.common.VanishStore;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;

/**
 * The proxy half. Backends do the hiding; the proxy owns what a backend cannot see: the network
 * login (auto-vanish must be stored before the first backend reads it), the server list ping,
 * which the proxy answers itself, and SkinsRestorer data when it runs in proxy mode.
 */
@NullMarked
public final class SmthsVanishVelocity {
    private final ProxyServer proxy;
    private final Logger logger;
    private final Path dataDirectory;
    private final Map<UUID, VanishState> online = new ConcurrentHashMap<>();
    private @Nullable VanishStore store;
    private int maxLevel = 10;
    private boolean autoVanish = true;
    private boolean hideFromPing = true;

    @Inject
    public SmthsVanishVelocity(ProxyServer proxy, Logger logger, @DataDirectory Path dataDirectory) {
        this.proxy = proxy;
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Subscribe
    public void onInit(ProxyInitializeEvent event) throws IOException {
        Properties cfg = loadConfig();
        maxLevel = Math.max(1, Integer.parseInt(cfg.getProperty("levels.max", "10").trim()));
        autoVanish = Boolean.parseBoolean(cfg.getProperty("auto-vanish", "true").trim());
        hideFromPing = Boolean.parseBoolean(cfg.getProperty("hide-from-ping", "true").trim());
        VanishStore redis = VanishStore.connect(
                cfg.getProperty("redis.uri", "redis://localhost:6379/0").trim(),
                Duration.ofMillis(Long.parseLong(cfg.getProperty("redis.timeout-ms", "2000").trim())));
        store = redis;
        redis.subscribe(this::onRemoteChange);
        // Backends in SkinsRestorer proxy mode cannot read skins; the proxy answers for them.
        if (proxy.getPluginManager().isLoaded("skinsrestorer")) {
            redis.serveSkins(this::skinOf, task -> proxy.getScheduler().buildTask(this, task).schedule());
        }
    }

    @Subscribe
    public void onShutdown(ProxyShutdownEvent event) {
        if (store != null) store.close();
    }

    /**
     * Velocity connects the player to the first backend only after this event's task completes,
     * so the auto-vanish write lands in Redis before that backend's pre-login reads it.
     */
    @Subscribe(priority = 16383) // EARLY
    public EventTask onPostLogin(PostLoginEvent event) {
        Player player = event.getPlayer();
        return EventTask.async(() -> {
            VanishStore redis = Objects.requireNonNull(store);
            UUID id = player.getUniqueId();
            try {
                VanishState state = redis.load(id);
                if (autoVanish && !state.vanished() && player.hasPermission("smthsvanish.auto")) {
                    int level = Math.max(1, Levels.highest(player::hasPermission, Levels.LEVEL, maxLevel));
                    state = state.withVanish(true, level);
                    redis.save(id, state).toCompletableFuture().join();
                }
                online.put(id, state);
            } catch (RuntimeException e) {
                // The backend fails closed for staff when it cannot read Redis either.
                logger.warn("Could not read or write vanish state of {}", player.getUsername(), e);
            }
        });
    }

    @Subscribe
    public void onDisconnect(DisconnectEvent event) {
        online.remove(event.getPlayer().getUniqueId());
    }

    @Subscribe(priority = Short.MIN_VALUE) // LAST: after MiniMOTD and Maintenance set the ping
    public void onPing(ProxyPingEvent event) {
        if (!hideFromPing) return;
        long hidden = online.entrySet().stream()
                .filter(e -> e.getValue().vanished() && proxy.getPlayer(e.getKey()).isPresent())
                .count();
        if (hidden == 0) return;
        var ping = event.getPing().asBuilder();
        ping.onlinePlayers((int) Math.max(0, ping.getOnlinePlayers() - hidden));
        var sample = ping.getSamplePlayers().stream()
                .filter(p -> !online.getOrDefault(p.getId(), VanishState.NONE).vanished())
                .toList();
        ping.clearSamplePlayers().samplePlayers(sample);
        event.setPing(ping.build());
    }

    private void onRemoteChange(UUID id) {
        if (proxy.getPlayer(id).isEmpty()) return;
        proxy.getScheduler().buildTask(this, () -> {
            try {
                online.put(id, Objects.requireNonNull(store).load(id));
            } catch (RuntimeException e) {
                logger.warn("Could not refresh vanish state of {}", id, e);
            }
        }).schedule();
    }

    private Optional<Skin> skinOf(String name, @Nullable UUID backendId) {
        try {
            UUID id = proxy.getPlayer(name).map(Player::getUniqueId).orElse(backendId);
            return SkinsRestorerLookup.find(id, name);
        } catch (Exception e) {
            logger.warn("SkinsRestorer lookup for {} failed", name, e);
            return Optional.empty();
        }
    }

    /** Vanished per the last state this proxy read. For other proxy plugins via the plugin instance. */
    public boolean isVanished(UUID player) {
        return online.getOrDefault(player, VanishState.NONE).vanished();
    }

    private Properties loadConfig() throws IOException {
        Files.createDirectories(dataDirectory);
        Path file = dataDirectory.resolve("config.properties");
        if (Files.notExists(file)) {
            try (InputStream in = Objects.requireNonNull(getClass().getResourceAsStream("/config.properties"))) {
                Files.copy(in, file);
            }
        }
        Properties p = new Properties();
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            p.load(r);
        }
        return p;
    }
}
