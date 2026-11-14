package dev.standgen.item;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.ArmorMaterial;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;

import java.util.List;

/** Частина броні-стенда. Видається при виклику стенда форми Броня й зникає, коли стенд відкликано. */
public class StandArmorItem extends ArmorItem {
    public StandArmorItem(ArmorMaterial material, Type type, Settings settings) {
        super(material, type, settings);
    }

    @Override
    public void appendTooltip(ItemStack stack, World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable("item.standgen.stand_armor.tooltip").formatted(Formatting.GRAY));
    }
}
