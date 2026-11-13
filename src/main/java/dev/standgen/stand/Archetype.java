package dev.standgen.stand;

/** Типи "душі". Порядок = індекс у масиві scores (і в моделі зброї). */
public enum Archetype {
    MINER("Шахтар", "Розкопка", 0.07f, "Кирка"),
    FIGHTER("Боєць", "Шквал ударів", 0.0f, "Меч"),
    BUILDER("Будівельник", "Бастіон", 0.55f, "Молот"),
    RUNNER("Мандрівник", "Ривок", 0.14f, "Спис"),
    ARCHER("Стрілець", "Пронизуючий постріл", 0.33f, "Лук"),
    SURVIVOR("Вижилий", "Відродження", 0.8f, "Щит");

    public static final int COUNT = values().length;
    public final String title;
    public final String abilityName;
    public final float hue;
    public final String weaponName;

    Archetype(String title, String abilityName, float hue, String weaponName) {
        this.title = title;
        this.abilityName = abilityName;
        this.hue = hue;
        this.weaponName = weaponName;
    }
}
