package dev.standgen.client;

import dev.standgen.StandGenMod;
import dev.standgen.entity.StandEntity;
import dev.standgen.stand.Archetype;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;

public class StandRenderer extends EntityRenderer<StandEntity> {
    private static final Identifier TEXTURE = new Identifier(StandGenMod.MOD_ID, "textures/entity/stand.png");
    private static final float RAD = 0.017453292f;
    private static final int FULLBRIGHT = 0xF000F0;
    /** Порядок = Archetype: MINER, FIGHTER, BUILDER, RUNNER, ARCHER, SURVIVOR */
    private static final String[] GROUPS = {"pick", "sword", "hammer", "spear", "bow", "shield"};
    private static final String[] SHARD_NAMES = new String[StandModels.SHARDS];
    private static final String[] ORB_NAMES = new String[StandModels.ORBS];
    static {
        for (int i = 0; i < SHARD_NAMES.length; i++) SHARD_NAMES[i] = "shard" + i;
        for (int i = 0; i < ORB_NAMES.length; i++) ORB_NAMES[i] = "orb" + i;
    }

    private final ModelPart humanoid, weapon, swarm;

    public StandRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.humanoid = ctx.getPart(StandModels.HUMANOID);
        this.weapon = ctx.getPart(StandModels.WEAPON);
        this.swarm = ctx.getPart(StandModels.SWARM);
    }

    @Override
    public Identifier getTexture(StandEntity entity) { return TEXTURE; }

    private static float[] rgb(int c) {
        return new float[]{(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    @Override
    public void render(StandEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, e.prevBodyYaw, e.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, e.prevHeadYaw, e.headYaw) - bodyYaw;
        float pitch = MathHelper.lerp(tickDelta, e.prevPitch, e.getPitch());
        float age = e.age + tickDelta;
        int stage = e.getStandStage();
        int l = stage >= 3 ? FULLBRIGHT : light;
        float[] c1 = rgb(e.getColor1());
        float[] c2 = rgb(e.getColor2());
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));

        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
        m.scale(-1.0f, -1.0f, 1.0f);

        switch (e.getForm()) {
            case WEAPON -> {
                float k = e.getHeightScale() * 0.8f + stage * 0.03f;
                m.scale(k, k, k);
                renderWeapon(e, age, m, vc, l, c1, c2, stage);
            }
            case SWARM -> renderSwarm(e, age, m, vc, l, c1, c2, stage);
            default -> {
                float sx = e.getBulk();
                m.scale(sx, e.getHeightScale(), sx);
                m.translate(0.0f, -1.501f, 0.0f);
                renderBiped(humanoid, e, age, headYaw, pitch, m, vc, l, c1, c2, stage);
            }
        }
        m.pop();
    }

    // ---------------- Гуманоїд і Броня (однакова структура частин) ----------------
    private void renderBiped(ModelPart model, StandEntity e, float age, float headYaw, float pitch, MatrixStack m,
                             VertexConsumer vc, int l, float[] c1, float[] c2, int stage) {
        ModelPart head = model.getChild("head"), body = model.getChild("body");
        ModelPart ra = model.getChild("right_arm"), la = model.getChild("left_arm");
        ModelPart rl = model.getChild("right_leg"), ll = model.getChild("left_leg");

        head.yaw = headYaw * RAD;
        head.pitch = pitch * RAD;
        float bob = MathHelper.sin(age * 0.1f) * 0.05f;
        ra.pitch = bob; la.pitch = -bob;
        ra.yaw = 0f; la.yaw = 0f;
        ra.roll = 0.08f; la.roll = -0.08f;
        rl.pitch = -0.1f + bob; ll.pitch = 0.1f - bob;
        if (e.isPunching()) {
            float sw = MathHelper.sin(age * 1.6f);
            ra.pitch = -1.5f + sw * 0.7f;
            la.pitch = -1.5f - sw * 0.7f;
            ra.roll = 0f; la.roll = 0f;
        }

        int style = e.getStyle();
        Archetype k = e.getKind();
        head.getChild("crest").visible = (style & 1) != 0;
        head.getChild("horn_l").visible = (style & 2) != 0;
        head.getChild("horn_r").visible = (style & 2) != 0;
        head.getChild("acc_miner").visible = k == Archetype.MINER;
        head.getChild("acc_survivor").visible = k == Archetype.SURVIVOR;
        ra.getChild("acc_fighter").visible = k == Archetype.FIGHTER;
        la.getChild("acc_fighter").visible = k == Archetype.FIGHTER;
        body.getChild("acc_builder").visible = k == Archetype.BUILDER;
        body.getChild("acc_runner").visible = k == Archetype.RUNNER;
        body.getChild("acc_archer").visible = k == Archetype.ARCHER;

        model.render(m, vc, l, OverlayTexture.DEFAULT_UV, c1[0], c1[1], c1[2], 0.9f);
        if (stage >= 2) { // аура другого кольору
            m.push();
            m.scale(1.08f, 1.04f, 1.08f);
            m.translate(0.0f, -0.05f, 0.0f);
            model.render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c2[0], c2[1], c2[2], 0.3f);
            m.pop();
        }
    }

    // ---------------- Зброя ----------------
    private void renderWeapon(StandEntity e, float age, MatrixStack m, VertexConsumer vc, int l,
                              float[] c1, float[] c2, int stage) {
        int kind = e.getKind().ordinal();
        for (int i = 0; i < GROUPS.length; i++) weapon.getChild(GROUPS[i]).visible = (i == kind);
        ModelPart g = weapon.getChild(GROUPS[kind]);

        g.pivotX = 0f;
        g.pivotY = 3f + MathHelper.sin(age * 0.1f) * 1.5f;
        g.pivotZ = 0f;
        if (e.isPunching()) {            // випад уперед до цілі
            float sw = MathHelper.sin(age * 1.4f);
            g.pitch = 0.9f + sw * 0.6f;
            g.yaw = 0f;
            g.roll = 0f;
            g.pivotZ = -8f - sw * 6f;
        } else {                         // у спокої повільно обертається
            g.pitch = 0f;
            g.yaw = age * 0.06f;
            g.roll = 0.5f;
        }

        g.render(m, vc, l, OverlayTexture.DEFAULT_UV, c1[0], c1[1], c1[2], 0.92f);
        if (stage >= 2) {
            m.push();
            m.scale(1.12f, 1.12f, 1.12f);
            g.render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c2[0], c2[1], c2[2], 0.3f);
            m.pop();
        }

        // руни-орби, що кружляють навколо зброї
        float speed = e.isPunching() ? 0.25f : 0.08f;
        for (int i = 0; i < StandModels.ORBS; i++) {
            ModelPart orb = weapon.getChild(ORB_NAMES[i]);
            float a = age * speed + i * 1.5707964f;
            orb.pivotX = MathHelper.cos(a) * 16f;
            orb.pivotY = g.pivotY - 4f + MathHelper.sin(age * 0.1f + i) * 3f;
            orb.pivotZ = MathHelper.sin(a) * 16f;
            orb.pitch = age * 0.1f + i;
            orb.yaw = age * 0.13f + i;
            orb.render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c2[0], c2[1], c2[2], 0.9f);
        }
    }

    // ---------------- Рій ----------------
    private void renderSwarm(StandEntity e, float age, MatrixStack m, VertexConsumer vc, int l,
                             float[] c1, float[] c2, int stage) {
        int count = Math.min(StandModels.SHARDS, 8 + stage * 2);
        boolean atk = e.isPunching();
        m.translate(0.0f, -0.3f, 0.0f); // центр орбіти трохи нижче рівня шиї

        for (int i = 0; i < StandModels.SHARDS; i++) {
            ModelPart s = swarm.getChild(SHARD_NAMES[i]);
            s.visible = i < count;
            if (i >= count) continue;

            float x, y, z;
            if (atk) {
                float phase = (age * 0.09f + i / (float) count) % 1.0f;
                x = MathHelper.sin(i * 2.4f) * 6f;
                y = MathHelper.cos(i * 1.7f) * 6f - 2f;
                z = -(6f + 22f * phase);
            } else {
                float a = age * 0.05f + i * (MathHelper.TAU / count);
                float r = 20f + (i % 3) * 4f;
                x = MathHelper.cos(a) * r;
                z = MathHelper.sin(a) * r;
                y = -6f + MathHelper.sin(age * 0.1f + i) * 6f + ((i % 4) - 1.5f) * 5f;
            }
            s.pivotX = x; s.pivotY = y; s.pivotZ = z;
            s.pitch = age * 0.1f + i;
            s.yaw = age * 0.07f + i * 0.5f;
            s.roll = i * 0.3f;

            float[] c = (i % 2 == 0) ? c1 : c2;
            s.render(m, vc, l, OverlayTexture.DEFAULT_UV, c[0], c[1], c[2], 0.9f);
        }

        // яскраве ядро в центрі
        ModelPart core = swarm.getChild("core");
        core.pivotX = 0f;
        core.pivotY = -2f + MathHelper.sin(age * 0.1f) * 2f;
        core.pivotZ = 0f;
        core.pitch = age * 0.05f;
        core.yaw = age * 0.08f;
        core.render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c2[0], c2[1], c2[2], 0.95f);
    }
}
