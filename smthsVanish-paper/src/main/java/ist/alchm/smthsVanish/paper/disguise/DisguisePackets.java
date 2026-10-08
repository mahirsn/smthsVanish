package ist.alchm.smthsVanish.paper.disguise;

import com.github.retrooper.packetevents.event.PacketListenerAbstract;
import com.github.retrooper.packetevents.event.PacketListenerPriority;
import com.github.retrooper.packetevents.event.PacketSendEvent;
import com.github.retrooper.packetevents.protocol.chat.ChatType;
import com.github.retrooper.packetevents.protocol.chat.message.ChatMessage_v1_19_3;
import com.github.retrooper.packetevents.protocol.entity.data.EntityData;
import com.github.retrooper.packetevents.protocol.packettype.PacketType;
import com.github.retrooper.packetevents.protocol.player.TextureProperty;
import com.github.retrooper.packetevents.protocol.player.UserProfile;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerActionBar;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDisguisedChat;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerInfoUpdate;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerResetScore;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleSubtitle;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSetTitleText;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSystemChatMessage;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerTeams;
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerUpdateScore;
import ist.alchm.smthsVanish.common.VanishState;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Rewrites outgoing packets so a viewer without {@link DisguiseService#SEE} only learns the
 * disguise: the profile in tab entries (name and skin), and the real name anywhere it appears as
 * text — chat, tab display names, team entries and prefixes (name tags), score holders, titles and
 * entity text such as name-tag displays. Runs on netty threads, so it only reads concurrent state.
 */
@NullMarked
public final class DisguisePackets extends PacketListenerAbstract {
    private final DisguiseService disguises;

    public DisguisePackets(DisguiseService disguises) {
        super(PacketListenerPriority.HIGHEST);
        this.disguises = disguises;
    }

    @Override
    public void onPacketSend(PacketSendEvent event) {
        if (!disguises.anyActive()) return;
        if (!(event.getPlayer() instanceof Player viewer) || viewer.hasPermission(DisguiseService.SEE)) return;
        var type = event.getPacketType();
        boolean changed;
        if (type == PacketType.Play.Server.PLAYER_INFO_UPDATE) {
            changed = playerInfo(new WrapperPlayServerPlayerInfoUpdate(event), viewer.getUniqueId());
        } else {
            NameMask mask = disguises.maskFor(viewer.getUniqueId());
            if (mask.isEmpty()) return;
            changed = text(event, type, mask);
        }
        if (changed) event.markForReEncode(true);
    }

    private boolean playerInfo(WrapperPlayServerPlayerInfoUpdate packet, UUID viewer) {
        boolean changed = false;
        NameMask mask = disguises.maskFor(viewer);
        for (var entry : packet.getEntries()) {
            UUID id = entry.getProfileId();
            VanishState.Disguise d = id.equals(viewer) ? null : disguises.disguise(id);
            if (d == null) continue;
            UserProfile profile = entry.getGameProfile();
            if (profile != null && profile.getName() != null && !d.name().equals(profile.getName())) {
                List<TextureProperty> textures = new ArrayList<>();
                if (d.skinValue() != null) textures.add(new TextureProperty("textures", d.skinValue(), d.skinSignature()));
                entry.setGameProfile(new UserProfile(id, d.name(), textures));
                changed = true;
            }
            Component display = entry.getDisplayName();
            if (display != null) {
                Component masked = mask.apply(display);
                if (masked != display) {
                    entry.setDisplayName(masked);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static boolean text(PacketSendEvent event, com.github.retrooper.packetevents.protocol.packettype.PacketTypeCommon type, NameMask mask) {
        if (type == PacketType.Play.Server.SYSTEM_CHAT_MESSAGE) {
            var p = new WrapperPlayServerSystemChatMessage(event);
            Component masked = mask.apply(p.getMessage());
            if (masked == p.getMessage()) return false;
            p.setMessage(masked);
            return true;
        }
        if (type == PacketType.Play.Server.DISGUISED_CHAT) {
            var p = new WrapperPlayServerDisguisedChat(event);
            boolean changed = false;
            Component masked = mask.apply(p.getMessage());
            if (masked != p.getMessage()) {
                p.setMessage(masked);
                changed = true;
            }
            return bound(p.getChatFormatting(), mask) | changed;
        }
        if (type == PacketType.Play.Server.CHAT_MESSAGE) {
            var p = new WrapperPlayServerChatMessage(event);
            if (!(p.getMessage() instanceof ChatMessage_v1_19_3 m)) return false;
            boolean changed = bound(m.getChatFormatting(), mask);
            // The signed body cannot change; unsigned content is what the client then shows.
            Component shown = m.getUnsignedChatContent().orElse(m.getChatContent());
            Component masked = mask.apply(shown);
            if (masked != shown) {
                m.setUnsignedChatContent(masked);
                changed = true;
            }
            return changed;
        }
        if (type == PacketType.Play.Server.TEAMS) {
            var p = new WrapperPlayServerTeams(event);
            boolean changed = false;
            Optional<WrapperPlayServerTeams.ScoreBoardTeamInfo> info = p.getTeamInfo();
            if (info.isPresent()) {
                var i = info.get();
                Component prefix = mask.apply(i.getPrefix());
                Component suffix = mask.apply(i.getSuffix());
                Component display = mask.apply(i.getDisplayName());
                if (prefix != i.getPrefix() || suffix != i.getSuffix() || display != i.getDisplayName()) {
                    i.setPrefix(prefix);
                    i.setSuffix(suffix);
                    i.setDisplayName(display);
                    changed = true;
                }
            }
            if (p.getPlayers() != null && !p.getPlayers().isEmpty()) {
                List<String> entries = new ArrayList<>(p.getPlayers().size());
                boolean entryChanged = false;
                for (String entry : p.getPlayers()) {
                    String fake = mask.exact(entry);
                    entries.add(fake == null ? entry : fake);
                    entryChanged |= fake != null;
                }
                if (entryChanged) {
                    p.setPlayers(entries);
                    changed = true;
                }
            }
            return changed;
        }
        if (type == PacketType.Play.Server.UPDATE_SCORE) {
            var p = new WrapperPlayServerUpdateScore(event);
            boolean changed = false;
            String fake = mask.exact(p.getEntityName());
            if (fake != null) {
                p.setEntityName(fake);
                changed = true;
            }
            Component display = p.getEntityDisplayName();
            if (display != null) {
                Component masked = mask.apply(display);
                if (masked != display) {
                    p.setEntityDisplayName(masked);
                    changed = true;
                }
            }
            return changed;
        }
        if (type == PacketType.Play.Server.RESET_SCORE) {
            var p = new WrapperPlayServerResetScore(event);
            String fake = mask.exact(p.getTargetName());
            if (fake == null) return false;
            p.setTargetName(fake);
            return true;
        }
        if (type == PacketType.Play.Server.ENTITY_METADATA) {
            var p = new WrapperPlayServerEntityMetadata(event);
            boolean changed = false;
            for (EntityData<?> data : p.getEntityMetadata()) {
                changed |= metadata(data, mask);
            }
            return changed;
        }
        if (type == PacketType.Play.Server.SET_TITLE_TEXT) {
            var p = new WrapperPlayServerSetTitleText(event);
            Component masked = mask.apply(p.getTitle());
            if (masked == p.getTitle()) return false;
            p.setTitle(masked);
            return true;
        }
        if (type == PacketType.Play.Server.SET_TITLE_SUBTITLE) {
            var p = new WrapperPlayServerSetTitleSubtitle(event);
            Component masked = mask.apply(p.getSubtitle());
            if (masked == p.getSubtitle()) return false;
            p.setSubtitle(masked);
            return true;
        }
        if (type == PacketType.Play.Server.ACTION_BAR) {
            var p = new WrapperPlayServerActionBar(event);
            Component masked = mask.apply(p.getActionBarText());
            if (masked == p.getActionBarText()) return false;
            p.setActionBarText(masked);
            return true;
        }
        return false;
    }

    private static boolean bound(ChatType.@Nullable Bound bound, NameMask mask) {
        if (bound == null) return false;
        boolean changed = false;
        Component name = mask.apply(bound.getName());
        if (name != bound.getName()) {
            bound.setName(name);
            changed = true;
        }
        Component target = bound.getTargetName();
        if (target != null) {
            Component masked = mask.apply(target);
            if (masked != target) {
                bound.setTargetName(masked);
                changed = true;
            }
        }
        return changed;
    }

    /** Text displays, custom names and similar: plain or optional components. */
    @SuppressWarnings("unchecked")
    private static boolean metadata(EntityData<?> data, NameMask mask) {
        Object value = data.getValue();
        if (value instanceof Component c) {
            Component masked = mask.apply(c);
            if (masked == c) return false;
            ((EntityData<Component>) data).setValue(masked);
            return true;
        }
        if (value instanceof Optional<?> o && o.isPresent() && o.get() instanceof Component c) {
            Component masked = mask.apply(c);
            if (masked == c) return false;
            ((EntityData<Optional<Component>>) data).setValue(Optional.of(masked));
            return true;
        }
        return false;
    }
}
