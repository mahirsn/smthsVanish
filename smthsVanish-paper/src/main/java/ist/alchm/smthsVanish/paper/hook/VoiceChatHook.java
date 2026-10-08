package ist.alchm.smthsVanish.paper.hook;

import de.maxhenkel.voicechat.api.BukkitVoicechatService;
import de.maxhenkel.voicechat.api.VoicechatConnection;
import de.maxhenkel.voicechat.api.VoicechatPlugin;
import de.maxhenkel.voicechat.api.events.EntitySoundPacketEvent;
import de.maxhenkel.voicechat.api.events.EventRegistration;
import de.maxhenkel.voicechat.api.events.LocationalSoundPacketEvent;
import de.maxhenkel.voicechat.api.events.SoundPacketEvent;
import de.maxhenkel.voicechat.api.events.StaticSoundPacketEvent;
import ist.alchm.smthsVanish.paper.SmthsVanishPaper;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Simple Voice Chat sends proximity voice to everyone in range, whether they see the speaker or
 * not. Each packet is dropped for receivers who cannot see the vanished speaker. Group voice is
 * kept: joining a group is the speaker's own choice.
 */
@NullMarked
public final class VoiceChatHook implements VoicechatPlugin {
    private final SmthsVanishPaper plugin;

    private VoiceChatHook(SmthsVanishPaper plugin) {
        this.plugin = plugin;
    }

    public static boolean register(SmthsVanishPaper plugin) {
        BukkitVoicechatService service = plugin.getServer().getServicesManager().load(BukkitVoicechatService.class);
        if (service == null) return false;
        service.registerPlugin(new VoiceChatHook(plugin));
        return true;
    }

    @Override
    public String getPluginId() {
        return "smthsvanish";
    }

    @Override
    public void registerEvents(EventRegistration registration) {
        registration.registerEvent(EntitySoundPacketEvent.class, this::filter);
        registration.registerEvent(LocationalSoundPacketEvent.class, this::filter);
        registration.registerEvent(StaticSoundPacketEvent.class, this::filter);
    }

    private void filter(SoundPacketEvent<?> event) {
        if (SoundPacketEvent.SOURCE_GROUP.equals(event.getSource())) return;
        Player sender = player(event.getSenderConnection());
        Player receiver = player(event.getReceiverConnection());
        if (sender == null || receiver == null) return;
        if (plugin.vanish().isVanished(sender.getUniqueId()) && !plugin.vanish().canSee(receiver, sender)) {
            event.cancel();
        }
    }

    private static @Nullable Player player(@Nullable VoicechatConnection connection) {
        if (connection == null) return null;
        return connection.getPlayer().getPlayer() instanceof Player p ? p : null;
    }
}
