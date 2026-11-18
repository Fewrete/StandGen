package dev.standgen.stand;

import net.minecraft.advancement.Advancement;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Стадія гри = кількість виконаних віх (advancements).
 * Щоб додати віху з іншого мода / збірки: Progression.addMilestone(new Identifier("modid", "path"));
 * Назви стадій: ключі standgen.stage.0 ... standgen.stage.8 у мовних файлах.
 */
public final class Progression {
    private static final List<Identifier> MILESTONES = new ArrayList<>(List.of(
            new Identifier("story/smelt_iron"),
            new Identifier("story/mine_diamond"),
            new Identifier("story/enter_the_nether"),
            new Identifier("nether/find_fortress"),
            new Identifier("nether/summon_wither"),
            new Identifier("story/enter_the_end"),
            new Identifier("end/kill_dragon"),
            new Identifier("end/elytra")
    ));

    private Progression() {}

    public static void addMilestone(Identifier id) {
        if (!MILESTONES.contains(id)) MILESTONES.add(id);
    }

    /** Бітова маска виконаних віх (біт i = i-та віха зі списку). */
    public static int mask(ServerPlayerEntity p) {
        int m = 0;
        var loader = p.getServer().getAdvancementLoader();
        for (int i = 0; i < MILESTONES.size() && i < 31; i++) {
            Advancement a = loader.get(MILESTONES.get(i));
            if (a != null && p.getAdvancementTracker().getProgress(a).isDone()) m |= 1 << i;
        }
        return m;
    }

    /** Стадія = кількість виконаних віх. */
    public static int stage(ServerPlayerEntity p) { return Integer.bitCount(mask(p)); }

    /** Назва етапу = найвища досягнута віха (Нижній світ до алмазів не перетворюється на "Алмази"). */
    public static Text labelText(int mask) {
        int hi = mask == 0 ? 0 : 32 - Integer.numberOfLeadingZeros(mask);
        return Text.translatable("standgen.stage." + Math.min(hi, 8));
    }

    public static Text stageText(int stage) {
        return Text.translatable("standgen.stage." + Math.min(stage, 8));
    }
}
