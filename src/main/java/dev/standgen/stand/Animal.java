package dev.standgen.stand;

import net.minecraft.text.Text;

import java.util.Locale;

/** Тварина форми ANIMAL. NONE = не тваринний стенд. id = ordinal (NBT "an"). Як тварина обирається, гравцям не показується. */
public enum Animal {
    NONE(0f, 0f, 0f, 0f, 0f, 1f),
    DOG(0.09f, 0.45f, 0.92f, 0.0f, 0.0f, 1.0f),
    BEAR(0.06f, 0.60f, 0.70f, 0.22f, 1.30f, 1.1f),
    EAGLE(0.11f, 0.55f, 0.95f, 0.0f, 0.0f, 1.0f),
    HORSE(0.07f, 0.50f, 0.85f, 0.34f, 1.57f, 1.35f);

    public static final int COUNT = values().length - 1;

    public final float hue, sat, val;
    /** Швидкість їзди (блоків за тік, до бонусу від швидкості); 0 = не осідлати. */
    public final float rideSpeed;
    /** Висота сидіння над землею (блоків). */
    public final float seat;
    /** Масштаб моделі. */
    public final float scale;
    public final int id;

    Animal(float hue, float sat, float val, float rideSpeed, float seat, float scale) {
        this.hue = hue; this.sat = sat; this.val = val; this.rideSpeed = rideSpeed; this.seat = seat; this.scale = scale;
        this.id = ordinal();
    }

    public boolean isNone() { return this == NONE; }
    public boolean rideable() { return rideSpeed > 0f; }
    /** Орел не возить на спині: тримає гравця й планерить. */
    public boolean glides() { return this == EAGLE; }

    public Text text() { return Text.translatable("standgen.animal." + name().toLowerCase(Locale.ROOT)); }

    public static Animal byId(int id) {
        Animal[] v = values();
        return id < 0 || id >= v.length ? NONE : v[id];
    }
}
