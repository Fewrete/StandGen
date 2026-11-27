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

    /** Назва з урахуванням рідкісного покрасу (синій вогонь, червоний пісок тощо). */
    public Text text(boolean alt) {
        return alt ? Text.translatable("standgen.element." + name().toLowerCase(Locale.ROOT) + "_alt") : text();
    }

    /**
     * Палітра стихії: {відтінок1, насиченість1, яскравість1, відтінок2, насиченість2, яскравість2}.
     * Обидва кольори завжди з однієї родини стихії. alt = рідкісний альтернативний покрас.
     */
    public float[] palette(boolean alt) {
        return switch (this) {
            case FIRE -> alt ? new float[]{0.58f, 0.85f, 1.00f, 0.50f, 0.55f, 1.00f}     // синій вогонь
                             : new float[]{0.05f, 0.90f, 1.00f, 0.12f, 0.80f, 1.00f};    // помаранчевий вогонь
            case WATER -> alt ? new float[]{0.46f, 0.75f, 0.90f, 0.40f, 0.50f, 1.00f}    // тропічна вода
                              : new float[]{0.58f, 0.80f, 0.95f, 0.52f, 0.50f, 1.00f};   // синя вода
            case ICE -> alt ? new float[]{0.62f, 0.70f, 0.95f, 0.55f, 0.40f, 1.00f}      // глибокий синій лід
                            : new float[]{0.52f, 0.35f, 1.00f, 0.58f, 0.15f, 1.00f};     // світло-блакитний лід
            case SAND -> alt ? new float[]{0.03f, 0.65f, 0.80f, 0.05f, 0.70f, 0.65f}     // червоний пісок
                             : new float[]{0.12f, 0.55f, 0.92f, 0.09f, 0.60f, 0.75f};    // жовтий пісок
            default -> new float[]{0f, 0f, 1f, 0f, 0f, 1f};
        };
    }

    public static Element byId(int id) {
        Element[] v = values();
        return id < 0 || id >= v.length ? NONE : v[id];
    }
}
