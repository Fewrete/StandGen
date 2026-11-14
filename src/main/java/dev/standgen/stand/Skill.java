package dev.standgen.stand;

import net.minecraft.text.Text;

import java.util.Locale;

/** Усі здібності. Слот 0 = основний тип, 1 = додатковий тип, 2 = форма, 3 = ультимейт основного типу. */
public enum Skill {
    // слот 0/1 (за типом душі)
    EXCAVATE(140),
    BARRAGE(230),          // кд 11.5 с, не залежить від швидкості
    BASTION(400),
    DASH(120),
    PIERCE(160),
    RESURGENCE(500),
    // слот 2 (за формою)
    GRAB(300),
    WALL(360),
    THROW(260),
    BURST(280),
    // слот 3 (ультимейти)
    QUAKE(1200),
    FURY(1400),
    CITADEL(1500),
    TIMESTOP(1500),
    ARROW_RAIN(1100),
    SECOND_CHANCE(2400);

    /** Стадія, з якої відкривається слот. */
    public static final int[] UNLOCK = {0, 2, 3, 4};

    private static final Skill[] BY_TYPE = {EXCAVATE, BARRAGE, BASTION, DASH, PIERCE, RESURGENCE};
    private static final Skill[] BY_FORM = {GRAB, WALL, THROW, BURST};   // порядок StandForm
    private static final Skill[] ULTIMATE = {QUAKE, FURY, CITADEL, TIMESTOP, ARROW_RAIN, SECOND_CHANCE};

    public final int baseCooldown; // тіки

    Skill(int baseCooldown) { this.baseCooldown = baseCooldown; }

    public Text text() { return Text.translatable("standgen.skill." + name().toLowerCase(Locale.ROOT)); }

    public static Skill of(Archetype a) { return BY_TYPE[a.ordinal()]; }
    public static Skill form(StandForm f) { return BY_FORM[f.ordinal()]; }
    public static Skill ultimate(Archetype a) { return ULTIMATE[a.ordinal()]; }
}
