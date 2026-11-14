package dev.standgen.stand;

import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.registry.Registries;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.stat.ServerStatHandler;
import net.minecraft.stat.Stats;

import java.util.ArrayList;
import java.util.List;

/**
 * Збирає "душу" гравця з ванільної статистики (працює і для старих світів).
 * Інші моди (магія, зброя...) підключаються через StatCollector.register(...).
 */
public final class StatCollector {
    public interface Source {
        /** Повертає додатковий бал для архетипу (1.0 ≈ "багато"). */
        float score(ServerPlayerEntity player, Archetype archetype);
    }

    private static final List<Source> EXTRA = new ArrayList<>();

    private StatCollector() {}

    public static void register(Source s) { EXTRA.add(s); }

    public static float[] collect(ServerPlayerEntity p) {
        ServerStatHandler h = p.getStatHandler();

        long mined = 0;
        for (Block b : Registries.BLOCK) mined += h.getStat(Stats.MINED, b);

        long placed = 0;
        for (Item i : Registries.ITEM) if (i instanceof BlockItem) placed += h.getStat(Stats.USED, i);

        long ranged = h.getStat(Stats.USED, Items.BOW) + h.getStat(Stats.USED, Items.CROSSBOW)
                + h.getStat(Stats.USED, Items.TRIDENT);

        long cm = c(h, Stats.WALK_ONE_CM) + c(h, Stats.SPRINT_ONE_CM) + c(h, Stats.SWIM_ONE_CM)
                + c(h, Stats.FLY_ONE_CM) + c(h, Stats.AVIATE_ONE_CM);
        double meters = cm / 100.0;

        long kills = c(h, Stats.MOB_KILLS);
        double dealt = c(h, Stats.DAMAGE_DEALT) / 10.0;   // у серцях*2
        double taken = c(h, Stats.DAMAGE_TAKEN) / 10.0;
        long deaths = c(h, Stats.DEATHS);
        long jumps = c(h, Stats.JUMP);

        float[] s = new float[Archetype.COUNT];
        s[Archetype.MINER.ordinal()] = mined / 6000f;
        s[Archetype.FIGHTER.ordinal()] = (float) (kills / 150.0 * 0.6 + dealt / 4000.0 * 0.4);
        s[Archetype.BUILDER.ordinal()] = placed / 2500f;
        s[Archetype.RUNNER.ordinal()] = (float) (meters / 60000.0 * 0.85 + jumps / 8000.0 * 0.15);
        s[Archetype.ARCHER.ordinal()] = ranged / 300f;
        s[Archetype.SURVIVOR.ordinal()] = (float) (deaths / 15.0 * 0.5 + taken / 3000.0 * 0.5);

        for (Archetype a : Archetype.values())
            for (Source src : EXTRA) s[a.ordinal()] += src.score(p, a);

        return s;
    }

    private static long c(ServerStatHandler h, net.minecraft.util.Identifier id) {
        return h.getStat(Stats.CUSTOM, id);
    }
}
