package dev.standgen.stand;

import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.Arrays;
import java.util.Random;

public final class StandGenerator {
    private static final String[] PREFIX = {"Crimson", "Hollow", "Velvet", "Gilded", "Silent", "Rusted",
            "Neon", "Ashen", "Lunar", "Sunken", "Phantom", "Electric"};
    private static final String[][] NOUN = {
            {"Quarry", "Geode", "Obsidian"},       // MINER
            {"Fury", "Knuckle", "Riot"},           // FIGHTER
            {"Mason", "Bastion", "Monolith"},      // BUILDER
            {"Horizon", "Gale", "Drifter"},        // RUNNER
            {"Piercer", "Falcon", "Meridian"},     // ARCHER
            {"Phoenix", "Lazarus", "Cradle"}       // SURVIVOR
    };

    private StandGenerator() {}

    public static StandData generate(ServerPlayerEntity p) {
        float[] raw = StatCollector.collect(p);
        long seed = p.getUuid().getLeastSignificantBits() ^ (p.getServer().getOverworld().getTime() * 31L)
                ^ Arrays.hashCode(raw);
        Random r = new Random(seed);

        float[] s = new float[raw.length];
        for (int i = 0; i < s.length; i++) s[i] = raw[i] * (0.9f + 0.2f * r.nextFloat()) + r.nextFloat() * 0.002f;

        Integer[] idx = new Integer[s.length];
        for (int i = 0; i < idx.length; i++) idx[i] = i;
        Arrays.sort(idx, (a, b) -> Float.compare(s[b], s[a]));

        StandData d = new StandData();
        d.seed = seed;
        d.scores = s;
        d.primary = Archetype.values()[idx[0]];
        d.secondary = Archetype.values()[idx[1]];
        String[] nouns = NOUN[d.primary.ordinal()];
        d.name = PREFIX[r.nextInt(PREFIX.length)] + " " + nouns[r.nextInt(nouns.length)];
        d.stage = Progression.stage(p);
        d.recalc();
        return d;
    }

    public static void awaken(ServerPlayerEntity p) {
        StandData d = generate(p);
        StandManager m = StandManager.get(p.getServer());
        m.put(p.getUuid(), d);
        p.sendMessage(Text.literal("Твій стенд пробудився!").formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
        for (Text t : d.describe()) p.sendMessage(t);
        p.sendMessage(Text.literal(d.scoreLine()).formatted(Formatting.DARK_GRAY));
    }
}
