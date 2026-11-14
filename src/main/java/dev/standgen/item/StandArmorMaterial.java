package dev.standgen.item;

import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.recipe.Ingredient;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

/** Формальний матеріал. Реальні показники броні виставляються на кожен предмет через NBT (див. StandArmor). */
public class StandArmorMaterial implements ArmorMaterial {
    public static final StandArmorMaterial INSTANCE = new StandArmorMaterial();

    @Override public int getDurability(ArmorItem.Type type) { return 500; }

    @Override
    public int getProtection(ArmorItem.Type type) {
        return switch (type) {
            case HELMET, BOOTS -> 2;
            case CHESTPLATE -> 5;
            case LEGGINGS -> 4;
        };
    }

    @Override public int getEnchantability() { return 0; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ITEM_ARMOR_EQUIP_NETHERITE; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
    @Override public String getName() { return "standgen_stand"; }
    @Override public float getToughness() { return 0f; }
    @Override public float getKnockbackResistance() { return 0f; }
}
