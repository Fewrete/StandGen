package dev.standgen.stand;

import dev.standgen.StandGenMod;
import dev.standgen.item.StandArmorItem;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.attribute.EntityAttributeModifier;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;

import java.util.UUID;

/**
 * Броня стенда форми Броня. Це справжні предмети у слотах броні: захист, міцність і шкала броні працюють ванільно.
 * Показники лежать в NBT предмета (AttributeModifiers) і залежать від стійкості та стадії стенда.
 * Прокляття прив'язки не дає зняти броню, поки стенд не відкликано; прокляття зникнення прибирає її при смерті.
 */
public final class StandArmor {
    public static final String TAG = "standgen_armor";
    /** Порядок слотів і предметів: шолом, нагрудник, поножі, чоботи. */
    public static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
    private static final double[] SHARE = {0.15, 0.40, 0.30, 0.15};   // ванільне співвідношення 3:8:6:3
    private static final UUID[] UUIDS = {
            UUID.fromString("7e1b9a10-5c11-4d3a-9a01-1f5d0a000001"),
            UUID.fromString("7e1b9a10-5c11-4d3a-9a01-1f5d0a000002"),
            UUID.fromString("7e1b9a10-5c11-4d3a-9a01-1f5d0a000003"),
            UUID.fromString("7e1b9a10-5c11-4d3a-9a01-1f5d0a000004")};

    private StandArmor() {}

    private static Item item(int i) {
        return switch (i) {
            case 0 -> StandGenMod.STAND_HELMET;
            case 1 -> StandGenMod.STAND_CHESTPLATE;
            case 2 -> StandGenMod.STAND_LEGGINGS;
            default -> StandGenMod.STAND_BOOTS;
        };
    }

    public static boolean isStandArmor(ItemStack s) { return s.getItem() instanceof StandArmorItem; }

    public static ItemStack create(StandData d, int i) {
        double armor = Math.min(20.0, 3.0 + d.durability * 0.12 + d.stage * 0.5);
        double toughness = d.durability * 0.03;
        double knockback = Math.min(0.4, d.durability / 250.0);

        ItemStack st = new ItemStack(item(i));
        NbtCompound n = st.getOrCreateNbt();
        n.putBoolean(TAG, true);
        n.putInt("c1", d.color1);
        n.putInt("c2", d.color2);
        n.putInt("stage", d.stage);
        n.putInt("style", d.style);
        n.putInt("kind", d.primary.ordinal());
        n.putBoolean("Unbreakable", true);

        EquipmentSlot slot = SLOTS[i];
        UUID id = UUIDS[i];
        st.addAttributeModifier(EntityAttributes.GENERIC_ARMOR,
                new EntityAttributeModifier(id, "Stand armor", armor * SHARE[i], EntityAttributeModifier.Operation.ADDITION), slot);
        st.addAttributeModifier(EntityAttributes.GENERIC_ARMOR_TOUGHNESS,
                new EntityAttributeModifier(id, "Stand armor toughness", toughness / 4.0, EntityAttributeModifier.Operation.ADDITION), slot);
        st.addAttributeModifier(EntityAttributes.GENERIC_KNOCKBACK_RESISTANCE,
                new EntityAttributeModifier(id, "Stand armor knockback", knockback / 4.0, EntityAttributeModifier.Operation.ADDITION), slot);
        if (slot == EquipmentSlot.CHEST) {   // сила стенда підсилює власні удари гравця
            st.addAttributeModifier(EntityAttributes.GENERIC_ATTACK_DAMAGE,
                    new EntityAttributeModifier(id, "Stand armor power", d.power * 0.012, EntityAttributeModifier.Operation.ADDITION), slot);
        }
        st.addEnchantment(Enchantments.BINDING_CURSE, 1);
        st.addEnchantment(Enchantments.VANISHING_CURSE, 1);
        return st;
    }
}
