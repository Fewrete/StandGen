package dev.standgen.stand;

import net.minecraft.text.Text;

import java.util.Locale;

/** Типи "душі". Порядок = індекс у масиві scores (і в моделі зброї). Назви беруться з мовних файлів. */
public enum Archetype {
    MINER(0.07f),
    FIGHTER(0.0f),
    BUILDER(0.55f),
    RUNNER(0.14f),
    ARCHER(0.33f),
    SURVIVOR(0.8f);

    public static final int COUNT = values().length;
    public final float hue;

    Archetype(float hue) { this.hue = hue; }

    public Text text() { return Text.translatable("standgen.archetype." + name().toLowerCase(Locale.ROOT)); }
    public Text weaponText() { return Text.translatable("standgen.weapon." + name().toLowerCase(Locale.ROOT)); }
}
