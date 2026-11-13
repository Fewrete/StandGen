package dev.standgen.item;

import dev.standgen.stand.StandGenerator;
import dev.standgen.stand.StandManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;

public class StandArrowItem extends Item {
    public StandArrowItem(Settings settings) { super(settings); }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) return TypedActionResult.success(stack);

        ServerPlayerEntity sp = (ServerPlayerEntity) user;
        if (StandManager.get(sp.getServer()).get(sp.getUuid()) != null) {
            sp.sendMessage(Text.literal("У тебе вже є стенд").formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }
        if (sp.getHealth() <= 6f) {
            sp.sendMessage(Text.literal("Ти надто ослаблений для пробудження").formatted(Formatting.RED), true);
            return TypedActionResult.fail(stack);
        }
        sp.damage(world.getDamageSources().magic(), 4f);
        if (!sp.getAbilities().creativeMode) stack.decrement(1);
        StandGenerator.awaken(sp);
        world.playSound(null, sp.getX(), sp.getY(), sp.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 0.8f);
        return TypedActionResult.success(stack);
    }
}
