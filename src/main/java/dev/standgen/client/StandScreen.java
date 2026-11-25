package dev.standgen.client;

import dev.standgen.StandGenMod;
import dev.standgen.stand.Archetype;
import dev.standgen.stand.Element;
import dev.standgen.stand.Progression;
import dev.standgen.stand.Skill;
import dev.standgen.stand.StandData;
import dev.standgen.stand.StandForm;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Меню душі. Сторінка 1: бали душі й типи. Сторінка 2: стихія й здібності (звідки кожна береться).
 * Стенд і його характеристики показуються в чаті. У креативі (або в оператора) душу, форму й стихію можна міняти для тестів.
 */
public class StandScreen extends Screen {
    private static final int GOLD = 0xFFAA00, WHITE = 0xFFFFFF, GRAY = 0xAAAAAA, DARK = 0xFF303030;
    private static final int SOUL_Y = 62;
    private ButtonWidget formBtn;
    private ButtonWidget elemBtn;
    private int page;

    public StandScreen() { super(Text.translatable("standgen.menu.title")); }

    private int left() { return width / 2 - 115; }

    @Override
    protected void init() {
        StandClientData d = StandClientData.current;
        formBtn = null;
        elemBtn = null;
        addDrawableChild(ButtonWidget.builder(Text.literal("<"), b -> setPage(0)).dimensions(width / 2 - 8, height - 28, 28, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal(">"), b -> setPage(1)).dimensions(width / 2 + 22, height - 28, 28, 20).build());
        if (page == 1) {
            if (d != null && d.canEdit()) {
                elemBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> cycleElement())
                        .dimensions(width / 2 - 150, height - 28, 140, 20).build());
            }
        } else if (d != null && d.canEdit()) {
            int bx = left() + 190;
            for (int i = 0; i < Archetype.COUNT; i++) {
                final int idx = i;
                int y = SOUL_Y + i * 16;
                addDrawableChild(ButtonWidget.builder(Text.literal("-"), b -> adjust(idx, -0.1f)).dimensions(bx, y - 2, 16, 14).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("+"), b -> adjust(idx, 0.1f)).dimensions(bx + 18, y - 2, 16, 14).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("1"), b -> pickType(2, idx)).dimensions(bx + 42, y - 2, 16, 14).build());
                addDrawableChild(ButtonWidget.builder(Text.literal("2"), b -> pickType(3, idx)).dimensions(bx + 60, y - 2, 16, 14).build());
            }
            formBtn = addDrawableChild(ButtonWidget.builder(Text.empty(), b -> cycleForm())
                    .dimensions(width / 2 - 150, height - 28, 140, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), b -> close())
                .dimensions(width / 2 + 50, height - 28, 100, 20).build());
    }

    private void setPage(int p) {
        if (page == p) return;
        page = p;
        clearChildren();
        init();
    }

    private void cycleElement() {
        StandClientData d = StandClientData.current;
        if (d == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(4); buf.writeInt(d.element() + 1); buf.writeFloat(0f);
        ClientPlayNetworking.send(StandGenMod.EDIT, buf);
    }

    private void adjust(int idx, float delta) {
        StandClientData d = StandClientData.current;
        if (d == null) return;
        float step = hasShiftDown() ? delta * 5 : delta;
        float nv = Math.max(0f, Math.min(3f, d.scores()[idx] + step));
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(0); buf.writeInt(idx); buf.writeFloat(nv);
        ClientPlayNetworking.send(StandGenMod.EDIT, buf);
    }

    /** kind: 2 = основний тип, 3 = додатковий тип. */
    private void pickType(int kind, int idx) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(kind); buf.writeInt(idx); buf.writeFloat(0f);
        ClientPlayNetworking.send(StandGenMod.EDIT, buf);
    }

    private void cycleForm() {
        StandClientData d = StandClientData.current;
        if (d == null) return;
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(1); buf.writeInt(d.form() + 1); buf.writeFloat(0f);
        ClientPlayNetworking.send(StandGenMod.EDIT, buf);
    }

    private void bar(DrawContext ctx, int x, int y, int w, float frac, int color) {
        ctx.fill(x, y + 1, x + w, y + 8, DARK);
        int fw = Math.round(w * Math.max(0f, Math.min(1f, frac)));
        if (fw > 0) ctx.fill(x, y + 1, x + fw, y + 8, 0xFF000000 | color);
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        renderBackground(ctx);
        StandClientData d = StandClientData.current;
        if (d == null) {
            ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("msg.standgen.no_stand"), width / 2, height / 2, WHITE);
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }
        StandData sd = d.asData();
        if (page == 1) {
            renderElementPage(ctx, d, sd);
            super.render(ctx, mouseX, mouseY, delta);
            return;
        }
        Archetype p = sd.primary, s = sd.secondary;

        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✦ " + d.name() + " ✦"), width / 2, 12, GOLD);
        Text line = Text.translatable("msg.standgen.soul", p.text(), s.text()).append("   ")
                .append(Text.translatable("msg.standgen.stage", d.stage(), Progression.labelText(d.milestones())));
        ctx.drawCenteredTextWithShadow(textRenderer, line, width / 2, 28, 0xFFFF55);

        ctx.drawText(textRenderer, Text.translatable("standgen.menu.soul"), left(), SOUL_Y - 14, GRAY, true);
        for (int i = 0; i < Archetype.COUNT; i++) {
            Archetype a = Archetype.values()[i];
            int yy = SOUL_Y + i * 16;
            boolean top = a == p || a == s;
            ctx.drawText(textRenderer, a.text(), left(), yy, top ? GOLD : WHITE, true);
            ctx.drawText(textRenderer, Text.literal(String.format(java.util.Locale.ROOT, "%.2f", d.scores()[i])), left() + 84, yy, WHITE, true);
            bar(ctx, left() + 116, yy, 68, d.scores()[i] / 2.5f, top ? GOLD : 0xAAAAAA);
        }
        if (d.canEdit()) {
            ctx.drawText(textRenderer, Text.translatable("standgen.menu.edit"), left(), SOUL_Y + Archetype.COUNT * 16 + 6, 0xFF5555, true);
            ctx.drawText(textRenderer, Text.translatable("standgen.menu.pick"), left(), SOUL_Y + Archetype.COUNT * 16 + 18, 0xFFAA55, true);
            if (formBtn != null) formBtn.setMessage(Text.translatable("standgen.menu.form", sd.form.text()));
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("1 / 2"), width / 2 + 6, height - 42, GRAY);
        super.render(ctx, mouseX, mouseY, delta);
    }

    private void renderElementPage(DrawContext ctx, StandClientData d, StandData sd) {
        int x = width / 2 - 130;
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("✦ " + d.name() + " ✦"), width / 2, 12, GOLD);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.translatable("standgen.menu.element", sd.element.text()), width / 2, 30,
                sd.element.isNone() ? GRAY : 0xFFFF55);
        ctx.drawText(textRenderer, Text.translatable("standgen.menu.abilities"), x, 56, GRAY, true);
        boolean elemental = !sd.element.isNone() && sd.form != StandForm.SUPPORT;
        boolean support = sd.form == StandForm.SUPPORT;
        for (int i = 0; i < 4; i++) {
            Skill sk = sd.skill(i);
            boolean open = d.stage() >= sd.unlockStage(i);
            Text key = Text.keybind("key.standgen.skill" + (i + 1));
            MutableText line = open
                    ? Text.translatable("msg.standgen.skill", key, sk.text())
                    : Text.translatable("msg.standgen.skill_locked", key, sk.text(), sd.unlockStage(i));
            if (!support) {
                String src = i == 2 ? "form" : (elemental && (i == 1 || i == 3)) ? "element" : "type";
                line.append(Text.literal("   ")).append(Text.translatable("standgen.menu.src." + src).formatted(Formatting.DARK_GRAY));
            }
            ctx.drawText(textRenderer, line, x, 74 + i * 18, open ? 0x55FF55 : GRAY, true);
        }
        if (d.canEdit()) {
            ctx.drawText(textRenderer, Text.translatable("standgen.menu.edit"), x, 74 + 4 * 18 + 8, 0xFF5555, true);
            if (elemBtn != null) elemBtn.setMessage(Text.translatable("standgen.menu.elem_btn", sd.element.text()));
        }
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("2 / 2"), width / 2 + 6, height - 42, GRAY);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (StandGenClient.menuKey != null && StandGenClient.menuKey.matchesKey(keyCode, scanCode)) { close(); return true; }
        if (keyCode == 263) { setPage(0); return true; }   // стрілка вліво
        if (keyCode == 262) { setPage(1); return true; }   // стрілка вправо
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override public boolean shouldPause() { return false; }
}
