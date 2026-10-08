package ist.alchm.smthsVanish.paper;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import ist.alchm.smthsVanish.paper.disguise.DisguiseService;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import static io.papermc.paper.command.brigadier.Commands.argument;
import static io.papermc.paper.command.brigadier.Commands.literal;

/**
 * /vanish [player|list|reload] and /disguise [name] [skin], /undisguise [player]. Every branch
 * checks its own permission, so LuckPerms decides each feature on its own.
 */
@NullMarked
final class Commands {
    private final SmthsVanishPaper plugin;

    private Commands(SmthsVanishPaper plugin) {
        this.plugin = plugin;
    }

    static void register(SmthsVanishPaper plugin) {
        Commands c = new Commands(plugin);
        plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            var registrar = event.registrar();
            registrar.register(c.vanish(), "Vanish aç veya kapat", List.of("v", "gizlen"));
            registrar.register(c.disguise(), "Başka bir adla görün", List.of("dis", "kilik"));
            registrar.register(c.undisguise(), "Disguise kaldır", List.of("undis"));
        });
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> vanish() {
        return literal("vanish")
                .requires(s -> s.getSender().hasPermission(VanishService.USE)
                        || s.getSender().hasPermission("smthsvanish.list")
                        || s.getSender().hasPermission("smthsvanish.admin"))
                .executes(ctx -> {
                    Player self = player(ctx);
                    if (self == null || !self.hasPermission(VanishService.USE)) return 0;
                    toggle(self, self);
                    return Command.SINGLE_SUCCESS;
                })
                .then(literal("interact")
                        .requires(s -> s.getSender().hasPermission("smthsvanish.interact"))
                        .executes(ctx -> toggleSwitch(ctx, true)))
                .then(literal("pickup")
                        .requires(s -> s.getSender().hasPermission("smthsvanish.pickup"))
                        .executes(ctx -> toggleSwitch(ctx, false)))
                .then(literal("list")
                        .requires(s -> s.getSender().hasPermission("smthsvanish.list"))
                        .executes(this::list))
                .then(literal("reload")
                        .requires(s -> s.getSender().hasPermission("smthsvanish.admin"))
                        .executes(ctx -> {
                            plugin.reload();
                            plugin.messages().send(ctx.getSource().getSender(), "reloaded");
                            return Command.SINGLE_SUCCESS;
                        }))
                .then(argument("player", StringArgumentType.word())
                        .requires(s -> s.getSender().hasPermission("smthsvanish.use.others"))
                        .suggests(visiblePlayers())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            Player target = target(sender, StringArgumentType.getString(ctx, "player"));
                            if (target == null) return 0;
                            toggle(sender, target);
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> disguise() {
        return literal("disguise")
                .requires(s -> s.getSender().hasPermission("smthsvanish.disguise"))
                .executes(ctx -> {
                    Player self = player(ctx);
                    if (self == null || !usable(self)) return 0;
                    String name = plugin.disguises().randomName();
                    if (name == null) {
                        plugin.messages().send(self, "disguise-name-taken");
                        return 0;
                    }
                    plugin.disguises().disguise(self, name, name);
                    return Command.SINGLE_SUCCESS;
                })
                .then(argument("name", StringArgumentType.word())
                        .requires(s -> s.getSender().hasPermission("smthsvanish.disguise.name"))
                        .executes(ctx -> disguiseAs(ctx, StringArgumentType.getString(ctx, "name"), null))
                        .then(argument("skin", StringArgumentType.word())
                                .requires(s -> s.getSender().hasPermission("smthsvanish.disguise.skin"))
                                .executes(ctx -> disguiseAs(ctx,
                                        StringArgumentType.getString(ctx, "name"),
                                        StringArgumentType.getString(ctx, "skin")))))
                .build();
    }

    private com.mojang.brigadier.tree.LiteralCommandNode<CommandSourceStack> undisguise() {
        return literal("undisguise")
                .requires(s -> s.getSender().hasPermission("smthsvanish.disguise")
                        || s.getSender().hasPermission("smthsvanish.disguise.others"))
                .executes(ctx -> {
                    Player self = player(ctx);
                    if (self == null) return 0;
                    plugin.messages().send(self, plugin.disguises().undisguise(self) ? "disguise-off" : "disguise-none");
                    return Command.SINGLE_SUCCESS;
                })
                .then(argument("player", StringArgumentType.word())
                        .requires(s -> s.getSender().hasPermission("smthsvanish.disguise.others"))
                        .suggests(visiblePlayers())
                        .executes(ctx -> {
                            CommandSender sender = ctx.getSource().getSender();
                            Player target = target(sender, StringArgumentType.getString(ctx, "player"));
                            if (target == null) return 0;
                            boolean done = plugin.disguises().undisguise(target);
                            plugin.messages().send(sender, done ? "disguise-off-other" : "disguise-none", Messages.player(target.getName()));
                            return Command.SINGLE_SUCCESS;
                        }))
                .build();
    }

    // --- actions -----------------------------------------------------------------------------------

    private void toggle(CommandSender by, Player target) {
        VanishService vanish = plugin.vanish();
        boolean on = !vanish.isVanished(target.getUniqueId());
        vanish.setVanished(target, on);
        int level = vanish.state(target.getUniqueId()).level();
        if (by.equals(target)) {
            plugin.messages().send(target, on ? "vanish-on" : "vanish-off", Messages.value("level", level));
        } else {
            plugin.messages().send(by, on ? "vanish-on-other" : "vanish-off-other", Messages.player(target.getName()));
            plugin.messages().send(target, on ? "vanish-on-by" : "vanish-off-by", Messages.player(by.getName()), Messages.value("level", level));
        }
    }

    /** /vanish interact and /vanish pickup: on for now, off again at the next vanish. */
    private int toggleSwitch(CommandContext<CommandSourceStack> ctx, boolean interact) {
        Player self = player(ctx);
        if (self == null) return 0;
        VanishService vanish = plugin.vanish();
        var state = vanish.state(self.getUniqueId());
        if (!state.vanished()) {
            plugin.messages().send(self, "not-vanished");
            return 0;
        }
        boolean on = interact ? !state.interact() : !state.pickup();
        vanish.setState(self, interact ? state.withInteract(on) : state.withPickup(on));
        plugin.messages().send(self, (interact ? "interact-" : "pickup-") + (on ? "on" : "off"));
        return Command.SINGLE_SUCCESS;
    }

    private int list(CommandContext<CommandSourceStack> ctx) {
        CommandSender sender = ctx.getSource().getSender();
        List<String> names = Bukkit.getOnlinePlayers().stream()
                .filter(p -> plugin.vanish().isVanished(p.getUniqueId()))
                .filter(p -> !(sender instanceof Player viewer) || plugin.vanish().canSee(viewer, p))
                .map(p -> p.getName() + " (" + plugin.vanish().state(p.getUniqueId()).level() + ")")
                .sorted()
                .toList();
        if (names.isEmpty()) plugin.messages().send(sender, "vanish-list-empty");
        else plugin.messages().send(sender, "vanish-list",
                Messages.value("count", names.size()), Messages.value("list", String.join(", ", names)));
        return Command.SINGLE_SUCCESS;
    }

    private int disguiseAs(CommandContext<CommandSourceStack> ctx, String name, @Nullable String skin) {
        Player self = player(ctx);
        if (self == null || !usable(self)) return 0;
        if (!DisguiseService.isValidName(name)) {
            plugin.messages().send(self, "disguise-invalid-name");
            return 0;
        }
        if (!plugin.disguises().isFree(name) && !name.equalsIgnoreCase(plugin.disguises().visibleName(self))) {
            plugin.messages().send(self, "disguise-name-taken");
            return 0;
        }
        if (skin != null && !DisguiseService.isValidName(skin)) {
            plugin.messages().send(self, "disguise-invalid-name");
            return 0;
        }
        plugin.disguises().disguise(self, name, skin == null ? name : skin);
        return Command.SINGLE_SUCCESS;
    }

    // --- helpers -----------------------------------------------------------------------------------

    private boolean usable(Player player) {
        if (plugin.disguises().available()) return true;
        plugin.messages().send(player, "disguise-unavailable");
        return false;
    }

    private static @Nullable Player player(CommandContext<CommandSourceStack> ctx) {
        return ctx.getSource().getSender() instanceof Player p ? p : null;
    }

    /** A target the sender may act on. Players the sender cannot see are reported as not found. */
    private @Nullable Player target(CommandSender sender, String name) {
        Player target = Bukkit.getPlayerExact(name);
        if (target == null || (sender instanceof Player viewer && !plugin.vanish().canSee(viewer, target))) {
            plugin.messages().send(sender, "player-not-found", Messages.value("name", name));
            return null;
        }
        return target;
    }

    private SuggestionProvider<CommandSourceStack> visiblePlayers() {
        return (ctx, builder) -> {
            CommandSender sender = ctx.getSource().getSender();
            String typed = builder.getRemainingLowerCase();
            Bukkit.getOnlinePlayers().stream()
                    .filter(p -> !(sender instanceof Player viewer)
                            || (plugin.vanish().canSee(viewer, p) && !plugin.disguises().masksFor(viewer, p)))
                    .map(Player::getName)
                    .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(typed))
                    .collect(Collectors.toCollection(java.util.TreeSet::new))
                    .forEach(builder::suggest);
            return builder.buildFuture();
        };
    }
}
