package dev.standgen.stand;

import dev.standgen.StandGenMod;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;

import java.util.UUID;

/**
 * Зброя стенда форми Зброя у ручному режимі. Тип предмета залежить від основного типу душі:
 * Шахтар кирка, Боєць меч, Будівельник молот, Мандрівник спис, Стрілець лук, Вижилий щит.
 * Урон і швидкість атаки лежать в NBT предмета і залежать від сили стенда.
 */
public final class StandWeapon {
    public static final String TAG = "standgen_weapon";
    private static final UUID DMG_ID = UUID.fromString("7e1b9a10-5c11-4d3a-9a01-1f5d0b000001");
    private static final UUID SPD_ID = UUID.fromString("7e1b9a10-5c11-4d3a-9a01-1f5d0b000002");

    private StandWeapon() {}

    public static boolean isStandWeapon(ItemStack s) {
        return !s.isEmpty() && s.hasNbt() && s.getNbt().getBoolean(TAG);
    }

    /** Предмет дійсний, лише поки він належить цьому гравцеві й його стенд тримає зброю в руках. */
    public static boolean validFor(ItemStack s, ServerPlayerEntity p) {
        NbtCompound n = s.getNbt();
        return n != null && n.getBoolean(TAG) && n.containsUuid("owner") && n.getUuid("owner").equals(p.getUuid())
                && StandRuntime.isWeaponHeld(p.getUuid());
    }

    private static Item item(Archetype a) {
        return switch (a) {
            case MINER -> StandGenMod.STAND_PICKAXE;
            case FIGHTER -> StandGenMod.STAND_SWORD;
            case BUILDER -> StandGenMod.STAND_HAMMER;
            case RUNNER -> StandGenMod.STAND_SPEAR;
            case ARCHER -> StandGenMod.STAND_BOW;
            case SURVIVOR -> Items.SHIELD;   // ванільний щит з банером кольору стенда
        };
    }

    private static void melee(ItemStack st, double damage, double attackSpeed) {
        st.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                new EntityAttributeModifier(DMG_ID, "Stand weapon damage", damage, EntityAttributeModifier.Operation.ADDITION),
                EquipmentSlot.MAINHAND);
        st.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_SPEED,
                new EntityAttributeModifier(SPD_ID, "Stand weapon speed", attackSpeed - 4.0, EntityAttributeModifier.Operation.ADDITION),
                EquipmentSlot.MAINHAND);
    }

    private static DyeColor nearestDye(int rgb) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        DyeColor best = DyeColor.WHITE;
        double bd = Double.MAX_VALUE;
        for (DyeColor c : DyeColor.values()) {
            int f = c.getFireworkColor();
            int dr = (f >> 16 & 255) - r, dg = (f >> 8 & 255) - g, db = (f & 255) - b;
            double dd = dr * dr + dg * dg + db * db;
            if (dd < bd) { bd = dd; best = c; }
        }
        return best;
    }

    /** Ванільний щит: база кольору стенда й облямівка другого кольору (найближчі до них кольори банера). */
    private static void shieldBanner(ItemStack st, StandData d) {
        DyeColor base = nearestDye(d.color1), trim = nearestDye(d.color2);
        NbtCompound be = st.getOrCreateSubNbt("BlockEntityTag");
        be.putInt("Base", base.getId());
        if (trim != base) {
            NbtList pats = new NbtList();
            NbtCompound pt = new NbtCompound();
            pt.putString("Pattern", "bo");
            pt.putInt("Color", trim.getId());
            pats.add(pt);
            be.put("Patterns", pats);
        }
        st.setCustomName(Text.translatable("item.standgen.stand_shield").setStyle(Style.EMPTY.withItalic(false)));
    }

    public static ItemStack create(StandData d, UUID owner) {
        ItemStack st = new ItemStack(item(d.primary));
        NbtCompound n = st.getOrCreateNbt();
        n.putBoolean(TAG, true);
        n.putUuid("owner", owner);
        n.putInt("c1", d.color1);
        n.putInt("c2", d.color2);
        n.putInt("stage", d.stage);
        n.putBoolean("Unbreakable", true);
        switch (d.primary) {
            case MINER -> melee(st, 0.8 + d.power * 0.015 + d.stage * 0.22, 1.2);
            case FIGHTER -> melee(st, 1.3 + d.power * 0.02 + d.stage * 0.47, 1.6);
            case BUILDER -> melee(st, 2.9 + d.power * 0.03 + d.stage * 0.4, 0.9);
            case RUNNER -> melee(st, 1.2 + d.power * 0.018 + d.stage * 0.27, 1.8);
            case ARCHER -> {
                st.addEnchantment(Enchantments.INFINITY, 1);
                int pw = Math.min(5, d.stage * 5 / 8);
                if (pw > 0) st.addEnchantment(Enchantments.POWER, pw);
            }
            case SURVIVOR -> shieldBanner(st, d);
        }
        st.addEnchantment(Enchantments.VANISHING_CURSE, 1);
        return st;
    }
}
