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
    PIERCE(200),
    RESURGENCE(600),
    // слот 2 (за формою)
    GRAB(300),
    WALL(40),
    THROW(260),
    BURST(280),
    // слот 3 (ультимейти)
    QUAKE(1200),
    FURY(1400),
    CITADEL(1800),
    TIMESTOP(1500),
    ARROW_RAIN(1100),
    SECOND_CHANCE(3600),
    // форма Підтримка (нічого не б'ють): слоти 0/1 за типом душі, слот 2 і 3 свої
    PROSPECT(600),
    RALLY(700),
    FORTIFY(700),
    TAILWIND(500),
    SCOUT(500),
    MEND(600),
    PURIFY(400),
    SANCTUARY(2400),
    // стихії: слот 1 (замість додаткового типу) і слот 3 (замість ультимейта типу); у кінці, щоб не зсунути ordinal старих здібностей
    FLAME_LANCE(220), INFERNO(1500),
    TIDAL_JET(220), DELUGE(1500),
    FROST_BOLT(220), BLIZZARD(1600),
    SAND_BLAST(220), SANDSTORM(1500),
    // слот 2 форми Стихія: перетворення стенда на Зброю або Броню
    SHIFT(100),
    // тварини: слот 1, слот 2 (форма) і ультимейт (слот 3)
    POUNCE(200), SCENT(500), HOWL(1500),                 // собака
    MAUL(240), ROAR(600), HIBERNATE(1700),               // ведмідь
    TALON_DIVE(220), UPDRAFT(500), GALE(1600),           // орел
    TRAMPLE(220), KICK(300), GALLOP(1500);               // кінь

    /** Стадія, з якої відкривається слот. */
    public static final int[] UNLOCK = {0, 2, 3, 4};

    private static final Skill[] BY_TYPE = {EXCAVATE, BARRAGE, BASTION, DASH, PIERCE, RESURGENCE};
    private static final Skill[] BY_FORM = {GRAB, WALL, THROW, BURST, PURIFY, SHIFT, SHIFT};   // порядок StandForm
    private static final Skill[] SUPPORT = {PROSPECT, RALLY, FORTIFY, TAILWIND, SCOUT, MEND};   // порядок Archetype
    private static final Skill[] ULTIMATE = {QUAKE, FURY, CITADEL, TIMESTOP, ARROW_RAIN, SECOND_CHANCE};

    private static final Skill[] ELEMENT_SKILL = {null, FLAME_LANCE, TIDAL_JET, FROST_BOLT, SAND_BLAST};   // за Element.id
    private static final Skill[] ELEMENT_ULTIMATE = {null, INFERNO, DELUGE, BLIZZARD, SANDSTORM};
    private static final Skill[] ANIMAL_A = {null, POUNCE, MAUL, TALON_DIVE, TRAMPLE};     // за Animal.id
    private static final Skill[] ANIMAL_B = {null, SCENT, ROAR, UPDRAFT, KICK};
    private static final Skill[] ANIMAL_ULT = {null, HOWL, HIBERNATE, GALE, GALLOP};

    public final int baseCooldown; // тіки

    Skill(int baseCooldown) { this.baseCooldown = baseCooldown; }

    public Text text() { return Text.translatable("standgen.skill." + name().toLowerCase(Locale.ROOT)); }

    public static Skill of(Archetype a) { return BY_TYPE[a.ordinal()]; }
    public static Skill support(Archetype a) { return SUPPORT[a.ordinal()]; }
    public static Skill form(StandForm f) { return BY_FORM[f.ordinal()]; }
    public static Skill ultimate(Archetype a) { return ULTIMATE[a.ordinal()]; }
    public static Skill elemental(Element e) { return ELEMENT_SKILL[e.id]; }
    public static Skill elementalUltimate(Element e) { return ELEMENT_ULTIMATE[e.id]; }
    public static Skill animalA(Animal a) { return ANIMAL_A[a.id]; }
    public static Skill animalB(Animal a) { return ANIMAL_B[a.id]; }
    public static Skill animalUltimate(Animal a) { return ANIMAL_ULT[a.id]; }

    /** Ультимейт-поле стихії (діє певний час навколо гравця). */
    public boolean isElementUltimate() {
        return this == INFERNO || this == DELUGE || this == BLIZZARD || this == SANDSTORM;
    }
}
