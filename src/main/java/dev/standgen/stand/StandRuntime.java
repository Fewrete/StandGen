package dev.standgen.stand;

import dev.standgen.StandGenMod;
import dev.standgen.entity.StandEntity;
import net.minecraft.entity.Entity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Тимчасовий стан: хто викликав стенд, перезарядки, режим керування, "другий шанс". */
public final class StandRuntime {
    private static final Map<UUID, UUID> ACTIVE = new HashMap<>();
    private static final Map<UUID, int[]> COOLDOWN = new HashMap<>();
    private static final Set<UUID> MANUAL = new HashSet<>();
    private static final Map<UUID, Integer> HOLD = new HashMap<>();    // до якого тіка діє утримання атаки
    private static final Map<UUID, Integer> REVIVE = new HashMap<>();  // до якого тіка діє "другий шанс"

    private StandRuntime() {}

    public static void clear() {
        ACTIVE.clear(); COOLDOWN.clear(); MANUAL.clear(); HOLD.clear(); REVIVE.clear();
    }

    public static void forget(UUID id) {
        ACTIVE.remove(id); COOLDOWN.remove(id); MANUAL.remove(id); HOLD.remove(id); REVIVE.remove(id);
    }

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

    // ---- режими керування ----
    public static boolean isManual(UUID id) { return MANUAL.contains(id); }

    public static boolean isHolding(UUID id, int nowTick) {
        Integer t = HOLD.get(id);
        return t != null && nowTick <= t;
    }

    public static void setHold(ServerPlayerEntity p, boolean down) {
        if (down) HOLD.put(p.getUuid(), p.getServer().getTicks() + 40);
        else HOLD.remove(p.getUuid());
    }

    public static void toggleManual(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        boolean nowManual = !MANUAL.contains(id);
        if (nowManual) MANUAL.add(id);
        else { MANUAL.remove(id); HOLD.remove(id); }
        msg(p, nowManual ? "Стенд: РУЧНЕ керування (утримуй клавішу атаки)" : "Стенд: АВТОМАТИЧНИЙ режим", Formatting.AQUA);
    }

    // ---- "Другий шанс" ----
    public static void armRevive(ServerPlayerEntity p) { REVIVE.put(p.getUuid(), p.getServer().getTicks() + 600); }

    public static boolean consumeRevive(ServerPlayerEntity p) {
        Integer t = REVIVE.get(p.getUuid());
        if (t != null && p.getServer().getTicks() <= t) {
            REVIVE.remove(p.getUuid());
            return true;
        }
        return false;
    }

    // ---- виклик і здібності ----
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
        if (d.stage < d.unlockStage(slot)) {
            msg(p, "Ця здібність відкриється на " + d.unlockStage(slot) + " стадії", Formatting.RED);
            return;
        }
        Skill sk = d.skill(slot);
        int[] cd = COOLDOWN.computeIfAbsent(p.getUuid(), k -> new int[4]);
        int now = p.getServer().getTicks();
        if (now < cd[slot]) {
            msg(p, sk.title + ": перезарядка " + ((cd[slot] - now) / 20 + 1) + " с", Formatting.RED);
            return;
        }
        if (Abilities.execute(sk, p, s, d)) {
            cd[slot] = now + Abilities.cooldown(sk, d);
            msg(p, sk.title + "!", Formatting.LIGHT_PURPLE);
        }
    }
}
