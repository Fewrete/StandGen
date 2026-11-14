package dev.standgen.stand;

import dev.standgen.StandGenMod;
import dev.standgen.entity.StandEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Тимчасовий стан: хто викликав стенд, перезарядки, режим керування, броня-стенд, "другий шанс". */
public final class StandRuntime {
    private static final Map<UUID, UUID> ACTIVE = new HashMap<>();
    private static final Map<UUID, int[]> COOLDOWN = new HashMap<>();
    private static final Set<UUID> MANUAL = new HashSet<>();
    private static final Map<UUID, Integer> HOLD = new HashMap<>();    // до якого тіка діє утримання атаки
    private static final Map<UUID, Integer> REVIVE = new HashMap<>();  // до якого тіка діє "другий шанс"
    // форма Броня
    private static final Set<UUID> ARMOR_ON = new HashSet<>();
    private static final Map<UUID, Integer> BARRAGE = new HashMap<>();  // до якого тіка триває серія ударів броні
    private static final Map<UUID, Integer> NEXT_HIT = new HashMap<>();

    private StandRuntime() {}

    public static void clear() {
        ACTIVE.clear(); COOLDOWN.clear(); MANUAL.clear(); HOLD.clear(); REVIVE.clear();
        ARMOR_ON.clear(); BARRAGE.clear(); NEXT_HIT.clear();
    }

    public static void forget(UUID id) {
        ACTIVE.remove(id); COOLDOWN.remove(id); MANUAL.remove(id); HOLD.remove(id); REVIVE.remove(id);
        ARMOR_ON.remove(id); BARRAGE.remove(id); NEXT_HIT.remove(id);
    }

    public static StandEntity getStand(ServerPlayerEntity p) {
        UUID id = ACTIVE.get(p.getUuid());
        if (id == null) return null;
        Entity e = ((ServerWorld) p.getWorld()).getEntity(id);
        if (e instanceof StandEntity s && s.isAlive()) return s;
        ACTIVE.remove(p.getUuid());
        return null;
    }

    private static void msg(ServerPlayerEntity p, Text t, Formatting f) {
        p.sendMessage(t.copy().formatted(f), true);
    }

    private static void msg(ServerPlayerEntity p, String key, Formatting f, Object... args) {
        p.sendMessage(Text.translatable(key, args).formatted(f), true);
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
        msg(p, nowManual ? "msg.standgen.mode_manual" : "msg.standgen.mode_auto", Formatting.AQUA);
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

    // ---- серія ударів (Шквал, Режим люті): стендом або бронею ----
    public static void startBarrage(ServerPlayerEntity p, StandEntity stand, int ticks) {
        if (stand != null) stand.startBarrage(ticks);
        else BARRAGE.put(p.getUuid(), p.getServer().getTicks() + ticks);
    }

    // ================= Броня =================
    public static boolean isArmorOn(UUID id) { return ARMOR_ON.contains(id); }

    private static boolean hasStandArmor(ServerPlayerEntity p) {
        for (EquipmentSlot s : StandArmor.SLOTS) if (StandArmor.isStandArmor(p.getEquippedStack(s))) return true;
        return false;
    }

    private static void stripArmor(ServerPlayerEntity p) {
        for (EquipmentSlot s : StandArmor.SLOTS)
            if (StandArmor.isStandArmor(p.getEquippedStack(s))) p.equipStack(s, ItemStack.EMPTY);
    }

    private static void equipArmor(ServerPlayerEntity p, StandData d) {
        for (int i = 0; i < StandArmor.SLOTS.length; i++) p.equipStack(StandArmor.SLOTS[i], StandArmor.create(d, i));
    }

    /** Оновити показники надітої броні (після еволюції). */
    public static void refreshArmor(ServerPlayerEntity p, StandData d) {
        if (ARMOR_ON.contains(p.getUuid())) equipArmor(p, d);
    }

    private static void toggleArmor(ServerPlayerEntity p, StandData d) {
        UUID id = p.getUuid();
        if (ARMOR_ON.contains(id)) {
            ARMOR_ON.remove(id);
            stripArmor(p);
            msg(p, "msg.standgen.dismissed", Formatting.GRAY);
            return;
        }
        for (EquipmentSlot s : StandArmor.SLOTS) {
            if (!p.getEquippedStack(s).isEmpty()) {   // активується лише при знятій звичайній броні
                msg(p, "msg.standgen.armor_remove_first", Formatting.RED);
                return;
            }
        }
        equipArmor(p, d);
        ARMOR_ON.add(id);
        ((ServerWorld) p.getWorld()).spawnParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.4);
        msg(p, Text.literal(d.name), Formatting.GOLD);
    }

    /** Прибрати стенд будь-якої форми (сутність або броню). */
    public static void dismiss(ServerPlayerEntity p) {
        StandEntity s = getStand(p);
        if (s != null) s.discard();
        ACTIVE.remove(p.getUuid());
        boolean was = ARMOR_ON.remove(p.getUuid());
        if (was || hasStandArmor(p)) stripArmor(p);
    }

    /** Щотіка: чистка загублених предметів броні, пасивки й ближній бій броні. */
    public static void tickArmor(MinecraftServer server) {
        int now = server.getTicks();
        StandManager mgr = StandManager.get(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UUID id = p.getUuid();
            if (now % 20 == 0) {
                boolean has = hasStandArmor(p);
                if (!ARMOR_ON.contains(id) && has) stripArmor(p);          // залишилась від минулої сесії
                else if (ARMOR_ON.contains(id) && !has) ARMOR_ON.remove(id); // зникла (смерть, команди)
            }
            if (!ARMOR_ON.contains(id)) continue;
            StandData d = mgr.get(id);
            if (d == null) { stripArmor(p); ARMOR_ON.remove(id); continue; }
            if (now % 40 == 0) Abilities.applyPassive(p, d);
            armorStrike(p, d, now);
        }
    }

    /**
     * Ближній бій броні. Авто: б'є ворога впритул (до 2.5 блоків). Ручний: б'є, поки утримується клавіша атаки.
     * Серія ударів (Шквал / Режим люті) діє в обох режимах. Дальність маленька: броня не б'є далеко.
     */
    private static void armorStrike(ServerPlayerEntity p, StandData d, int now) {
        UUID id = p.getUuid();
        boolean barrage = BARRAGE.getOrDefault(id, 0) > now;
        boolean manual = MANUAL.contains(id);
        boolean holding = manual && isHolding(id, now);
        if (manual && !holding && !barrage) return;
        if (now < NEXT_HIT.getOrDefault(id, 0)) return;

        ServerWorld w = (ServerWorld) p.getWorld();
        double reach = Math.min(4.0, 2.5 + d.range * 0.015);
        LivingEntity t;
        if (holding || barrage) {
            t = Abilities.lookedEntity(w, p, reach);
            if (t == null && barrage) t = Abilities.nearestHostile(w, p, reach);
        } else {
            t = Abilities.nearestHostile(w, p, 2.5);
        }
        if (t == null) return;

        NEXT_HIT.put(id, now + (barrage ? 3 : Math.max(8, 20 - d.speed / 10)));
        float dmg = d.standDamage() * (barrage ? 0.3f : 1f);
        t.timeUntilRegen = 0;
        if (t.damage(w.getDamageSources().playerAttack(p), dmg)) {
            if (!barrage) t.takeKnockback(0.4, p.getX() - t.getX(), p.getZ() - t.getZ());
            p.swingHand(Hand.MAIN_HAND, true);
            w.spawnParticles(ParticleTypes.CRIT, t.getX(), t.getBodyY(0.5), t.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
            w.playSound(null, t.getX(), t.getY(), t.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_STRONG,
                    SoundCategory.PLAYERS, 0.6f, barrage ? 1.4f : 1.0f);
        }
    }

    // ---- виклик і здібності ----
    public static void toggle(ServerPlayerEntity p) {
        StandData d = StandManager.get(p.getServer()).get(p.getUuid());
        if (d == null) {
            msg(p, "msg.standgen.no_stand_hint", Formatting.RED);
            return;
        }
        if (d.form == StandForm.ARMORED) { toggleArmor(p, d); return; }

        StandEntity existing = getStand(p);
        if (existing != null) {
            existing.discard();
            ACTIVE.remove(p.getUuid());
            msg(p, "msg.standgen.dismissed", Formatting.GRAY);
            return;
        }
        ServerWorld w = (ServerWorld) p.getWorld();
        StandEntity s = new StandEntity(StandGenMod.STAND, w);
        s.setOwner(p.getUuid());
        s.applyData(d);
        s.refreshPositionAndAngles(p.getX(), p.getY() + 0.3, p.getZ(), p.getYaw(), 0);
        w.spawnEntity(s);
        ACTIVE.put(p.getUuid(), s.getUuid());
        msg(p, Text.literal(d.name), Formatting.GOLD);
    }

    public static void useAbility(ServerPlayerEntity p, int slot) {
        StandData d = StandManager.get(p.getServer()).get(p.getUuid());
        if (d == null) { msg(p, "msg.standgen.no_stand", Formatting.RED); return; }
        StandEntity s = null;
        if (d.form == StandForm.ARMORED) {
            if (!ARMOR_ON.contains(p.getUuid())) { msg(p, "msg.standgen.summon_first", Formatting.RED); return; }
        } else {
            s = getStand(p);
            if (s == null) { msg(p, "msg.standgen.summon_first", Formatting.RED); return; }
        }
        if (d.stage < d.unlockStage(slot)) {
            msg(p, "msg.standgen.locked", Formatting.RED, d.unlockStage(slot));
            return;
        }
        Skill sk = d.skill(slot);
        int[] cd = COOLDOWN.computeIfAbsent(p.getUuid(), k -> new int[4]);
        int now = p.getServer().getTicks();
        if (now < cd[slot]) {
            msg(p, "msg.standgen.cooldown", Formatting.RED, sk.text(), (cd[slot] - now) / 20 + 1);
            return;
        }
        if (Abilities.execute(sk, p, s, d)) {
            cd[slot] = now + Abilities.cooldown(sk, d);
            msg(p, "msg.standgen.used", Formatting.LIGHT_PURPLE, sk.text());
        }
    }
}
