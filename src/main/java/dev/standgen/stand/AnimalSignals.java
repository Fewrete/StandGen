package dev.standgen.stand;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.passive.AnimalEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;

/**
 * Приховані лічильники приручення й розведення тварин для визначення форми Звір (див. AnimalRoll).
 * Ванільна статистика не ділить розведення за видами, тож рахуємо самі: годування для розведення
 * і приручення. Гравцям це ніде не показується.
 */
public final class AnimalSignals {
    private AnimalSignals() {}

    public static final int DOG = 0, BEAR = 1, EAGLE = 2, HORSE = 3;
    public static final int COUNT = 4;

    private static void add(ServerPlayerEntity p, int idx, float v) {
        StandManager.get(p.getServer()).addAnimalSignal(p.getUuid(), idx, v);
    }

    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (world.isClient || !(player instanceof ServerPlayerEntity sp)) return ActionResult.PASS;
            ItemStack stack = player.getStackInHand(hand);
            if (stack.isEmpty()) return ActionResult.PASS;
            EntityType<?> t = entity.getType();

            // приручення: вовк із кісткою, папуга з насінням
            if (entity instanceof TameableEntity tame && !tame.isTamed()) {
                if (t == EntityType.WOLF && stack.isOf(Items.BONE)) add(sp, DOG, 1f);
                else if (t == EntityType.PARROT && isSeed(stack)) add(sp, EAGLE, 1f);
                return ActionResult.PASS;
            }
            // розведення: годуємо тварину, яка ще не в режимі кохання й не мала. Рахується лише справжнє годування для розведення
            if (entity instanceof AnimalEntity a && a.isBreedingItem(stack) && a.getBreedingAge() == 0 && !a.isInLove()) {
                if (t == EntityType.WOLF) add(sp, DOG, 1f);
                else if (t == EntityType.HORSE || t == EntityType.DONKEY || t == EntityType.MULE) add(sp, HORSE, 1f);
                else if (t == EntityType.CHICKEN) add(sp, EAGLE, 0.5f);
                else if (t == EntityType.BEE) add(sp, BEAR, 1f);
            }
            return ActionResult.PASS;
        });
    }

    private static boolean isSeed(ItemStack s) {
        return s.isOf(Items.WHEAT_SEEDS) || s.isOf(Items.BEETROOT_SEEDS) || s.isOf(Items.MELON_SEEDS) || s.isOf(Items.PUMPKIN_SEEDS);
    }
}
