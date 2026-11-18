package dev.standgen.stand;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class StandData {
    // Зберігається
    public Archetype primary = Archetype.FIGHTER;
    public Archetype secondary = Archetype.MINER;
    public long seed;
    public String name = "";
    public float[] scores = new float[Archetype.COUNT];
    public int stage;
    public int milestones;   // маска виконаних віх (для назви етапу)
    public int formId = -1;  // збережена форма; -1 = старий збережений стенд без цього поля

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

        // ---- форма вигляду (залежить від душі, але з випадковістю; після видачі зберігається) ----
        Random fr = new Random(seed * 31L + 7L);
        float u = fr.nextFloat();
        if (formId < 0) formId = pickIndex(formWeights(false), u);   // старий стенд: рахуємо як раніше (4 форми)
        form = StandForm.values()[formId];
        style = fr.nextInt(4);
        switch (form) {
            case ARMORED -> { durability = mul(durability, 1.25f); speed = mul(speed, 0.85f); }
            case WEAPON -> { power = mul(power, 1.2f); range = mul(range, 0.9f); }
            case SWARM -> { speed = mul(speed, 1.2f); range = mul(range, 1.15f); power = mul(power, 0.85f); }
            case HUMANOID -> precision = mul(precision, 1.15f);
            case SUPPORT -> { power = mul(power, 0.6f); range = mul(range, 1.2f); precision = mul(precision, 1.2f); }
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

    /** Ваги форм за душею. Підтримка (п'ята форма) тягнеться до Вижилого та Будівельника. */
    private float[] formWeights(boolean withSupport) {
        float M = Math.min(scores[Archetype.MINER.ordinal()], 2.5f), F = Math.min(scores[Archetype.FIGHTER.ordinal()], 2.5f),
                B = Math.min(scores[Archetype.BUILDER.ordinal()], 2.5f), R = Math.min(scores[Archetype.RUNNER.ordinal()], 2.5f),
                A = Math.min(scores[Archetype.ARCHER.ordinal()], 2.5f), S = Math.min(scores[Archetype.SURVIVOR.ordinal()], 2.5f);
        float[] w = {0.6f, 0.2f + S + B + 0.5f * M, 0.2f + F + 0.8f * A + 0.4f * M, 0.2f + R + 0.4f * A + 0.3f * S};
        if (!withSupport) return w;
        return new float[]{w[0], w[1], w[2], w[3], 0.25f + 0.7f * S + 0.5f * B};
    }

    private static int pickIndex(float[] w, float u) {
        float tot = 0;
        for (float x : w) tot += x;
        float pick = u * tot;
        for (int i = 0; i < w.length; i++) {
            pick -= w[i];
            if (pick <= 0) return i;
        }
        return 0;
    }

    /** Кидає форму для нового стенда (з урахуванням Підтримки) і запам'ятовує її. */
    public void rollForm() {
        formId = pickIndex(formWeights(true), new Random(seed * 31L + 7L).nextFloat());
    }

    private static int mul(int v, float f) { return Math.min(100, Math.round(v * f)); }

    private static float frac(float h) { return h - (float) Math.floor(h); }

    // Здібності за слотами: 0 основний тип, 1 додатковий тип, 2 форма, 3 ультимейт
    public Skill skill(int slot) {
        if (form == StandForm.SUPPORT) {
            return switch (slot) {
                case 0 -> Skill.support(primary);
                case 1 -> Skill.support(secondary);
                case 2 -> Skill.PURIFY;
                default -> Skill.SANCTUARY;
            };
        }
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

    private static MutableText statLine(String key, int v) {
        return Text.translatable("standgen.stat." + key).append(" " + rank(v) + " (" + v + ")");
    }

    public List<Text> describe() {
        List<Text> l = new ArrayList<>();
        l.add(Text.literal("✦ " + name + " ✦").formatted(Formatting.GOLD, Formatting.BOLD));
        l.add(Text.translatable("msg.standgen.soul", primary.text(), secondary.text()).formatted(Formatting.YELLOW));
        l.add((form == StandForm.WEAPON
                ? Text.translatable("msg.standgen.form_weapon", form.text(), primary.weaponText())
                : Text.translatable("msg.standgen.form", form.text())).formatted(Formatting.YELLOW));
        l.add(Text.translatable("msg.standgen.stage", stage, Progression.labelText(milestones)).formatted(Formatting.AQUA));
        l.add(statLine("power", power).append("   ").append(statLine("speed", speed)).formatted(Formatting.WHITE));
        l.add(statLine("range", range).append("   ").append(statLine("durability", durability))
                .append("   ").append(statLine("precision", precision)).formatted(Formatting.WHITE));
        if (form == StandForm.SUPPORT) l.add(Text.translatable("msg.standgen.support_passive").formatted(Formatting.LIGHT_PURPLE));
        for (int i = 0; i < 4; i++) {
            boolean open = stage >= unlockStage(i);
            l.add((open
                    ? Text.translatable("msg.standgen.skill", Text.keybind("key.standgen.skill" + (i + 1)), skill(i).text())
                    : Text.translatable("msg.standgen.skill_locked", Text.keybind("key.standgen.skill" + (i + 1)), skill(i).text(), unlockStage(i)))
                    .formatted(open ? Formatting.GREEN : Formatting.GRAY));
        }
        return l;
    }

    public Text scoreLine() {
        MutableText t = Text.translatable("msg.standgen.scores");
        for (Archetype a : Archetype.values())
            t.append(a.text()).append(" " + String.format(Locale.ROOT, "%.2f", scores[a.ordinal()]) + "  ");
        return t.formatted(Formatting.DARK_GRAY);
    }

    public NbtCompound toNbt() {
        NbtCompound n = new NbtCompound();
        n.putInt("primary", primary.ordinal());
        n.putInt("secondary", secondary.ordinal());
        n.putLong("seed", seed);
        n.putString("name", name);
        n.putInt("stage", stage);
        n.putInt("ms", milestones);
        n.putInt("form", formId);
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
        d.milestones = n.contains("ms") ? n.getInt("ms") : (1 << Math.min(d.stage, 30)) - 1;
        d.formId = n.contains("form") ? n.getInt("form") : -1;
        for (int i = 0; i < d.scores.length; i++) d.scores[i] = n.getFloat("s" + i);
        d.recalc();
        return d;
    }
}
