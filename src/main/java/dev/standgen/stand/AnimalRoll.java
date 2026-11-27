package dev.standgen.stand;

import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

import java.util.Random;

/**
 * Визначення тварини при пробудженні (форма Тварина). Критерії ПРИХОВАНІ. Перевіряється лише якщо стихія не випала.
 * Нічого не підійшло: невеликий шанс випадкової тварини.
 */
public final class AnimalRoll {
    private AnimalRoll() {}

    public static final float THRESHOLD = 1.0f;
    public static final float RANDOM_CHANCE = 0.02f;

    private static long custom(ServerStatHandler h, Identifier id) { return h.getStat(Stats.CUSTOM, id); }

    private static long used(ServerStatHandler h, net.minecraft.item.Item i) { return h.getStat(Stats.USED, i); }

    /** Бали тварин; індекс = Animal.id - 1. */
    public static float[] scores(ServerPlayerEntity p) {
        ServerStatHandler h = p.getStatHandler();
        float[] sig = StandManager.get(p.getServer()).animalSignals(p.getUuid());

        double dog = used(h, Items.BONE) / 12.0 + custom(h, Stats.ANIMALS_BRED) / 60.0 + used(h, Items.LEAD) / 40.0
                + sig[AnimalSignals.DOG] / 20.0;

        double bear = used(h, Items.HONEY_BOTTLE) / 25.0 + custom(h, Stats.SLEEP_IN_BED) / 100.0
                + h.getStat(Stats.KILLED, EntityType.POLAR_BEAR) / 6.0
                + (used(h, Items.SALMON) + used(h, Items.COOKED_SALMON)) / 60.0
                + sig[AnimalSignals.BEAR] / 30.0;

        double eagle = custom(h, Stats.AVIATE_ONE_CM) / 100.0 / 8000.0 + custom(h, Stats.FALL_ONE_CM) / 100.0 / 3000.0
                + h.getStat(Stats.KILLED, EntityType.PHANTOM) / 30.0 + used(h, Items.FIREWORK_ROCKET) / 80.0
                + sig[AnimalSignals.EAGLE] / 30.0;

        double horse = custom(h, Stats.HORSE_ONE_CM) / 100.0 / 10000.0
                + (custom(h, Stats.PIG_ONE_CM) + custom(h, Stats.STRIDER_ONE_CM)) / 100.0 / 6000.0
                + used(h, Items.SADDLE) / 6.0 + used(h, Items.GOLDEN_CARROT) / 20.0 + custom(h, Stats.JUMP) / 25000.0
                + sig[AnimalSignals.HORSE] / 25.0;

        float[] r = new float[Animal.COUNT];
        r[Animal.DOG.id - 1] = (float) dog;
        r[Animal.BEAR.id - 1] = (float) bear;
        r[Animal.EAGLE.id - 1] = (float) eagle;
        r[Animal.HORSE.id - 1] = (float) horse;
        return r;
    }

    public static Animal roll(ServerPlayerEntity p, long seed) {
        float[] s = scores(p);
        int best = -1;
        float bv = THRESHOLD;
        for (int i = 0; i < s.length; i++) {
            if (s[i] >= bv) { bv = s[i]; best = i; }
        }
        if (best >= 0) return Animal.byId(best + 1);
        Random r = new Random(seed ^ 0xA11AL);
        if (r.nextFloat() < RANDOM_CHANCE) return Animal.byId(1 + r.nextInt(Animal.COUNT));
        return Animal.NONE;
    }
}
