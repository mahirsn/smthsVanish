package ist.alchm.smthsVanish.common;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.pubsub.RedisPubSubAdapter;
import io.lettuce.core.pubsub.StatefulRedisPubSubConnection;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import org.jspecify.annotations.NullMarked;

/**
 * Network-wide vanish and disguise state. One hash per player, kept with no expiry so a vanished
 * player stays vanished across restarts, plus one pub/sub channel that names the player whose
 * state changed.
 */
@NullMarked
public final class VanishStore implements AutoCloseable {
    public static final String KEY_PREFIX = "smthsvanish:player:";
    public static final String CHANNEL = "smthsvanish:update";

    private final RedisClient client;
    private final StatefulRedisConnection<String, String> connection;
    private final StatefulRedisPubSubConnection<String, String> pubSub;

    private VanishStore(RedisClient client) {
        this.client = client;
        this.connection = client.connect();
        this.pubSub = client.connectPubSub();
    }

    public static VanishStore connect(String uri, Duration timeout) {
        RedisURI redisUri = RedisURI.create(uri);
        redisUri.setTimeout(timeout);
        return new VanishStore(RedisClient.create(redisUri));
    }

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

    /** Every vanished or disguised player on the network, online or not. */
    public Map<UUID, VanishState> loadAll() {
        var sync = connection.sync();
        Map<UUID, VanishState> out = new java.util.HashMap<>();
        for (String key : sync.keys(KEY_PREFIX + "*")) {
            try {
                out.put(UUID.fromString(key.substring(KEY_PREFIX.length())), VanishState.fromHash(sync.hgetall(key)));
            } catch (IllegalArgumentException ignored) {
                // Not one of ours.
            }
        }
        return out;
    }

    public void subscribe(Consumer<UUID> onChange) {
        pubSub.addListener(new RedisPubSubAdapter<>() {
            @Override
            public void message(String channel, String message) {
                try {
                    onChange.accept(UUID.fromString(message));
                } catch (IllegalArgumentException ignored) {
                    // Someone else wrote to our channel.
                }
            }
        });
        pubSub.sync().subscribe(CHANNEL);
    }

    @Override
    public void close() {
        pubSub.close();
        connection.close();
        client.shutdown();
    }
}
