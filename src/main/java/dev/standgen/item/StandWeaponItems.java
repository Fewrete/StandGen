package dev.standgen.item;

import dev.standgen.stand.StandWeapon;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.AxeItem;
import net.minecraft.item.BowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.PickaxeItem;
import net.minecraft.item.SwordItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.util.UseAction;
import net.minecraft.world.World;

import java.util.List;

/**
 * Зброя стенда форми Зброя: у ручному режимі видається гравцю в руки як справжній інструмент.
 * Кожен предмет сам видаляється з інвентаря, якщо він уже не належить активному стенду власника.
 */
public final class StandWeaponItems {
    private StandWeaponItems() {}

    private static void validate(ItemStack stack, World world, Entity entity) {
        if (world.isClient || !(entity instanceof ServerPlayerEntity p)) return;
        if (!StandWeapon.validFor(stack, p)) stack.setCount(0);
    }

    private static void tip(List<Text> tooltip) {
        tooltip.add(Text.translatable("item.standgen.stand_weapon.tooltip").formatted(Formatting.GRAY));
    }

    /** Кирка (Шахтар). */
    public static class Pickaxe extends PickaxeItem {
        public Pickaxe(Settings s) { super(StandToolMaterial.INSTANCE, 0, -2.8f, s); }
        @Override public void inventoryTick(ItemStack st, World w, Entity e, int slot, boolean sel) { validate(st, w, e); }
        @Override public void appendTooltip(ItemStack st, World w, List<Text> t, TooltipContext c) { tip(t); }
    }

    /** Меч (Боєць). */
    public static class Sword extends SwordItem {
        public Sword(Settings s) { super(StandToolMaterial.INSTANCE, 0, -2.4f, s); }
        @Override public void inventoryTick(ItemStack st, World w, Entity e, int slot, boolean sel) { validate(st, w, e); }
        @Override public void appendTooltip(ItemStack st, World w, List<Text> t, TooltipContext c) { tip(t); }
    }

    /** Молот (Будівельник): важка сокира. */
    public static class Hammer extends AxeItem {
        public Hammer(Settings s) { super(StandToolMaterial.INSTANCE, 0f, -3.1f, s); }
        @Override public void inventoryTick(ItemStack st, World w, Entity e, int slot, boolean sel) { validate(st, w, e); }
        @Override public void appendTooltip(ItemStack st, World w, List<Text> t, TooltipContext c) { tip(t); }
    }

    /** Спис (Мандрівник): швидкий. */
    public static class Spear extends SwordItem {
        public Spear(Settings s) { super(StandToolMaterial.INSTANCE, 0, -2.2f, s); }
        @Override public void inventoryTick(ItemStack st, World w, Entity e, int slot, boolean sel) { validate(st, w, e); }
        @Override public void appendTooltip(ItemStack st, World w, List<Text> t, TooltipContext c) { tip(t); }
    }

    /** Лук (Стрілець): має Нескінченність, тож стріляє без стріл в інвентарі. */
    public static class Bow extends BowItem {
        public Bow(Settings s) { super(s); }
        @Override public void inventoryTick(ItemStack st, World w, Entity e, int slot, boolean sel) { validate(st, w, e); }
        @Override public void appendTooltip(ItemStack st, World w, List<Text> t, TooltipContext c) { tip(t); }
    }
}
