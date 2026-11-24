package dev.standgen.client;

import dev.standgen.StandGenMod;
import dev.standgen.entity.StandFxEntity;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.Frustum;
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
import org.joml.Matrix4f;

public class StandFxRenderer extends EntityRenderer<StandFxEntity> {
    private static final Identifier TEXTURE = new Identifier(StandGenMod.MOD_ID, "textures/entity/stand.png");
    private static final int FULLBRIGHT = 0xF000F0;
    /** Порядок = Archetype: MINER, FIGHTER, BUILDER, RUNNER, ARCHER, SURVIVOR */
    private static final String[] GROUPS = {"pick", "sword", "hammer", "spear", "bow", "shield"};

    private final ModelPart weapon;

    public StandFxRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.weapon = ctx.getPart(StandModels.WEAPON);
    }

    @Override
    public Identifier getTexture(StandFxEntity entity) { return TEXTURE; }

    /** Сфера й хвиля великі, а сама сутність крихітна: без цього вони зникали б, коли центр поза кадром. */
    @Override
    public boolean shouldRender(StandFxEntity e, Frustum frustum, double x, double y, double z) {
        if (e.getFx() == StandFxEntity.THROWN) return super.shouldRender(e, frustum, x, y, z);
        return e.squaredDistanceTo(x, y, z) < 96.0 * 96.0;
    }

    private static float[] rgb(int c) {
        return new float[]{(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    @Override
    public void render(StandFxEntity e, float yaw, float td, MatrixStack m, VertexConsumerProvider vcp, int light) {
        switch (e.getFx()) {
            case StandFxEntity.THROWN -> renderThrown(e, td, m, vcp);
            case StandFxEntity.SPHERE -> renderSphere(e, td, m, vcp);
            case StandFxEntity.PULSE -> renderPulse(e, td, m, vcp);
            default -> {}
        }
    }

    // ---------------- зброя, що летить ----------------
    private void renderThrown(StandFxEntity e, float td, MatrixStack m, VertexConsumerProvider vcp) {
        if (!StandClientState.canSeeStands()) return;
        float age = e.getTicks() + td;
        float yawDeg = MathHelper.lerpAngleDegrees(td, e.prevYaw, e.getYaw());
        float pitchDeg = MathHelper.lerp(td, e.prevPitch, e.getPitch());
        float yr = yawDeg * 0.017453292f, pr = pitchDeg * 0.017453292f;
        // напрям польоту (для шлейфу)
        float dx = -MathHelper.sin(yr) * MathHelper.cos(pr);
        float dy = -MathHelper.sin(pr);
        float dz = MathHelper.cos(yr) * MathHelper.cos(pr);

        int kind = MathHelper.clamp(e.getWeaponKind(), 0, GROUPS.length - 1);
        ModelPart g = weapon.getChild(GROUPS[kind]);
        g.pivotX = 0f; g.pivotY = 0f; g.pivotZ = 0f;
        g.pitch = 0f; g.yaw = 0f; g.roll = 0f;
        g.visible = true;
        float[] c1 = rgb(e.getColor1());
        float[] c2 = rgb(e.getColor2());
        float k = e.getSize();
        boolean axial = kind == 3 || kind == 4;   // спис і лук летять рівно, решта крутиться через голову
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(TEXTURE));

        // шлейф-післяобрази позаду
        for (int i = 2; i >= 0; i--) {
            float off = i * 0.7f;
            float alpha = i == 0 ? 0.95f : 0.4f / i;
            m.push();
            m.translate(-dx * off, 0.25f - dy * off, -dz * off);
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yawDeg));
            m.multiply(RotationAxis.POSITIVE_X.rotationDegrees(90f + pitchDeg));
            m.scale(-1f, -1f, 1f);
            m.scale(k, k, k);
            float spin = (age - i * 0.8f) * (axial ? 0.9f : 0.7f);
            m.multiply((axial ? RotationAxis.POSITIVE_Y : RotationAxis.POSITIVE_X).rotation(spin));
            float[] c = i == 0 ? c1 : c2;
            g.render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c[0], c[1], c[2], alpha);
            if (i == 0) {
                m.scale(1.14f, 1.14f, 1.14f);
                g.render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c2[0], c2[1], c2[2], 0.3f);
            }
            m.pop();
        }
    }

    // ---------------- сфера зупинки часу ----------------
    private void renderSphere(StandFxEntity e, float td, MatrixStack m, VertexConsumerProvider vcp) {
        float t = e.getTicks() + td;
        int life = e.getLife();
        float grow = MathHelper.clamp(t / 8f, 0f, 1f);
        grow = 1f - (1f - grow) * (1f - grow) * (1f - grow);             // швидке розширення
        float shrink = MathHelper.clamp((life - t) / 10f, 0f, 1f);       // стискання в кінці
        float rad = e.getSize() * Math.min(grow, shrink * shrink);
        if (rad < 0.05f) return;
        float[] c1 = rgb(e.getColor1());
        float[] c2 = rgb(e.getColor2());
        float pulse = 0.5f + 0.5f * MathHelper.sin(t * 0.35f);
        float flash = t < 4f ? (4f - t) / 4f : 0f;                       // білий спалах на початку

        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
        m.push();
        Matrix4f mat = m.peek().getPositionMatrix();
        float[] white = {1f, 1f, 1f};
        sphere(mat, vc, rad, 18, 28, c2, 0.15f + 0.06f * pulse);
        sphere(mat, vc, rad * 0.97f, 14, 22, c1, 0.09f);
        if (flash > 0f) sphere(mat, vc, rad * 0.9f, 10, 16, white, 0.35f * flash);
        // годинник на землі: кільце, 12 позначок і стрілки, що клацають
        float rc = Math.min(rad * 0.5f, 6f);
        if (rc > 0.8f) clock(mat, vc, rc, t, c2, white);
        m.pop();
    }

    private static void v(Matrix4f m, VertexConsumer vc, float x, float y, float z, float[] c, float a) {
        vc.vertex(m, x, y, z).color(c[0], c[1], c[2], a).next();
    }

    /** Чотирикутник, що малюється з обох боків (шар без ввімкненого відсікання). */
    private static void quad(Matrix4f m, VertexConsumer vc, float[] c, float a,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz) {
        v(m, vc, ax, ay, az, c, a); v(m, vc, bx, by, bz, c, a); v(m, vc, cx, cy, cz, c, a); v(m, vc, dx, dy, dz, c, a);
        v(m, vc, dx, dy, dz, c, a); v(m, vc, cx, cy, cz, c, a); v(m, vc, bx, by, bz, c, a); v(m, vc, ax, ay, az, c, a);
    }

    private static void sphere(Matrix4f m, VertexConsumer vc, float r, int rings, int segs, float[] c, float a) {
        for (int i = 0; i < rings; i++) {
            float p0 = -MathHelper.HALF_PI + MathHelper.PI * i / rings;
            float p1 = -MathHelper.HALF_PI + MathHelper.PI * (i + 1) / rings;
            float y0 = r * MathHelper.sin(p0), r0 = r * MathHelper.cos(p0);
            float y1 = r * MathHelper.sin(p1), r1 = r * MathHelper.cos(p1);
            for (int j = 0; j < segs; j++) {
                float t0 = MathHelper.TAU * j / segs, t1 = MathHelper.TAU * (j + 1) / segs;
                float c0 = MathHelper.cos(t0), s0 = MathHelper.sin(t0), c1 = MathHelper.cos(t1), s1 = MathHelper.sin(t1);
                quad(m, vc, c, a,
                        r0 * c0, y0, r0 * s0, r0 * c1, y0, r0 * s1,
                        r1 * c1, y1, r1 * s1, r1 * c0, y1, r1 * s0);
            }
        }
    }

    /** Плоске кільце на висоті y між радіусами r0 і r1. */
    private static void annulus(Matrix4f m, VertexConsumer vc, float y, float r0, float r1, int segs, float[] c, float a) {
        for (int j = 0; j < segs; j++) {
            float t0 = MathHelper.TAU * j / segs, t1 = MathHelper.TAU * (j + 1) / segs;
            quad(m, vc, c, a,
                    r0 * MathHelper.cos(t0), y, r0 * MathHelper.sin(t0), r1 * MathHelper.cos(t0), y, r1 * MathHelper.sin(t0),
                    r1 * MathHelper.cos(t1), y, r1 * MathHelper.sin(t1), r0 * MathHelper.cos(t1), y, r0 * MathHelper.sin(t1));
        }
    }

    /** Вертикальна стіна-циліндр від y0 до y1. */
    private static void wall(Matrix4f m, VertexConsumer vc, float r, float y0, float y1, int segs, float[] c, float a) {
        for (int j = 0; j < segs; j++) {
            float t0 = MathHelper.TAU * j / segs, t1 = MathHelper.TAU * (j + 1) / segs;
            float x0 = r * MathHelper.cos(t0), z0 = r * MathHelper.sin(t0), x1 = r * MathHelper.cos(t1), z1 = r * MathHelper.sin(t1);
            quad(m, vc, c, a, x0, y0, z0, x1, y0, z1, x1, y1, z1, x0, y1, z0);
        }
    }

    /** Промінь від центра під кутом ang (радіани) довжиною len і шириною w на висоті y. */
    private static void spoke(Matrix4f m, VertexConsumer vc, float y, float ang, float from, float len, float w, float[] c, float a) {
        float ca = MathHelper.cos(ang), sa = MathHelper.sin(ang);
        float px = -sa * w, pz = ca * w;
        float x0 = ca * from, z0 = sa * from, x1 = ca * len, z1 = sa * len;
        quad(m, vc, c, a, x0 - px, y, z0 - pz, x0 + px, y, z0 + pz, x1 + px, y, z1 + pz, x1 - px, y, z1 - pz);
    }

    private static void clock(Matrix4f m, VertexConsumer vc, float rc, float t, float[] c, float[] hand) {
        float y = 0.08f;
        annulus(m, vc, y, rc - 0.12f, rc, 48, c, 0.7f);
        for (int i = 0; i < 12; i++) {
            float ang = i * MathHelper.TAU / 12f;
            boolean major = i % 3 == 0;
            spoke(m, vc, y, ang, rc - (major ? 0.7f : 0.4f), rc - 0.15f, major ? 0.07f : 0.04f, c, 0.8f);
        }
        float minute = (float) Math.floor(t / 4f) * (MathHelper.TAU / 60f);   // клацає кожні 4 тіки
        float hour = t * 0.004f;
        spoke(m, vc, y + 0.01f, -MathHelper.HALF_PI + hour, 0f, rc * 0.5f, 0.1f, hand, 0.9f);
        spoke(m, vc, y + 0.01f, -MathHelper.HALF_PI + minute, 0f, rc * 0.85f, 0.05f, hand, 0.9f);
    }

    // ---------------- ударна хвиля ----------------
    private void renderPulse(StandFxEntity e, float td, MatrixStack m, VertexConsumerProvider vcp) {
        float t = e.getTicks() + td;
        float p = MathHelper.clamp(t / Math.max(1, e.getLife()), 0f, 1f);
        float eased = 1f - (1f - p) * (1f - p);
        float rr = e.getSize() * eased;
        if (rr < 0.1f) return;
        float a = 1f - p;
        float[] c1 = rgb(e.getColor1());
        float[] c2 = rgb(e.getColor2());
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getLightning());
        m.push();
        Matrix4f mat = m.peek().getPositionMatrix();
        wall(mat, vc, rr, 0f, 0.3f + 0.8f * a, 40, c1, 0.5f * a);
        wall(mat, vc, rr * 0.75f, 0f, 0.2f + 0.5f * a, 36, c2, 0.4f * a);
        annulus(mat, vc, 0.06f, Math.max(0f, rr - 0.8f), rr, 40, c2, 0.6f * a);
        m.pop();
    }
}
