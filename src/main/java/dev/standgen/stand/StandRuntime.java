package dev.standgen.stand;

import dev.standgen.StandGenMod;
import dev.standgen.entity.StandEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Тимчасовий стан: хто викликав стенд, перезарядки. */
public final class StandRuntime {
    private static final Map<UUID, UUID> ACTIVE = new HashMap<>();
    private static final Map<UUID, int[]> COOLDOWN = new HashMap<>();

    private StandRuntime() {}

    public static void clear() { ACTIVE.clear(); COOLDOWN.clear(); }

    public static StandEntity getStand(ServerPlayerEntity p) {
        UUID id = ACTIVE.get(p.getUuid());
        if (id == null) return null;
        Entity e = ((ServerWorld) p.getWorld()).getEntity(id);
        if (e instanceof StandEntity s && s.isAlive()) return s;
        ACTIVE.remove(p.getUuid());
        return null;
    }

    private static void msg(ServerPlayerEntity p, String s, Formatting f) {
        p.sendMessage(Text.literal(s).formatted(f), true);
    }

    public static void toggle(ServerPlayerEntity p) {
        StandData d = StandManager.get(p.getServer()).get(p.getUuid());
        if (d == null) {
            msg(p, "У тебе ще немає стенда. Використай Стрілу Стенда.", Formatting.RED);
            return;
        }
        StandEntity existing = getStand(p);
        if (existing != null) {
            existing.discard();
            ACTIVE.remove(p.getUuid());
            msg(p, "Стенд прибрано", Formatting.GRAY);
            return;
        }
        ServerWorld w = (ServerWorld) p.getWorld();
        StandEntity s = new StandEntity(StandGenMod.STAND, w);
        s.setOwner(p.getUuid());
        s.applyData(d);
        s.refreshPositionAndAngles(p.getX(), p.getY() + 0.3, p.getZ(), p.getYaw(), 0);
        w.spawnEntity(s);
        ACTIVE.put(p.getUuid(), s.getUuid());
        msg(p, d.name, Formatting.GOLD);
    }

    public static void useAbility(ServerPlayerEntity p, int slot) {
        StandData d = StandManager.get(p.getServer()).get(p.getUuid());
        if (d == null) { msg(p, "У тебе ще немає стенда", Formatting.RED); return; }
        StandEntity s = getStand(p);
        if (s == null) { msg(p, "Спочатку виклич стенд", Formatting.RED); return; }
        if (slot == 1 && d.stage < 2) { msg(p, "Друга здібність відкриється на 2 стадії", Formatting.RED); return; }

        Archetype a = slot == 0 ? d.primary : d.secondary;
        int[] cd = COOLDOWN.computeIfAbsent(p.getUuid(), k -> new int[2]);
        int now = p.getServer().getTicks();
        if (now < cd[slot]) {
            msg(p, "Перезарядка: " + ((cd[slot] - now) / 20 + 1) + " с", Formatting.RED);
            return;
        }
        if (Abilities.execute(a, p, s, d)) {
            cd[slot] = now + Abilities.cooldown(a, d);
            msg(p, a.abilityName + "!", Formatting.LIGHT_PURPLE);
        }
    }
}
