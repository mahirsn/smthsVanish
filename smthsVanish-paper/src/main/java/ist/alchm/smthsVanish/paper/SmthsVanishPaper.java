package ist.alchm.smthsVanish.paper;

import com.github.retrooper.packetevents.PacketEvents;
import ist.alchm.smthsVanish.common.VanishStore;
import ist.alchm.smthsVanish.paper.api.SmthsVanishApi;
import ist.alchm.smthsVanish.paper.disguise.DisguisePackets;
import ist.alchm.smthsVanish.paper.disguise.DisguiseService;
import ist.alchm.smthsVanish.paper.hook.LuckPermsHook;
import ist.alchm.smthsVanish.paper.hook.Placeholders;
import ist.alchm.smthsVanish.paper.hook.VoiceChatHook;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
public final class SmthsVanishPaper extends JavaPlugin implements SmthsVanishApi {
    private volatile @Nullable Settings settings;
    private volatile @Nullable Messages messages;
    private @Nullable VanishStore store;
    private @Nullable VanishService vanish;
    private @Nullable DisguiseService disguises;
    private @Nullable LuckPermsHook luckPerms;
    private @Nullable DisguisePackets packets;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reload();
        Settings s = settings();
        VanishStore redis = VanishStore.connect(s.redisUri(), Duration.ofMillis(s.redisTimeoutMs()));
        this.store = redis;
        this.vanish = new VanishService(this, redis);

        var plugins = getServer().getPluginManager();
        if (plugins.isPluginEnabled("LuckPerms")) {
            luckPerms = new LuckPermsHook(this);
            luckPerms.subscribe();
        }
        boolean packetEvents = plugins.isPluginEnabled("packetevents");
        this.disguises = new DisguiseService(this, luckPerms, packetEvents);
        if (packetEvents) {
            packets = new DisguisePackets(disguises);
            PacketEvents.getAPI().getEventManager().registerListener(packets);
        } else {
            getLogger().warning("packetevents is missing: disguise is off.");
        }
        if (plugins.isPluginEnabled("PlaceholderAPI")) new Placeholders(this).register();
        if (plugins.isPluginEnabled("voicechat") && !VoiceChatHook.register(this)) {
            getLogger().warning("voicechat is enabled but its API service is missing; voice is not filtered.");
        }

        plugins.registerEvents(new VanishListener(this, vanish), this);
        plugins.registerEvents(new SilentContainers(this), this);
        plugins.registerEvents(new Interactions(this), this);
        Commands.register(this);
        getServer().getServicesManager().register(SmthsVanishApi.class, this, this, ServicePriority.Normal);

        redis.subscribe(this::onRemoteChange);
        getServer().getGlobalRegionScheduler().runAtFixedRate(this, task -> actionBars(), 40L, 40L);

        // A /reload or a late enable: players already online were never pre-loaded.
        for (Player p : Bukkit.getOnlinePlayers()) {
            getServer().getAsyncScheduler().runNow(this, task -> {
                vanish.preload(p.getUniqueId());
                p.getScheduler().run(this, t -> {
                    vanish.join(p);
                    disguises.join(p);
                }, null);
            });
        }
    }

    @Override
    public void onDisable() {
        // Hidden players stay hidden until they leave. Showing them here would reveal every
        // vanished moderator during a restart or a plugin reload.
        getServer().getAsyncScheduler().cancelTasks(this);
        getServer().getGlobalRegionScheduler().cancelTasks(this);
        getServer().getServicesManager().unregisterAll(this);
        if (packets != null) PacketEvents.getAPI().getEventManager().unregisterListener(packets);
        if (luckPerms != null) luckPerms.close();
        if (store != null) store.close();
    }

    void reload() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        settings = Settings.from(getConfig());
        messages = Messages.load(this);
    }

    private void onRemoteChange(UUID id) {
        VanishStore redis = Objects.requireNonNull(store);
        Player player = Bukkit.getPlayer(id);
        if (player == null) return;
        // Our own saves come back here too; remoteUpdate ignores a state we already hold.
        getServer().getAsyncScheduler().runNow(this, task -> {
            var state = redis.load(id);
            player.getScheduler().run(this, t -> vanish().remoteUpdate(player, state), null);
        });
    }

    private void actionBars() {
        if (!settings().actionBar()) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            var state = vanish().state(p.getUniqueId());
            if (state.vanished()) {
                p.sendActionBar(messages().get("action-bar",
                        Messages.value("level", state.level()),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("interact", messages().get(state.interact() ? "state-on" : "state-off")),
                        net.kyori.adventure.text.minimessage.tag.resolver.Placeholder.component("pickup", messages().get(state.pickup() ? "state-on" : "state-off"))));
            }
        }
    }

    // --- accessors ---------------------------------------------------------------------------------

    public Settings settings() {
        return Objects.requireNonNull(settings);
    }

    public Messages messages() {
        return Objects.requireNonNull(messages);
    }

    public VanishService vanish() {
        return Objects.requireNonNull(vanish);
    }

    public DisguiseService disguises() {
        return Objects.requireNonNull(disguises);
    }

    // --- SmthsVanishApi ----------------------------------------------------------------------------

    @Override
    public boolean isVanished(UUID player) {
        return vanish().isVanished(player);
    }

    @Override
    public int level(UUID player) {
        var state = vanish().state(player);
        return state.vanished() ? state.level() : 0;
    }

    @Override
    public boolean canSee(Player viewer, Player target) {
        return vanish().canSee(viewer, target);
    }

    @Override
    public String visibleName(Player viewer, Player target) {
        return disguises().masksFor(viewer, target) ? disguises().visibleName(target) : target.getName();
    }
}
