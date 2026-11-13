package dev.standgen.client;

import dev.standgen.StandGenMod;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;

/** Власні моделі стендів, зібрані з кубів у коді (без blockbench). */
public final class StandModels {
    public static final EntityModelLayer ARMORED = new EntityModelLayer(StandGenMod.id("stand_armored"), "main");
    public static final EntityModelLayer WEAPON = new EntityModelLayer(StandGenMod.id("stand_weapon"), "main");
    public static final EntityModelLayer SWARM = new EntityModelLayer(StandGenMod.id("stand_swarm"), "main");
    public static final int SHARDS = 24;

    private StandModels() {}

    private static ModelPartBuilder b() { return ModelPartBuilder.create(); }

    /** Важкі лати: наплічники, нагрудник, шолом з гребенем і рогами, чоботи. */
    public static TexturedModelData armored() {
        ModelData md = new ModelData();
        ModelPartData root = md.getRoot();

        ModelPartData head = root.addChild("head", b().uv(0, 0).cuboid(-4, -8, -4, 8, 8, 8), ModelTransform.pivot(0, 0, 0));
        head.addChild("helmet", b().uv(0, 16).cuboid(-4.6f, -8.6f, -4.6f, 9.2f, 5f, 9.2f), ModelTransform.NONE);
        head.addChild("crest", b().uv(32, 0).cuboid(-0.5f, -12f, -5f, 1f, 4f, 10f), ModelTransform.NONE);
        head.addChild("horn_l", b().uv(0, 0).cuboid(3.5f, -12f, -1f, 1.5f, 5f, 1.5f), ModelTransform.NONE);
        head.addChild("horn_r", b().uv(0, 0).cuboid(-5f, -12f, -1f, 1.5f, 5f, 1.5f), ModelTransform.NONE);

        ModelPartData body = root.addChild("body", b().uv(16, 16).cuboid(-4, 0, -2, 8, 12, 4), ModelTransform.pivot(0, 0, 0));
        body.addChild("chest", b().uv(16, 16).cuboid(-5f, 0.5f, -3.2f, 10f, 7f, 1.5f), ModelTransform.NONE);
        body.addChild("back", b().uv(16, 16).cuboid(-5f, 0.5f, 1.7f, 10f, 7f, 1.5f), ModelTransform.NONE);
        body.addChild("belt", b().uv(16, 16).cuboid(-4.5f, 9f, -2.5f, 9f, 2f, 5f), ModelTransform.NONE);

        ModelPartData ra = root.addChild("right_arm", b().uv(40, 16).cuboid(-3, -2, -2, 4, 12, 4), ModelTransform.pivot(-5, 2, 0));
        ra.addChild("pauldron", b().uv(40, 16).cuboid(-4.5f, -3.5f, -3f, 6f, 3f, 6f), ModelTransform.NONE);
        ra.addChild("gauntlet", b().uv(40, 16).cuboid(-3.5f, 6f, -2.5f, 5f, 4f, 5f), ModelTransform.NONE);
        ModelPartData la = root.addChild("left_arm", b().uv(40, 16).cuboid(-1, -2, -2, 4, 12, 4), ModelTransform.pivot(5, 2, 0));
        la.addChild("pauldron", b().uv(40, 16).cuboid(-1.5f, -3.5f, -3f, 6f, 3f, 6f), ModelTransform.NONE);
        la.addChild("gauntlet", b().uv(40, 16).cuboid(-1.5f, 6f, -2.5f, 5f, 4f, 5f), ModelTransform.NONE);

        ModelPartData rl = root.addChild("right_leg", b().uv(0, 16).cuboid(-2, 0, -2, 4, 12, 4), ModelTransform.pivot(-1.9f, 12, 0));
        rl.addChild("boot", b().uv(0, 16).cuboid(-2.5f, 8f, -2.5f, 5f, 4f, 5.5f), ModelTransform.NONE);
        ModelPartData ll = root.addChild("left_leg", b().uv(0, 16).cuboid(-2, 0, -2, 4, 12, 4), ModelTransform.pivot(1.9f, 12, 0));
        ll.addChild("boot", b().uv(0, 16).cuboid(-2.5f, 8f, -2.5f, 5f, 4f, 5.5f), ModelTransform.NONE);

        return TexturedModelData.of(md, 64, 64);
    }

    /** Шість видів зброї. Імена груп = порядок Archetype: MINER, FIGHTER, BUILDER, RUNNER, ARCHER, SURVIVOR. */
    public static TexturedModelData weapon() {
        ModelData md = new ModelData();
        ModelPartData root = md.getRoot();

        ModelPartData pick = root.addChild("pick", b(), ModelTransform.NONE);
        pick.addChild("shaft", b().uv(0, 0).cuboid(-1, -26, -1, 2, 34, 2), ModelTransform.NONE);
        pick.addChild("head", b().uv(8, 0).cuboid(-12, -28, -1.5f, 24, 3, 3), ModelTransform.NONE);
        pick.addChild("tip_l", b().uv(8, 8).cuboid(-13.5f, -28, -1, 2, 8, 2), ModelTransform.NONE);
        pick.addChild("tip_r", b().uv(8, 8).cuboid(11.5f, -28, -1, 2, 8, 2), ModelTransform.NONE);

        ModelPartData sword = root.addChild("sword", b(), ModelTransform.NONE);
        sword.addChild("blade", b().uv(0, 0).cuboid(-2, -30, -0.5f, 4, 26, 1), ModelTransform.NONE);
        sword.addChild("tip", b().uv(0, 0).cuboid(-1, -33, -0.5f, 2, 3, 1), ModelTransform.NONE);
        sword.addChild("guard", b().uv(8, 0).cuboid(-7, -4, -1.5f, 14, 2.5f, 3), ModelTransform.NONE);
        sword.addChild("grip", b().uv(0, 8).cuboid(-1, -2, -1, 2, 8, 2), ModelTransform.NONE);
        sword.addChild("pommel", b().uv(0, 8).cuboid(-1.5f, 6, -1.5f, 3, 2.5f, 3), ModelTransform.NONE);

        ModelPartData hammer = root.addChild("hammer", b(), ModelTransform.NONE);
        hammer.addChild("shaft", b().uv(0, 0).cuboid(-1, -22, -1, 2, 32, 2), ModelTransform.NONE);
        hammer.addChild("head", b().uv(0, 16).cuboid(-9, -32, -6, 18, 11, 12), ModelTransform.NONE);
        hammer.addChild("cap", b().uv(0, 16).cuboid(-1.5f, 10, -1.5f, 3, 3, 3), ModelTransform.NONE);

        ModelPartData spear = root.addChild("spear", b(), ModelTransform.NONE);
        spear.addChild("shaft", b().uv(0, 0).cuboid(-0.75f, -34, -0.75f, 1.5f, 48, 1.5f), ModelTransform.NONE);
        spear.addChild("ring", b().uv(0, 8).cuboid(-1.5f, -30, -1.5f, 3, 2, 3), ModelTransform.NONE);
        spear.addChild("tip", b().uv(8, 8).cuboid(-2.5f, -2.5f, -0.5f, 5, 5, 1),
                ModelTransform.of(0, -38, 0, 0, 0, (float) Math.PI / 4f));

        ModelPartData bow = root.addChild("bow", b(), ModelTransform.NONE);
        bow.addChild("grip", b().uv(0, 0).cuboid(-1, -4, -1, 2, 8, 2), ModelTransform.NONE);
        bow.addChild("upper", b().uv(0, 8).cuboid(-1, -16, -1, 2, 16, 2), ModelTransform.of(0, -4, 0, 0, 0, 0.35f));
        bow.addChild("lower", b().uv(0, 8).cuboid(-1, 0, -1, 2, 16, 2), ModelTransform.of(0, 4, 0, 0, 0, -0.35f));
        bow.addChild("string", b().uv(8, 0).cuboid(5.3f, -19, -0.25f, 0.5f, 38, 0.5f), ModelTransform.NONE);

        ModelPartData shield = root.addChild("shield", b(), ModelTransform.NONE);
        shield.addChild("face", b().uv(0, 0).cuboid(-9, -12, -1, 18, 24, 2), ModelTransform.NONE);
        shield.addChild("boss", b().uv(0, 26).cuboid(-3, -4, -2.5f, 6, 8, 2), ModelTransform.NONE);
        shield.addChild("rim_t", b().uv(0, 26).cuboid(-10, -13, -1.5f, 20, 2, 3), ModelTransform.NONE);
        shield.addChild("rim_b", b().uv(0, 26).cuboid(-10, 11, -1.5f, 20, 2, 3), ModelTransform.NONE);

        return TexturedModelData.of(md, 64, 64);
    }

    /** Хмара уламків, які літають по орбіті або поцілено вилітають вперед. */
    public static TexturedModelData swarm() {
        ModelData md = new ModelData();
        ModelPartData root = md.getRoot();
        for (int i = 0; i < SHARDS; i++) {
            root.addChild("shard" + i, b().uv(0, 0).cuboid(-2, -2, -2, 4, 4, 4), ModelTransform.NONE);
        }
        return TexturedModelData.of(md, 64, 64);
    }
}
