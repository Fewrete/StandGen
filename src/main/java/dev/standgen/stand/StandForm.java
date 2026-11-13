package dev.standgen.stand;

/** Тип зовнішнього вигляду стенда. Визначається душею + сідом, не змінюється при еволюції. */
public enum StandForm {
    HUMANOID("Гуманоїд"),   // класична фігура, висока точність
    ARMORED("Броня"),       // важкі лати, висока стійкість
    WEAPON("Зброя"),        // літаюча зброя (залежить від основного типу), висока сила
    SWARM("Рій");           // хмара уламків, висока швидкість і дальність

    public final String title;

    StandForm(String title) { this.title = title; }
}
