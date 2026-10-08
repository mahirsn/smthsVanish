package ist.alchm.smthsVanish.paper.hook;

import ist.alchm.smthsVanish.paper.SmthsVanishPaper;
import java.util.Objects;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.event.EventSubscription;
import net.luckperms.api.event.user.UserDataRecalculateEvent;
import net.luckperms.api.model.group.Group;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.NodeType;
import net.luckperms.api.node.types.PrefixNode;
import net.luckperms.api.node.types.SuffixNode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Two jobs. A permission change (rank up, temporary see node) re-applies visibility right away
 * instead of at the next join. While disguised, the player reads as the mask group everywhere
 * {@code %luckperms_prefix%} is used: a transient node, never written to the database, so a crash
 * cannot strip a rank. The same approach smthsRoyale uses.
 */
@NullMarked
public final class LuckPermsHook {
    /** Above any real group weight, so the mask wins the meta stack. */
    private static final int PRIORITY = 9999;

    private final SmthsVanishPaper plugin;
    private final LuckPerms luckPerms = LuckPermsProvider.get();
    private @Nullable EventSubscription<UserDataRecalculateEvent> subscription;

    public LuckPermsHook(SmthsVanishPaper plugin) {
        this.plugin = plugin;
    }

    public void subscribe() {
        subscription = luckPerms.getEventBus().subscribe(plugin, UserDataRecalculateEvent.class, event -> {
            Player player = Bukkit.getPlayer(event.getUser().getUniqueId());
            if (player == null) return;
            player.getScheduler().run(plugin, task -> plugin.vanish().permissionsChanged(player), null);
        });
    }

    public void close() {
        if (subscription != null) subscription.close();
    }

    public void maskRank(Player player, String groupName) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user == null) return;
        Group group = luckPerms.getGroupManager().getGroup(groupName);
        String prefix = group == null ? null : group.getCachedData().getMetaData().getPrefix();
        String suffix = group == null ? null : group.getCachedData().getMetaData().getSuffix();
        unmask(user);
        user.transientData().add(PrefixNode.builder(Objects.requireNonNullElse(prefix, ""), PRIORITY).build());
        user.transientData().add(SuffixNode.builder(Objects.requireNonNullElse(suffix, ""), PRIORITY).build());
    }

    public void unmaskRank(Player player) {
        User user = luckPerms.getUserManager().getUser(player.getUniqueId());
        if (user != null) unmask(user);
    }

    private static void unmask(User user) {
        user.transientData().clear(n -> (n.getType() == NodeType.PREFIX || n.getType() == NodeType.SUFFIX)
                && n instanceof net.luckperms.api.node.types.ChatMetaNode<?, ?> meta
                && meta.getPriority() == PRIORITY);
    }
}
