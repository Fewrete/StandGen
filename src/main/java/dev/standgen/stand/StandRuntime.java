package dev.standgen.stand;

import dev.standgen.StandGenMod;
import dev.standgen.entity.StandEntity;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.ItemEntity;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.util.math.Vec3d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.UpdateSelectedSlotS2CPacket;
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
    private static final Set<UUID> WEAPON_HELD = new HashSet<>();   // форма Зброя в ручному режимі: інструмент у руках
    private static final Set<UUID> SUPPORT_ON = new HashSet<>();   // форма Підтримка: стенд на спині ввімкнено
    private static final Set<UUID> STANCE = new HashSet<>();   // перемикана пасивка Броньована стійка

    private StandRuntime() {}

    public static void clear() {
        ACTIVE.clear(); COOLDOWN.clear(); MANUAL.clear(); HOLD.clear(); REVIVE.clear();
        ARMOR_ON.clear(); BARRAGE.clear(); NEXT_HIT.clear(); STANCE.clear(); WEAPON_HELD.clear(); SUPPORT_ON.clear();
    }

    public static void forget(UUID id) {
        ACTIVE.remove(id); COOLDOWN.remove(id); MANUAL.remove(id); HOLD.remove(id); REVIVE.remove(id);
        ARMOR_ON.remove(id); BARRAGE.remove(id); NEXT_HIT.remove(id); STANCE.remove(id); WEAPON_HELD.remove(id); SUPPORT_ON.remove(id);
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

        // форма Зброя: у ручному режимі зброя видається в руки як інструмент, в авто-режимі літає поруч
        StandData d = StandManager.get(p.getServer()).get(id);
        if (d != null && d.form == StandForm.WEAPON) {
            StandEntity cur = getStand(p);
            if (nowManual && cur != null) {
                if (!giveWeapon(p, d)) { MANUAL.remove(id); return; }   // немає вільного слота: лишаємось в авто-режимі
                cur.discard();
                ACTIVE.remove(id);
                WEAPON_HELD.add(id);
            } else if (!nowManual && WEAPON_HELD.contains(id)) {
                WEAPON_HELD.remove(id);
                stripWeapon(p);
                spawnStand(p, d);
            }
        }
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

    // ================= Підтримка: стенд на спині =================
    public static boolean isSupportOn(UUID id) { return SUPPORT_ON.contains(id); }

    private static PacketByteBuf supportBuf(ServerPlayerEntity p, StandData d, boolean on) {
        PacketByteBuf b = PacketByteBufs.create();
        b.writeUuid(p.getUuid());
        b.writeBoolean(on);
        if (on) {
            b.writeInt(d.color1); b.writeInt(d.color2); b.writeInt(d.stage); b.writeInt(d.style); b.writeInt(d.primary.ordinal());
        }
        return b;
    }

    /** Повідомляє власнику й усім, хто його бачить, що стенд на спині ввімкнено чи вимкнено. */
    private static void broadcastSupport(ServerPlayerEntity p, StandData d, boolean on) {
        ServerPlayNetworking.send(p, StandGenMod.SUPPORT_BACK, supportBuf(p, d, on));
        for (ServerPlayerEntity v : PlayerLookup.tracking(p)) {
            ServerPlayNetworking.send(v, StandGenMod.SUPPORT_BACK, supportBuf(p, d, on));
        }
    }

    /** Новому глядачеві, що почав бачити власника, передаємо стан стенда на спині. */
    public static void sendSupportTo(ServerPlayerEntity owner, ServerPlayerEntity viewer) {
        if (!SUPPORT_ON.contains(owner.getUuid())) return;
        StandData d = StandManager.get(owner.getServer()).get(owner.getUuid());
        if (d != null) ServerPlayNetworking.send(viewer, StandGenMod.SUPPORT_BACK, supportBuf(owner, d, true));
    }

    private static void refreshSupport(ServerPlayerEntity p, StandData d) {
        if (SUPPORT_ON.contains(p.getUuid())) broadcastSupport(p, d, true);
    }

    private static void toggleSupport(ServerPlayerEntity p, StandData d) {
        UUID id = p.getUuid();
        if (SUPPORT_ON.remove(id)) {
            broadcastSupport(p, d, false);
            msg(p, "msg.standgen.dismissed", Formatting.GRAY);
            return;
        }
        SUPPORT_ON.add(id);
        broadcastSupport(p, d, true);
        msg(p, Text.literal(d.name), Formatting.GOLD);
    }

    /** Пасивка Підтримки "Магніт душі" (вимикається в ручному режимі) і легкі частинки для власників стендів. */
    private static void supportTick(ServerPlayerEntity p, StandData d, int now) {
        ServerWorld w = (ServerWorld) p.getWorld();
        if (!MANUAL.contains(p.getUuid()) && now % 4 == 0) {
            double mr = 4.0 + d.stage * 0.5;
            Vec3d pull = p.getPos().add(0, 0.6, 0);
            for (ItemEntity it : w.getEntitiesByClass(ItemEntity.class, p.getBoundingBox().expand(mr), x -> x.getItemAge() > 45)) {
                Vec3d dir = pull.subtract(it.getPos());
                if (dir.lengthSquared() < 0.25) continue;
                it.setVelocity(dir.normalize().multiply(0.3));
                it.velocityModified = true;
            }
        }
        if (d.stage >= 1 && now % 12 == 0) {
            StandManager m = StandManager.get(p.getServer());
            for (ServerPlayerEntity v : w.getPlayers()) {
                if (v.squaredDistanceTo(p) < 1024 && m.get(v.getUuid()) != null) {
                    w.spawnParticles(v, ParticleTypes.END_ROD, false, p.getX(), p.getY() + 1.3, p.getZ(), 1, 0.3, 0.5, 0.3, 0.01);
                }
            }
        }
    }

    /** Оновити показники зброї в руках після еволюції (комірка та сама). */
    public static void refreshWeapon(ServerPlayerEntity p, StandData d) {
        if (!WEAPON_HELD.contains(p.getUuid())) return;
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++)
            if (StandWeapon.isStandWeapon(inv.getStack(i))) inv.setStack(i, StandWeapon.create(d, p.getUuid()));
    }

    /** Усі предмети-стенди (броня й зброя) ростуть разом зі стадією. */
    public static void refreshItems(ServerPlayerEntity p, StandData d) {
        refreshArmor(p, d);
        refreshWeapon(p, d);
        refreshSupport(p, d);
    }

    private static void toggleArmor(ServerPlayerEntity p, StandData d) {
        UUID id = p.getUuid();
        if (ARMOR_ON.contains(id)) {
            ARMOR_ON.remove(id);
            STANCE.remove(id);
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

    private static void toggleStance(ServerPlayerEntity p) {
        UUID id = p.getUuid();
        boolean on = !STANCE.contains(id);
        if (on) STANCE.add(id); else STANCE.remove(id);
        p.sendMessage(Text.translatable(on ? "msg.standgen.toggle_on" : "msg.standgen.toggle_off", Skill.WALL.text())
                .formatted(on ? Formatting.GREEN : Formatting.GRAY), true);
        ((ServerWorld) p.getWorld()).playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_ANVIL_PLACE,
                SoundCategory.PLAYERS, 0.8f, on ? 0.7f : 1.2f);
    }

    /** Прибрати стенд будь-якої форми (сутність або броню). */
    public static void dismiss(ServerPlayerEntity p) {
        StandEntity s = getStand(p);
        if (s != null) s.discard();
        ACTIVE.remove(p.getUuid());
        boolean was = ARMOR_ON.remove(p.getUuid());
        STANCE.remove(p.getUuid());
        if (was || hasStandArmor(p)) stripArmor(p);
        WEAPON_HELD.remove(p.getUuid());
        stripWeapon(p);
        if (SUPPORT_ON.remove(p.getUuid())) broadcastSupport(p, null, false);
    }

    /** Щотіка: чистка загублених предметів броні, пасивки й ближній бій броні. */
    public static void tickArmor(MinecraftServer server) {
        int now = server.getTicks();
        StandManager mgr = StandManager.get(server);
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            UUID id = p.getUuid();
            if (now % 20 == 0) {
                boolean has = hasStandArmor(p);
                if (!ARMOR_ON.contains(id) && has) stripArmor(p);            // залишилась від минулої сесії
                else if (ARMOR_ON.contains(id) && !has) ARMOR_ON.remove(id);   // зникла (смерть, команди)
                boolean hasW = hasStandWeapon(p);
                if (!WEAPON_HELD.contains(id) && hasW) stripWeapon(p);
                else if (WEAPON_HELD.contains(id) && !hasW) WEAPON_HELD.remove(id);
            }
            boolean armor = ARMOR_ON.contains(id);
            boolean weapon = WEAPON_HELD.contains(id);
            if (!armor) STANCE.remove(id);
            boolean support = SUPPORT_ON.contains(id);
            if (!armor && !weapon && !support) continue;
            StandData d = mgr.get(id);
            if (d == null) {
                stripArmor(p); stripWeapon(p);
                ARMOR_ON.remove(id); WEAPON_HELD.remove(id); STANCE.remove(id); SUPPORT_ON.remove(id);
                continue;
            }
            if (support) { supportTick(p, d, now); continue; }   // Підтримка нічого не б'є
            boolean stance = armor && STANCE.contains(id);
            if (stance && now % 10 == 0) {
                // Стійка: Опір IV (як було) ціною Уповільнення II. Ефекти короткі й оновлюються, тож зникають одразу після вимкнення.
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 25, 3, true, true));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 1, true, true));
            }
            // у стійці пасивка Сили (Боєць) вимкнена
            if (now % 40 == 0 && !(stance && d.primary == Archetype.FIGHTER)) Abilities.applyPassive(p, d);
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
        if (d.form == StandForm.SUPPORT) { toggleSupport(p, d); return; }
        if (d.form == StandForm.WEAPON && MANUAL.contains(p.getUuid())) {
            UUID id = p.getUuid();
            if (WEAPON_HELD.contains(id)) {
                WEAPON_HELD.remove(id);
                stripWeapon(p);
                msg(p, "msg.standgen.dismissed", Formatting.GRAY);
                return;
            }
            if (!giveWeapon(p, d)) return;
            StandEntity cur = getStand(p);
            if (cur != null) { cur.discard(); ACTIVE.remove(id); }
            WEAPON_HELD.add(id);
            msg(p, Text.literal(d.name), Formatting.GOLD);
            return;
        }

        StandEntity existing = getStand(p);
        if (existing != null) {
            existing.discard();
            ACTIVE.remove(p.getUuid());
            msg(p, "msg.standgen.dismissed", Formatting.GRAY);
            return;
        }
        spawnStand(p, d);
        msg(p, Text.literal(d.name), Formatting.GOLD);
    }

    private static void spawnStand(ServerPlayerEntity p, StandData d) {
        ServerWorld w = (ServerWorld) p.getWorld();
        StandEntity s = new StandEntity(StandGenMod.STAND, w);
        s.setOwner(p.getUuid());
        s.applyData(d);
        s.refreshPositionAndAngles(p.getX(), p.getY() + 0.3, p.getZ(), p.getYaw(), 0);
        w.spawnEntity(s);
        ACTIVE.put(p.getUuid(), s.getUuid());
    }

    // ================= Зброя в руках (форма Зброя, ручний режим) =================
    public static boolean isWeaponHeld(UUID id) { return WEAPON_HELD.contains(id); }

    private static boolean hasStandWeapon(ServerPlayerEntity p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) if (StandWeapon.isStandWeapon(inv.getStack(i))) return true;
        return StandWeapon.isStandWeapon(p.currentScreenHandler.getCursorStack());
    }

    private static void stripWeapon(ServerPlayerEntity p) {
        var inv = p.getInventory();
        for (int i = 0; i < inv.size(); i++) if (StandWeapon.isStandWeapon(inv.getStack(i))) inv.setStack(i, ItemStack.EMPTY);
        if (StandWeapon.isStandWeapon(p.currentScreenHandler.getCursorStack())) p.currentScreenHandler.setCursorStack(ItemStack.EMPTY);
    }

    /** Кладе зброю в обрану комірку (якщо порожня) або в першу вільну комірку хотбара й вибирає її. */
    private static boolean giveWeapon(ServerPlayerEntity p, StandData d) {
        var inv = p.getInventory();
        int slot = inv.selectedSlot;
        if (!inv.getStack(slot).isEmpty()) {
            slot = -1;
            for (int i = 0; i < 9; i++) if (inv.getStack(i).isEmpty()) { slot = i; break; }
            if (slot < 0) { msg(p, "msg.standgen.free_slot", Formatting.RED); return false; }
        }
        inv.setStack(slot, StandWeapon.create(d, p.getUuid()));
        if (inv.selectedSlot != slot) {
            inv.selectedSlot = slot;
            p.networkHandler.sendPacket(new UpdateSelectedSlotS2CPacket(slot));
        }
        return true;
    }

    public static void useAbility(ServerPlayerEntity p, int slot) {
        StandData d = StandManager.get(p.getServer()).get(p.getUuid());
        if (d == null) { msg(p, "msg.standgen.no_stand", Formatting.RED); return; }
        StandEntity s = null;
        if (d.form == StandForm.SUPPORT) {
            if (!SUPPORT_ON.contains(p.getUuid())) { msg(p, "msg.standgen.summon_first", Formatting.RED); return; }
        } else if (d.form == StandForm.ARMORED) {
            if (!ARMOR_ON.contains(p.getUuid())) { msg(p, "msg.standgen.summon_first", Formatting.RED); return; }
        } else {
            s = getStand(p);
            if (s == null && !(d.form == StandForm.WEAPON && WEAPON_HELD.contains(p.getUuid()))) {
                msg(p, "msg.standgen.summon_first", Formatting.RED);
                return;
            }
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
        if (sk == Skill.WALL && d.form == StandForm.ARMORED) {   // перемикач, а не здібність з перезарядкою
            cd[slot] = now + Abilities.cooldown(sk, d);
            toggleStance(p);
            return;
        }
        if (Abilities.execute(sk, p, s, d)) {
            cd[slot] = now + Abilities.cooldown(sk, d);
            msg(p, "msg.standgen.used", Formatting.LIGHT_PURPLE, sk.text());
        }
    }
}
