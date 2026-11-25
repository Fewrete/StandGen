package dev.standgen.stand;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageTypes;
import net.minecraft.entity.projectile.thrown.SnowballEntity;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Приховані лічильники, з яких при пробудженні визначається стихія. Ванільна статистика не ділить шкоду за видом,
 * тому вогонь, холод, води й пісок рахуються тут за типом шкоди. Гравцям це ніде не показується.
 */
public final class ElementSignals {
    private ElementSignals() {}

    public static final int FIRE = 0, ICE = 1, SAND = 2, WATER = 3;
    public static final int COUNT = 4;

    private static void add(ServerPlayerEntity p, int idx, float amount) {
        StandManager.get(p.getServer()).addSignal(p.getUuid(), idx, amount);
    }

    public static void register() {
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity.getWorld().isClient) return true;
            if (entity instanceof ServerPlayerEntity victim) taken(victim, source, amount);
            if (source.getAttacker() instanceof ServerPlayerEntity att && att != entity) dealt(att, entity, source, amount);
            return true;
        });
    }

    /** Шкода, яку гравець отримав. */
    private static void taken(ServerPlayerEntity p, DamageSource s, float amount) {
        if (s.isIn(DamageTypeTags.IS_FIRE)) add(p, FIRE, amount);
        if (s.isIn(DamageTypeTags.IS_FREEZING)) add(p, ICE, amount);
        if (s.isOf(DamageTypes.DROWN)) add(p, WATER, amount);
        // піском "б'ють" падаючі блоки (пісок, гравій) і задуха всередині блока
        if (s.isOf(DamageTypes.FALLING_BLOCK) || s.isOf(DamageTypes.IN_WALL)) add(p, SAND, amount);
    }

    /** Шкода, яку гравець завдав. */
    private static void dealt(ServerPlayerEntity p, net.minecraft.entity.LivingEntity target, DamageSource s, float amount) {
        if (s.isIn(DamageTypeTags.IS_FIRE) || target.isOnFire()) add(p, FIRE, amount);
        if (s.getSource() instanceof SnowballEntity) add(p, ICE, 1f);
        if (target.isFrozen()) add(p, ICE, amount);
    }
}
