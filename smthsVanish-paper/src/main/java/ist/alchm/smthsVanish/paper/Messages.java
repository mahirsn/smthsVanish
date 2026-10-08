package ist.alchm.smthsVanish.paper;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.NullMarked;

/** messages.yml. Keys missing from the server's copy fall back to the bundled defaults. */
@NullMarked
public final class Messages {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final YamlConfiguration yaml;

    private Messages(YamlConfiguration yaml) {
        this.yaml = yaml;
    }

    static Messages load(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) plugin.saveResource("messages.yml", false);
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var bundled = Objects.requireNonNull(plugin.getResource("messages.yml"), "messages.yml");
        yaml.setDefaults(YamlConfiguration.loadConfiguration(new InputStreamReader(bundled, StandardCharsets.UTF_8)));
        return new Messages(yaml);
    }

    public Component get(String key, TagResolver... resolvers) {
        return MM.deserialize(raw(key), resolvers);
    }

    /** getString(path) reads the bundled defaults; getString(path, def) would skip them. */
    private String raw(String key) {
        String value = yaml.getString(key);
        return value == null ? key : value;
    }

    public void send(Audience to, String key, TagResolver... resolvers) {
        to.sendMessage(MM.deserialize(raw("prefix")).append(get(key, resolvers)));
    }

    public static TagResolver player(String name) {
        return Placeholder.unparsed("player", name);
    }

    public static TagResolver value(String tag, Object value) {
        return Placeholder.unparsed(tag, String.valueOf(value));
    }
}
