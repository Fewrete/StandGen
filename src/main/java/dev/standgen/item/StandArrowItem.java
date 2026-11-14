package dev.standgen.item;

import dev.standgen.stand.StandGenerator;
import dev.standgen.stand.StandManager;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.ArrowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * Стріла Стенда.
 *  - ПКМ, утримувати 1 секунду: пробудження власного стенда (невеликий шанс, що ти недостойний і помреш; не в хардкорі).
 *  - Постріл з лука/арбалета: гравець, у якого влучила стріла, пробуджує стенд (з тим самим ризиком (StandGenerator.UNWORTHY_CHANCE)).
 */
public class StandArrowItem extends ArrowItem {
    public static final String TAG = "standgen_arrow";
    private static final int HOLD_TICKS = 20;

    public StandArrowItem(Settings settings) { super(settings); }

    @Override
    public PersistentProjectileEntity createArrow(World world, ItemStack stack, LivingEntity shooter) {
        ArrowEntity arrow = new ArrowEntity(world, shooter);
        arrow.addCommandTag(TAG);
        arrow.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
        return arrow;
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.standgen.stand_arrow.tooltip1").formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("item.standgen.stand_arrow.tooltip2",
                String.format(java.util.Locale.ROOT, "%.1f%%", StandGenerator.UNWORTHY_CHANCE * 100)).formatted(Formatting.DARK_RED));
        tooltip.add(Text.translatable("item.standgen.stand_arrow.tooltip3").formatted(Formatting.GRAY));
    }

    @Override public int getMaxUseTime(ItemStack stack) { return HOLD_TICKS; }
    @Override public UseAction getUseAction(ItemStack stack) { return UseAction.BOW; }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (!world.isClient && user instanceof ServerPlayerEntity sp) {
            if (StandManager.get(sp.getServer()).get(sp.getUuid()) != null) {
                sp.sendMessage(Text.translatable("msg.standgen.already_has").formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
            if (sp.getHealth() <= 6f) {
                sp.sendMessage(Text.translatable("msg.standgen.too_weak").formatted(Formatting.RED), true);
                return TypedActionResult.fail(stack);
            }
        }
        user.setCurrentHand(hand);
        return TypedActionResult.consume(stack);
    }

    @Override
    public void usageTick(World world, LivingEntity user, ItemStack stack, int remainingUseTicks) {
        if (world instanceof ServerWorld sw && remainingUseTicks % 4 == 0) {
            sw.spawnParticles(ParticleTypes.ENCHANT, user.getX(), user.getY() + 1.0, user.getZ(), 8, 0.5, 0.6, 0.5, 0.6);
            if (user instanceof ServerPlayerEntity sp) {
                int left = remainingUseTicks / 4;
                sp.sendMessage(Text.translatable("msg.standgen.charging", ".".repeat(Math.max(1, 6 - left)))
                        .formatted(Formatting.LIGHT_PURPLE), true);
            }
        }
    }

    @Override
    public ItemStack finishUsing(ItemStack stack, World world, LivingEntity user) {
        if (world.isClient || !(user instanceof ServerPlayerEntity sp)) return stack;
        if (StandManager.get(sp.getServer()).get(sp.getUuid()) != null) return stack;
        if (sp.getHealth() <= 6f) return stack;

        if (!sp.getAbilities().creativeMode) stack.decrement(1);
        // невеликий шанс бути недостойним (UNWORTHY_CHANCE) (не в хардкорі): тоді гравець гине і стенда не отримує
        if (StandGenerator.awakenByArrow(sp)) {
            sp.damage(world.getDamageSources().magic(), 4f);
            world.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 0.8f);
        }
        return stack;
    }
}
