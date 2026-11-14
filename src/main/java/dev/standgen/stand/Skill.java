package dev.standgen.stand;

/** Усі здібності. Слот 0 = основний тип, 1 = додатковий тип, 2 = форма, 3 = ультимейт основного типу. */
public enum Skill {
    // слот 0/1 (за типом душі)
    EXCAVATE("Розкопка", 140),
    BARRAGE("Шквал ударів", 230),          // кд 11.5 с, не залежить від швидкості
    BASTION("Бастіон", 400),
    DASH("Ривок", 120),
    PIERCE("Пронизуючий постріл", 160),
    RESURGENCE("Відродження", 500),
    // слот 2 (за формою)
    GRAB("Хватка", 300),
    WALL("Броньована стіна", 360),
    THROW("Метання зброї", 260),
    BURST("Вибух уламків", 280),
    // слот 3 (ультимейти)
    QUAKE("Землетрус", 1200),
    FURY("Режим люті", 1400),
    CITADEL("Цитадель", 1500),
    TIMESTOP("Зупинка часу", 1500),
    ARROW_RAIN("Дощ стріл", 1100),
    SECOND_CHANCE("Другий шанс", 2400);

    /** Стадія, з якої відкривається слот. */
    public static final int[] UNLOCK = {0, 2, 3, 4};

    private static final Skill[] BY_TYPE = {EXCAVATE, BARRAGE, BASTION, DASH, PIERCE, RESURGENCE};
    private static final Skill[] BY_FORM = {GRAB, WALL, THROW, BURST};   // порядок StandForm
    private static final Skill[] ULTIMATE = {QUAKE, FURY, CITADEL, TIMESTOP, ARROW_RAIN, SECOND_CHANCE};

    public final String title;
    public final int baseCooldown; // тіки

    Skill(String title, int baseCooldown) {
        this.title = title;
        this.baseCooldown = baseCooldown;
    }

    public static Skill of(Archetype a) { return BY_TYPE[a.ordinal()]; }
    public static Skill form(StandForm f) { return BY_FORM[f.ordinal()]; }
    public static Skill ultimate(Archetype a) { return ULTIMATE[a.ordinal()]; }
}
