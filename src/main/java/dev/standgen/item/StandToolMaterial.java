package dev.standgen.item;

import net.minecraft.item.ToolMaterial;
import net.minecraft.recipe.Ingredient;

/** Формальний матеріал: реальний урон і швидкість задаються на предметі через NBT (див. StandWeapon). */
public class StandToolMaterial implements ToolMaterial {
    public static final StandToolMaterial INSTANCE = new StandToolMaterial();

    @Override public int getDurability() { return 1000; }
    @Override public float getMiningSpeedMultiplier() { return 6.0f; }
    @Override public float getAttackDamage() { return 0f; }
    @Override public int getMiningLevel() { return 2; }
    @Override public int getEnchantability() { return 0; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.EMPTY; }
}
