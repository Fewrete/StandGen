package dev.standgen.stand;

import net.minecraft.text.Text;

import java.util.Locale;

/** Тип зовнішнього вигляду стенда. Визначається душею + сідом, не змінюється при еволюції. */
public enum StandForm {
    HUMANOID,   // класична фігура, висока точність
    ARMORED,    // важкі лати, висока стійкість
    WEAPON,     // літаюча зброя (залежить від основного типу), висока сила
    SWARM,      // хмара уламків, висока швидкість і дальність
    SUPPORT;    // не атакує: лише допомагає здібностями й баффами (себе й союзників)

    public Text text() { return Text.translatable("standgen.form." + name().toLowerCase(Locale.ROOT)); }
}
