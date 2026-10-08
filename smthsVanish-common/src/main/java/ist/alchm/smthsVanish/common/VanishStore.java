package ist.alchm.smthsVanish.common;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.BiFunction;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Network-wide vanish and disguise state. One hash per player, kept with no expiry so a vanished
 * player stays vanished across restarts, plus one pub/sub channel that names the player whose
 * state changed.
 *
 * <p>Skins: a backend whose SkinsRestorer runs in proxy mode cannot read skins itself. It asks
 * on {@link #SKIN_REQUEST}; the proxy answers into a short-lived hash and names it on
 * {@link #SKIN_RESPONSE}.
 */
@NullMarked
public final class VanishStore implements AutoCloseable {
    public static final String KEY_PREFIX = "smthsvanish:player:";
    public static final String CHANNEL = "smthsvanish:update";
    public static final String SKIN_REQUEST = "smthsvanish:skin-request";
    public static final String SKIN_RESPONSE = "smthsvanish:skin-response";
    private static final String SKIN_PREFIX = "smthsvanish:skin:";
    /** Short, so a skin a player changes reaches new disguises soon. */
    private static final long SKIN_TTL_SECONDS = 60;

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final StatefulRedisPubSubConnection<String, String> pubSub;
    private final Map<String, Consumer<String>> handlers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Optional<Skin>>> pendingSkins = new ConcurrentHashMap<>();

    private VanishStore(RedisClient client) {
        this.client = client;
        this.connection = client.connect();
        this.pubSub = client.connectPubSub();
        pubSub.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                Consumer<String> handler = handlers.get(channel);
                if (handler != null) handler.accept(message);
            }
        });
    }

    public static VanishStore connect(String uri, Duration timeout) {
        RedisURI redisUri = RedisURI.create(uri);
        redisUri.setTimeout(timeout);
        return new VanishStore(RedisClient.create(redisUri));
    }

    private void listen(String channel, Consumer<String> handler) {
        handlers.put(channel, handler);
        pubSub.sync().subscribe(channel);
    }

    // --- player state ------------------------------------------------------------------------------

    /** Blocking read. Call it off the server thread, for example from a pre-login event. */
    public VanishState load(UUID id) {
        return VanishState.fromHash(connection.sync().hgetall(KEY_PREFIX + id));
    }

    /** Writes the state, then tells every server that it changed. */
    public CompletionStage<Long> save(UUID id, VanishState state) {
        String key = KEY_PREFIX + id;
        var async = connection.async();
        // One command per save: Lettuce sends commands in call order, so the last save wins.
        CompletionStage<?> write = state.isEmpty() ? async.del(key) : async.hset(key, state.toHash());
        return write.thenCompose(ignored -> async.publish(CHANNEL, id.toString()));
    }

    public void subscribe(Consumer<UUID> onChange) {
        listen(CHANNEL, message -> {
            try {
                onChange.accept(UUID.fromString(message));
            } catch (IllegalArgumentException ignored) {
                // Someone else wrote to our channel.
            }
        });
    }

    // --- skins -------------------------------------------------------------------------------------

    /** Backend side: call once before {@link #requestSkin}. */
    public void listenForSkins() {
        listen(SKIN_RESPONSE, name -> {
            CompletableFuture<Optional<Skin>> pending = pendingSkins.remove(name);
            if (pending != null) readSkin(name).thenAccept(skin -> pending.complete(skin.orElse(Optional.empty())));
        });
    }

    /**
     * The skin the proxy found for {@code name}: empty when it found none. Fails with a timeout
     * when no proxy answers, so the caller can fall back to another source. {@code knownId} is
     * the UUID this server has for the name, if any; the proxy only knows online players.
     */
    public CompletableFuture<Optional<Skin>> requestSkin(String name, @Nullable UUID knownId, Duration timeout) {
        String key = name.toLowerCase(Locale.ROOT);
        return readSkin(key).thenCompose(cached -> {
            if (cached.isPresent()) return CompletableFuture.completedFuture(cached.get());
            CompletableFuture<Optional<Skin>> answer = pendingSkins.computeIfAbsent(key, k -> new CompletableFuture<>());
            connection.async().publish(SKIN_REQUEST, knownId == null ? name : name + " " + knownId);
            return answer.orTimeout(timeout.toMillis(), TimeUnit.MILLISECONDS)
                    .whenComplete((skin, error) -> pendingSkins.remove(key, answer));
        });
    }

    /**
     * Proxy side: answers skin requests with {@code resolver} (name as typed, the backend's UUID
     * for it or null), which may block, on {@code executor}.
     */
    public void serveSkins(BiFunction<String, @Nullable UUID, Optional<Skin>> resolver, Executor executor) {
        listen(SKIN_REQUEST, message -> executor.execute(() -> {
            String[] parts = message.split(" ", 2);
            String name = parts[0];
            UUID knownId = null;
            if (parts.length == 2) {
                try {
                    knownId = UUID.fromString(parts[1]);
                } catch (IllegalArgumentException ignored) {
                    // Treat as unknown.
                }
            }
            Optional<Skin> skin = resolver.apply(name, knownId);
            Map<String, String> hash = new HashMap<>();
            hash.put("value", skin.map(Skin::value).orElse(""));
            hash.put("signature", skin.map(Skin::signature).orElse(""));
            var async = connection.async();
            String lower = name.toLowerCase(Locale.ROOT);
            String key = SKIN_PREFIX + lower;
            async.hset(key, hash)
                    .thenCompose(ignored -> async.expire(key, SKIN_TTL_SECONDS))
                    .thenCompose(ignored -> async.publish(SKIN_RESPONSE, lower));
        }));
    }

    /** Outer empty: nothing cached. Inner empty: the proxy looked and found no skin. */
    private CompletableFuture<Optional<Optional<Skin>>> readSkin(String name) {
        return connection.async().hgetall(SKIN_PREFIX + name).toCompletableFuture().thenApply(hash -> {
            if (hash.isEmpty()) return Optional.empty();
            String value = hash.getOrDefault("value", "");
            String signature = hash.getOrDefault("signature", "");
            return Optional.of(value.isEmpty() ? Optional.empty() : Optional.of(new Skin(value, signature.isEmpty() ? null : signature)));
        });
    }

    @Override
    public void close() {
        pubSub.close();
        connection.close();
        client.shutdown();
    }
}
