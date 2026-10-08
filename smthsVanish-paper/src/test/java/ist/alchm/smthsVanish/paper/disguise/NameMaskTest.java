package ist.alchm.smthsVanish.paper.disguise;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.Test;

class NameMaskTest {
    private final NameMask mask = new NameMask(Map.of("Ali", "Veli", "Mod_1", "Kedi"));

    @Test
    void replacesWholeNamesOnly() {
        assertEquals("Veli: selam Alim", mask.apply("Ali: selam Alim"));
        assertEquals("<Kedi> hi, veli said Veli", mask.apply("<Mod_1> hi, veli said ALI"));
        assertEquals("xAli Ali_ Ali1", mask.apply("xAli Ali_ Ali1"));
        assertEquals("[Veli]", mask.apply("[Ali]"));
    }

    @Test
    void exactLookupIgnoresCase() {
        assertEquals("Veli", mask.exact("aLi"));
        assertNull(mask.exact("Alim"));
    }

    @Test
    void emptyMaskChangesNothing() {
        Component c = Component.text("Ali");
        assertSame(c, NameMask.EMPTY.apply(c));
        assertEquals("Ali", NameMask.EMPTY.apply("Ali"));
    }

    @Test
    void componentsKeepStyleAndChildren() {
        Component in = Component.text("[Admin] ", NamedTextColor.RED)
                .append(Component.text("Ali", NamedTextColor.WHITE))
                .append(Component.text(" joined, Mod_1 too"));
        Component out = mask.apply(in);
        assertEquals("[Admin] Veli joined, Kedi too", PlainTextComponentSerializer.plainText().serialize(out));
        assertEquals(NamedTextColor.WHITE, out.children().get(0).color());
    }

    @Test
    void vanillaJoinLineLeaksNothing() {
        // Shape of multiplayer.player.joined as Paper sends it.
        UUID id = UUID.randomUUID();
        Component name = Component.text("Ali")
                .insertion("Ali")
                .clickEvent(ClickEvent.suggestCommand("/tell Ali "))
                .hoverEvent(HoverEvent.showEntity(Key.key("minecraft:player"), id, Component.text("Ali")));
        Component line = Component.translatable("multiplayer.player.joined", name);
        Component out = mask.apply(line);
        Component arg = (Component) ((TranslatableComponent) out).arguments().get(0).value();
        assertEquals("Veli", ((net.kyori.adventure.text.TextComponent) arg).content());
        assertEquals("Veli", arg.insertion());
        assertEquals("/tell Veli ", ((ClickEvent.Payload.Text) arg.clickEvent().payload()).value());
        assertEquals(ClickEvent.Action.SUGGEST_COMMAND, arg.clickEvent().action());
        HoverEvent.ShowEntity entity = (HoverEvent.ShowEntity) arg.hoverEvent().value();
        assertEquals(Component.text("Veli"), entity.name());
        assertEquals(id, entity.id());
    }

    @Test
    void untouchedComponentIsTheSameInstance() {
        Component c = Component.text("hello").append(Component.translatable("x", Component.text("y")));
        assertSame(c, mask.apply(c));
    }
}
