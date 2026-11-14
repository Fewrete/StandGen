package dev.standgen.stand;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class StandData {
    // Зберігається
    public Archetype primary = Archetype.FIGHTER;
    public Archetype secondary = Archetype.MINER;
    public long seed;
    public String name = "";
    public float[] scores = new float[Archetype.COUNT];
    public int stage;

    // Похідне (перераховується через recalc)
    public int power, speed, range, durability, precision;
    public int color1 = 0xFFFFFF, color2 = 0xFFFFFF;
    public float bulk = 1f, height = 1f;
    public StandForm form = StandForm.HUMANOID;
    public int style; // 0..3, дрібні варіації вигляду (гребінь, роги)

    public void recalc() {
        float[] s = new float[Archetype.COUNT];
        for (int i = 0; i < s.length; i++) s[i] = Math.min(scores[i], 2.5f);
        float M = s[Archetype.MINER.ordinal()], F = s[Archetype.FIGHTER.ordinal()],
                B = s[Archetype.BUILDER.ordinal()], R = s[Archetype.RUNNER.ordinal()],
                A = s[Archetype.ARCHER.ordinal()], S = s[Archetype.SURVIVOR.ordinal()];

        Random r = new Random(seed ^ 0x5DEECE66DL);
        float g = 1f + 0.15f * stage; // розвиток зі стадією гри

        power = stat(20 + 30 * (F + 0.5f * M), r, g);
        speed = stat(20 + 30 * (R + 0.3f * F), r, g);
        range = stat(20 + 30 * (A + 0.3f * R + 0.2f * B), r, g);
        durability = stat(20 + 30 * (S + 0.4f * B + 0.2f * M), r, g);
        precision = stat(20 + 30 * (0.6f * A + 0.6f * B + 0.2f * M), r, g);

        // ---- форма вигляду (залежить від душі, але з випадковістю) ----
        Random fr = new Random(seed * 31L + 7L);
        float[] w = {0.6f, 0.2f + S + B + 0.5f * M, 0.2f + F + 0.8f * A + 0.4f * M, 0.2f + R + 0.4f * A + 0.3f * S};
        float tot = 0;
        for (float x : w) tot += x;
        float pick = fr.nextFloat() * tot;
        int fi = 0;
        for (int i = 0; i < w.length; i++) {
            pick -= w[i];
            if (pick <= 0) { fi = i; break; }
        }
        form = StandForm.values()[fi];
        style = fr.nextInt(4);
        switch (form) {
            case ARMORED -> { durability = mul(durability, 1.25f); speed = mul(speed, 0.85f); }
            case WEAPON -> { power = mul(power, 1.2f); range = mul(range, 0.9f); }
            case SWARM -> { speed = mul(speed, 1.2f); range = mul(range, 1.15f); power = mul(power, 0.85f); }
            case HUMANOID -> precision = mul(precision, 1.15f);
        }

        float h1 = frac(primary.hue + (r.nextFloat() - 0.5f) * 0.06f);
        float h2 = frac(secondary.hue + (r.nextFloat() - 0.5f) * 0.06f);
        color1 = MathHelper.hsvToRgb(h1, 0.55f + r.nextFloat() * 0.35f, 0.8f + r.nextFloat() * 0.2f);
        color2 = MathHelper.hsvToRgb(h2, 0.55f + r.nextFloat() * 0.35f, 0.8f + r.nextFloat() * 0.2f);

        bulk = 0.85f + durability / 250f;
        height = 0.95f + power / 400f + stage * 0.02f;
    }

    private static int stat(float base, Random r, float growth) {
        base += r.nextFloat() * 10f - 5f;
        float x = Math.max(5f, base) * growth;
        return Math.round(100f * (1f - (float) Math.exp(-x / 55f))); // м'яке насичення, без жорсткої стелі
    }

    private static int mul(int v, float f) { return Math.min(100, Math.round(v * f)); }

    private static float frac(float h) { return h - (float) Math.floor(h); }

    // Здібності за слотами: 0 основний тип, 1 додатковий тип, 2 форма, 3 ультимейт
    public Skill skill(int slot) {
        return switch (slot) {
            case 0 -> Skill.of(primary);
            case 1 -> Skill.of(secondary);
            case 2 -> Skill.form(form);
            default -> Skill.ultimate(primary);
        };
    }

    public int unlockStage(int slot) { return Skill.UNLOCK[slot]; }

    // Боєві значення
    public float standDamage() { return 1.5f + power * 0.03f; }
    public float leash() { return 5f + range * 0.12f; }

    public static String rank(int v) {
        return v >= 80 ? "A" : v >= 60 ? "B" : v >= 40 ? "C" : v >= 20 ? "D" : "E";
    }

    public List<Text> describe() {
        List<Text> l = new ArrayList<>();
        l.add(Text.literal("✦ " + name + " ✦").formatted(Formatting.GOLD, Formatting.BOLD));
        l.add(Text.literal("Душа: " + primary.title + " / " + secondary.title).formatted(Formatting.YELLOW));
        l.add(Text.literal("Форма: " + form.title + (form == StandForm.WEAPON ? " (" + primary.weaponName + ")" : ""))
                .formatted(Formatting.YELLOW));
        l.add(Text.literal("Стадія " + stage + ": " + Progression.stageName(stage)).formatted(Formatting.AQUA));
        l.add(Text.literal("Сила " + rank(power) + " (" + power + ")  Швидкість " + rank(speed) + " (" + speed + ")")
                .formatted(Formatting.WHITE));
        l.add(Text.literal("Дальність " + rank(range) + " (" + range + ")  Стійкість " + rank(durability)
                + " (" + durability + ")  Точність " + rank(precision) + " (" + precision + ")")
                .formatted(Formatting.WHITE));
        String[] keys = {"X", "C", "V", "B"};
        for (int i = 0; i < 4; i++) {
            boolean open = stage >= unlockStage(i);
            l.add(Text.literal("[" + keys[i] + "] " + skill(i).title + (open ? "" : " (з " + unlockStage(i) + " стадії)"))
                    .formatted(open ? Formatting.GREEN : Formatting.GRAY));
        }
        return l;
    }

    public String scoreLine() {
        StringBuilder sb = new StringBuilder("Бали душі: ");
        for (Archetype a : Archetype.values())
            sb.append(a.title).append(' ').append(String.format("%.2f", scores[a.ordinal()])).append("  ");
        return sb.toString();
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("primary", primary.ordinal());
        n.putInt("secondary", secondary.ordinal());
        n.putLong("seed", seed);
        n.putString("name", name);
        n.putInt("stage", stage);
        for (int i = 0; i < scores.length; i++) n.putFloat("s" + i, scores[i]);
        return n;
    }

    public static StandData fromNbt(NbtCompound n) {
        StandData d = new StandData();
        d.primary = Archetype.values()[n.getInt("primary")];
        d.secondary = Archetype.values()[n.getInt("secondary")];
        d.seed = n.getLong("seed");
        d.name = n.getString("name");
        d.stage = n.getInt("stage");
        for (int i = 0; i < d.scores.length; i++) d.scores[i] = n.getFloat("s" + i);
        d.recalc();
        return d;
    }
}
