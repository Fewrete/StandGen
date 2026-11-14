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

    public static int stage(ServerPlayerEntity p) {
        int n = 0;
        var loader = p.getServer().getAdvancementLoader();
        for (Identifier id : MILESTONES) {
            Advancement a = loader.get(id);
            if (a != null && p.getAdvancementTracker().getProgress(a).isDone()) n++;
        }
        return n;
    }

    public static Text stageText(int stage) {
        return Text.translatable("standgen.stage." + Math.min(stage, 8));
    }
}
