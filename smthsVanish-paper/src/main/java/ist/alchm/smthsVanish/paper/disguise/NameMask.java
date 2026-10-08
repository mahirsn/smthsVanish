package ist.alchm.smthsVanish.paper.disguise;

import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.ComponentLike;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.TranslationArgument;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

/**
 * Replaces real names with disguise names in text a viewer receives. A name only matches as a
 * whole word, so "Ali" does not change "Alim". Minecraft names are [A-Za-z0-9_], which is what
 * the word edges test for. Immutable; a new one is built when a disguise starts or ends.
 *
 * <p>Components are walked whole, not only their text: the vanilla join line carries the name
 * three more times, as insertion, as a /tell click and as a hover entity.
 */
@NullMarked
public final class NameMask {
    public static final NameMask EMPTY = new NameMask(Map.of());

    /** Lower-case real name to disguise name. */
    private final Map<String, String> names;
    private final @Nullable Pattern pattern;

    public NameMask(Map<String, String> realToFake) {
        this.names = realToFake.entrySet().stream()
                .collect(Collectors.toUnmodifiableMap(e -> e.getKey().toLowerCase(Locale.ROOT), Map.Entry::getValue));
        if (names.isEmpty()) {
            this.pattern = null;
        } else {
            String alternatives = names.keySet().stream().map(Pattern::quote).collect(Collectors.joining("|"));
            this.pattern = Pattern.compile("(?<![A-Za-z0-9_])(?:" + alternatives + ")(?![A-Za-z0-9_])", Pattern.CASE_INSENSITIVE);
        }
    }

    public boolean isEmpty() {
        return names.isEmpty();
    }

    /** The disguise name for an exact real name, or null when that name is not masked. */
    public @Nullable String exact(String realName) {
        return names.get(realName.toLowerCase(Locale.ROOT));
    }

    public String apply(String text) {
        Pattern p = pattern;
        if (p == null) return text;
        Matcher m = p.matcher(text);
        if (!m.find()) return text;
        StringBuilder out = new StringBuilder(text.length());
        do {
            m.appendReplacement(out, Matcher.quoteReplacement(fakeFor(m.group())));
        } while (m.find());
        m.appendTail(out);
        return out.toString();
    }

    /** Returns the same instance when nothing matched, so callers can compare by identity. */
    public Component apply(Component text) {
        return pattern == null ? text : walk(text);
    }

    private Component walk(Component c) {
        Component out = c;
        if (out instanceof TextComponent t) {
            String content = apply(t.content());
            if (!content.equals(t.content())) out = t.content(content);
        } else if (out instanceof TranslatableComponent t && !t.arguments().isEmpty()) {
            List<ComponentLike> args = new ArrayList<>(t.arguments().size());
            boolean changed = false;
            for (TranslationArgument arg : t.arguments()) {
                if (arg.value() instanceof Component inner) {
                    Component masked = walk(inner);
                    changed |= masked != inner;
                    args.add(masked);
                } else {
                    args.add(arg);
                }
            }
            if (changed) out = t.arguments(args);
        }
        String insertion = out.insertion();
        if (insertion != null) {
            String masked = apply(insertion);
            if (!masked.equals(insertion)) out = out.insertion(masked);
        }
        ClickEvent<?> click = out.clickEvent();
        if (click != null && click.payload() instanceof ClickEvent.Payload.Text payload) {
            String masked = apply(payload.value());
            if (!masked.equals(payload.value())) out = out.clickEvent(retext(click, masked));
        }
        HoverEvent<?> hover = out.hoverEvent();
        if (hover != null) {
            if (hover.value() instanceof Component shown) {
                Component masked = walk(shown);
                if (masked != shown) out = out.hoverEvent(HoverEvent.showText(masked));
            } else if (hover.value() instanceof HoverEvent.ShowEntity entity && entity.name() != null) {
                Component name = entity.name();
                Component masked = walk(name);
                if (masked != name) out = out.hoverEvent(HoverEvent.showEntity(entity.name(masked)));
            }
        }
        List<Component> children = out.children();
        if (!children.isEmpty()) {
            List<Component> masked = new ArrayList<>(children.size());
            boolean changed = false;
            for (Component child : children) {
                Component m = walk(child);
                changed |= m != child;
                masked.add(m);
            }
            if (changed) out = out.children(masked);
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static ClickEvent<?> retext(ClickEvent<?> click, String value) {
        return ClickEvent.clickEvent((ClickEvent.Action<ClickEvent.Payload.Text>) click.action(), ClickEvent.Payload.string(value));
    }

    private String fakeFor(String real) {
        return names.getOrDefault(real.toLowerCase(Locale.ROOT), real);
    }
}
