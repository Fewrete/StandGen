package dev.standgen.stand;

import net.minecraft.text.Text;

import java.util.Locale;

/**
 * Стихія стенда. NONE = звичайний стенд. Ідентифікатор = ordinal (зберігається в NBT як "el").
 * Як саме стихія обирається, гравцям не показується (див. ElementRoll).
 */
public enum Element {
    NONE(0f, 0),
    FIRE(0.04f, 1),    // помаранчево-червоний
    WATER(0.58f, 2),   // синій
    ICE(0.52f, 3),     // блакитно-білий
    SAND(0.13f, 4);    // жовто-пісочний

    /** Кількість справжніх стихій (без NONE). */
    public static final int COUNT = values().length - 1;

    public final float hue;
    public final int id;

    Element(float hue, int id) { this.hue = hue; this.id = id; }

    public boolean isNone() { return this == NONE; }

    public Text text() { return Text.translatable("standgen.element." + name().toLowerCase(Locale.ROOT)); }

    public static Element byId(int id) {
        Element[] v = values();
        return id < 0 || id >= v.length ? NONE : v[id];
    }
}
