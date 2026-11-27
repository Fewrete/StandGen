package dev.standgen.stand;

import dev.standgen.entity.StandEntity;
import net.fabricmc.fabric.api.entity.event.v1.EntityElytraEvents;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.s2c.play.EntityVelocityUpdateS2CPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Їзда на тваринах. Кінь і ведмідь: гравець сідає на стенд (рухом керує сервер за введенням гравця).
 * Орел: гравець висить під орлом і планерить (ваніль-підхід до польоту з елітрами, лише без елітр).
 */
public final class AnimalRide {
    private AnimalRide() {}

    /** Гравці, що щойно стартували планерування: чекаємо, поки вони відірвуться від землі. Значення = тіків лишилось. */
    private static final Map<UUID, Integer> PENDING = new HashMap<>();
    /** Гравці, що планерять. Значення = тіків підряд без польоту (посадка). */
    private static final Map<UUID, Integer> GLIDING = new HashMap<>();

    public static boolean isGliding(UUID id) { return GLIDING.containsKey(id) || PENDING.containsKey(id); }

    public static void register() {
        // дозволяє серверу тримати прапор польоту без елітр, поки гравець планерить на орлі
        EntityElytraEvents.CUSTOM.register((entity, tickElytra) ->
                entity instanceof ServerPlayerEntity p && isGliding(p.getUuid()));
    }

    /** Миттєвий поштовх гравцю: сервер сам розсилає швидкість гравцю (для гравців це не робиться автоматично). */
    public static void launch(ServerPlayerEntity p, Vec3d v) {
        p.setVelocity(v);
        p.velocityModified = true;
        p.fallDistance = 0;
        p.networkHandler.sendPacket(new EntityVelocityUpdateS2CPacket(p));
    }

    private static void msg(ServerPlayerEntity p, String key, Formatting f) {
        p.sendMessage(Text.translatable(key).formatted(f), true);
    }

    /** Клавіша "осідлати / ширяти". */
    public static void mount(ServerPlayerEntity p) {
        StandData d = StandManager.get(p.getServer()).get(p.getUuid());
        if (d == null) { msg(p, "msg.standgen.no_stand", Formatting.RED); return; }
        if (d.baseForm() != StandForm.ANIMAL || d.animal == Animal.NONE) { msg(p, "msg.standgen.no_beast", Formatting.RED); return; }
        Animal an = d.animal;
        StandEntity s = StandRuntime.getStand(p);
        if (an.glides()) {
            if (isGliding(p.getUuid())) endGlide(p, true);
            else if (s == null) msg(p, "msg.standgen.summon_first", Formatting.RED);
            else startGlide(p);
        } else if (an.rideable()) {
            if (s != null && p.getVehicle() == s) {
                p.stopRiding();
            } else if (s == null) {
                msg(p, "msg.standgen.summon_first", Formatting.RED);
            } else {
                s.refreshPositionAndAngles(p.getX(), p.getY(), p.getZ(), p.getYaw(), 0f);
                p.startRiding(s, true);
            }
        } else {
            msg(p, "msg.standgen.not_rideable", Formatting.YELLOW);
        }
    }

    private static void startGlide(ServerPlayerEntity p) {
        Vec3d look = p.getRotationVec(1f);
        if (p.isOnGround()) launch(p, new Vec3d(look.x * 0.45, 1.0, look.z * 0.45));
        else launch(p, p.getVelocity().add(look.multiply(0.4)));
        PENDING.put(p.getUuid(), 30);
        p.getWorld().playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PHANTOM_FLAP, SoundCategory.PLAYERS, 1.2f, 1.1f);
    }

    private static void endGlide(ServerPlayerEntity p, boolean softLand) {
        PENDING.remove(p.getUuid());
        GLIDING.remove(p.getUuid());
        if (p.isFallFlying()) p.stopFallFlying();
        if (softLand) p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 120, 0, true, false));
    }

    public static void tick(MinecraftServer server) {
        if (PENDING.isEmpty() && GLIDING.isEmpty()) return;
        Iterator<Map.Entry<UUID, Integer>> it = PENDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null || !p.isAlive()) { it.remove(); continue; }
            int left = en.getValue() - 1;
            if (!p.isOnGround() && left <= 27) {           // відірвався від землі: вмикаємо планерування
                p.startFallFlying();
                GLIDING.put(p.getUuid(), 0);
                it.remove();
            } else if (left <= 0) {
                it.remove();                               // не злетів
            } else {
                en.setValue(left);
            }
        }
        it = GLIDING.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Integer> en = it.next();
            ServerPlayerEntity p = server.getPlayerManager().getPlayer(en.getKey());
            if (p == null || !p.isAlive() || StandRuntime.getStand(p) == null) {
                if (p != null && p.isFallFlying()) p.stopFallFlying();
                it.remove();
                continue;
            }
            if (p.isFallFlying()) {
                en.setValue(0);
            } else if (en.getValue() + 1 >= 6) {           // приземлився або зупинився
                it.remove();
            } else {
                en.setValue(en.getValue() + 1);
            }
        }
    }

    public static void endAll() { PENDING.clear(); GLIDING.clear(); }
}
