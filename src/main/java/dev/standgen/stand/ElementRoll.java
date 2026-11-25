package dev.standgen.stand;

import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stats;
import net.minecraft.util.Identifier;

import java.util.Random;

/**
 * Визначення стихії при пробудженні. Критерії ПРИХОВАНІ (нікуди не виводяться).
 * Якщо жодна стихія не дотягує до порога, є невеликий шанс отримати випадкову.
 */
public final class ElementRoll {
    private ElementRoll() {}

    /** Бал стихії від цього значення вважається "душа тягнеться до стихії". Налаштовується тут. */
    public static final float THRESHOLD = 1.0f;
    /** Шанс випадкової стихії, якщо нічого не підійшло (2-4%, беремо середнє). */
    public static final float RANDOM_CHANCE = 0.03f;

    private static long custom(ServerStatHandler h, Identifier id) { return h.getStat(Stats.CUSTOM, id); }

    private static long kills(ServerStatHandler h, EntityType<?> t) { return h.getStat(Stats.KILLED, t); }

    /** Бали стихій; індекс = Element.id - 1. */
    public static float[] scores(ServerPlayerEntity p) {
        ServerStatHandler h = p.getStatHandler();
        float[] sig = StandManager.get(p.getServer()).signals(p.getUuid());

        double fire = sig[ElementSignals.FIRE] / 400.0
                + (kills(h, EntityType.BLAZE) + kills(h, EntityType.MAGMA_CUBE) + kills(h, EntityType.GHAST)) / 120.0
                + (h.getStat(Stats.USED, Items.FLINT_AND_STEEL) + h.getStat(Stats.USED, Items.FIRE_CHARGE)
                + h.getStat(Stats.USED, Items.LAVA_BUCKET)) / 160.0;

        double meters = (custom(h, Stats.SWIM_ONE_CM) + custom(h, Stats.WALK_UNDER_WATER_ONE_CM)
                + custom(h, Stats.WALK_ON_WATER_ONE_CM) + 0.5 * custom(h, Stats.BOAT_ONE_CM)) / 100.0;
        double water = meters / 9000.0 + custom(h, Stats.FISH_CAUGHT) / 350.0 + sig[ElementSignals.WATER] / 60.0;

        long ice = h.getStat(Stats.MINED, Blocks.ICE) + h.getStat(Stats.MINED, Blocks.PACKED_ICE)
                + h.getStat(Stats.MINED, Blocks.BLUE_ICE) + h.getStat(Stats.MINED, Blocks.SNOW_BLOCK)
                + h.getStat(Stats.MINED, Blocks.SNOW);
        double cold = ice / 1200.0 + sig[ElementSignals.ICE] / 60.0 + kills(h, EntityType.STRAY) / 60.0
                + h.getStat(Stats.USED, Items.SNOWBALL) / 400.0;

        long sand = h.getStat(Stats.MINED, Blocks.SAND) + h.getStat(Stats.MINED, Blocks.RED_SAND)
                + h.getStat(Stats.MINED, Blocks.SANDSTONE) + h.getStat(Stats.MINED, Blocks.RED_SANDSTONE);
        double dust = sand / 2500.0 + sig[ElementSignals.SAND] / 40.0 + kills(h, EntityType.HUSK) / 50.0;

        float[] r = new float[Element.COUNT];
        r[Element.FIRE.id - 1] = (float) fire;
        r[Element.WATER.id - 1] = (float) water;
        r[Element.ICE.id - 1] = (float) cold;
        r[Element.SAND.id - 1] = (float) dust;
        return r;
    }

    public static Element roll(ServerPlayerEntity p, long seed) {
        float[] s = scores(p);
        int best = -1;
        float bv = THRESHOLD;
        for (int i = 0; i < s.length; i++) {
            if (s[i] >= bv) { bv = s[i]; best = i; }
        }
        if (best >= 0) return Element.byId(best + 1);
        Random r = new Random(seed ^ 0x51E5A1L);
        if (r.nextFloat() < RANDOM_CHANCE) return Element.byId(1 + r.nextInt(Element.COUNT));
        return Element.NONE;
    }
}
