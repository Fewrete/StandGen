package dev.standgen.client;

import dev.standgen.StandGenMod;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.FeatureRenderer;
import net.minecraft.client.render.entity.feature.FeatureRendererContext;
import net.minecraft.client.render.entity.model.EntityModelLoader;
import net.minecraft.client.render.entity.model.PlayerEntityModel;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;

/**
 * Стенд Підтримка, що носиться НА СПИНІ гравця: ранець-ядро, крила й німб за головою.
 * Малюється як частина моделі гравця, тож рухається разом із тілом (присідання, нахил, плавання).
 */
public class SupportBackFeature extends FeatureRenderer<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> {
    private static final Identifier TEXTURE = new Identifier(StandGenMod.MOD_ID, "textures/entity/stand.png");
    private static final int FULLBRIGHT = 0xF000F0;
    private static final String[] HALO = {"halo_t", "halo_b", "halo_l", "halo_r"};

    private final ModelPart root;

    public SupportBackFeature(FeatureRendererContext<AbstractClientPlayerEntity, PlayerEntityModel<AbstractClientPlayerEntity>> ctx,
                              EntityModelLoader loader) {
        super(ctx);
        this.root = loader.getModelPart(StandModels.BACK);
    }

    private static float[] rgb(int c) {
        return new float[]{(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    @Override
    public void render(MatrixStack m, VertexConsumerProvider vcp, int light, AbstractClientPlayerEntity player,
                       float limbAngle, float limbDistance, float tickDelta, float animProgress, float headYaw, float headPitch) {
        if (player.isInvisible() || !StandClientState.canSeeStands()) return;
        SupportBackState.Data d = SupportBackState.get(player.getUuid());
        if (d == null) return;

        float age = player.age + tickDelta;
        float[] c1 = rgb(d.c1()), c2 = rgb(d.c2());
        int l = d.stage() >= 3 ? FULLBRIGHT : light;
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityCutoutNoCull(TEXTURE));

        m.push();
        getContextModel().body.rotate(m);   // у систему координат тіла гравця

        root.getChild("pack").render(m, vc, l, OverlayTexture.DEFAULT_UV, c1[0], c1[1], c1[2], 1f);

        float flap = MathHelper.sin(age * 0.12f) * 0.12f;
        String[] wings = {"wing_l1", "wing_l2", "wing_r1", "wing_r2"};
        for (int i = 0; i < wings.length; i++) {
            ModelPart wg = root.getChild(wings[i]);
            boolean left = i < 2;
            float base = (i % 2 == 0) ? 0.55f : 0.2f;
            wg.roll = (left ? -1f : 1f) * (base + flap);
            wg.render(m, vc, l, OverlayTexture.DEFAULT_UV, c1[0], c1[1], c1[2], 1f);
        }

        root.getChild("core").render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c2[0], c2[1], c2[2], 1f);
        for (String name : HALO) {
            ModelPart h = root.getChild(name);
            h.roll = age * 0.05f;          // німб повільно обертається за головою
            h.render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c2[0], c2[1], c2[2], 1f);
        }
        m.pop();
    }
}
