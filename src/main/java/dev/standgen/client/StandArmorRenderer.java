package dev.standgen.client;

import dev.standgen.StandGenMod;
import dev.standgen.stand.Archetype;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Малює броню-стенд (форма Броня) на моделі будь-якого гравця. Дані (кольори, стадія, прикраси) беруться з NBT предмета,
 * тож усі гравці бачать однаково без додаткових пакетів.
 */
public class StandArmorRenderer implements ArmorRenderer {
    private static final Identifier TEXTURE = new Identifier(StandGenMod.MOD_ID, "textures/entity/stand.png");
    private static final int FULLBRIGHT = 0xF000F0;

    /** Частина моделі, її дочірній елемент, у якому слоті він показується і чи це оздоблення (другий колір). */
    private record Piece(String part, String child, EquipmentSlot slot, boolean trim) {}

    private static final Piece[] PIECES = {
            new Piece("head", "helmet", EquipmentSlot.HEAD, false),
            new Piece("head", "visor", EquipmentSlot.HEAD, true),
            new Piece("head", "crest", EquipmentSlot.HEAD, true),
            new Piece("head", "horn_l", EquipmentSlot.HEAD, true),
            new Piece("head", "horn_r", EquipmentSlot.HEAD, true),
            new Piece("head", "acc_miner", EquipmentSlot.HEAD, true),
            new Piece("head", "acc_survivor", EquipmentSlot.HEAD, true),
            new Piece("body", "chest", EquipmentSlot.CHEST, false),
            new Piece("body", "plate", EquipmentSlot.CHEST, true),
            new Piece("body", "back", EquipmentSlot.CHEST, true),
            new Piece("body", "emblem", EquipmentSlot.CHEST, true),
            new Piece("body", "acc_builder", EquipmentSlot.CHEST, true),
            new Piece("body", "acc_runner", EquipmentSlot.CHEST, true),
            new Piece("body", "acc_archer", EquipmentSlot.CHEST, true),
            new Piece("body", "belt", EquipmentSlot.LEGS, true),
            new Piece("right_arm", "sleeve", EquipmentSlot.CHEST, false),
            new Piece("right_arm", "pauldron", EquipmentSlot.CHEST, true),
            new Piece("right_arm", "gauntlet", EquipmentSlot.CHEST, true),
            new Piece("right_arm", "acc_fighter", EquipmentSlot.CHEST, true),
            new Piece("left_arm", "sleeve", EquipmentSlot.CHEST, false),
            new Piece("left_arm", "pauldron", EquipmentSlot.CHEST, true),
            new Piece("left_arm", "gauntlet", EquipmentSlot.CHEST, true),
            new Piece("left_arm", "acc_fighter", EquipmentSlot.CHEST, true),
            new Piece("right_leg", "thigh", EquipmentSlot.LEGS, false),
            new Piece("right_leg", "boot", EquipmentSlot.FEET, true),
            new Piece("left_leg", "thigh", EquipmentSlot.LEGS, false),
            new Piece("left_leg", "boot", EquipmentSlot.FEET, true),
    };

    private ModelPart root;

    private static boolean allowed(Piece pc, int style, Archetype kind) {
        return switch (pc.child()) {
            case "crest" -> (style & 1) != 0;
            case "horn_l", "horn_r" -> (style & 2) != 0;
            case "acc_miner" -> kind == Archetype.MINER;
            case "acc_survivor" -> kind == Archetype.SURVIVOR;
            case "acc_fighter" -> kind == Archetype.FIGHTER;
            case "acc_builder" -> kind == Archetype.BUILDER;
            case "acc_runner" -> kind == Archetype.RUNNER;
            case "acc_archer" -> kind == Archetype.ARCHER;
            default -> true;
        };
    }

    @Override
    public void render(MatrixStack matrices, VertexConsumerProvider vcp, ItemStack stack, LivingEntity entity,
                       EquipmentSlot slot, int light, BipedEntityModel<LivingEntity> ctx) {
        NbtCompound n = stack.getNbt();
        if (n == null || !n.contains("c1")) return;
        if (root == null) root = MinecraftClient.getInstance().getEntityModelLoader().getModelPart(StandModels.WORN);

        // поза гравця переноситься на частини броні
        root.getChild("head").copyTransform(ctx.head);
        root.getChild("body").copyTransform(ctx.body);
        root.getChild("right_arm").copyTransform(ctx.rightArm);
        root.getChild("left_arm").copyTransform(ctx.leftArm);
        root.getChild("right_leg").copyTransform(ctx.rightLeg);
        root.getChild("left_leg").copyTransform(ctx.leftLeg);

        int stage = n.getInt("stage");
        int style = n.getInt("style");
        Archetype kind = Archetype.values()[MathHelper.clamp(n.getInt("kind"), 0, Archetype.COUNT - 1)];
        int l = stage >= 3 ? FULLBRIGHT : light;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));

        for (int pass = 0; pass < 2; pass++) {
            boolean trim = pass == 1;
            for (Piece pc : PIECES) {
                root.getChild(pc.part()).getChild(pc.child()).visible =
                        pc.slot() == slot && pc.trim() == trim && allowed(pc, style, kind);
            }
            int c = n.getInt(trim ? "c2" : "c1");
            root.render(matrices, vc, l, OverlayTexture.DEFAULT_UV,
                    (c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f, 1f);
        }
    }
}
