package dev.standgen.client;

import dev.standgen.StandGenMod;
import dev.standgen.entity.StandEntity;
import dev.standgen.stand.Animal;
import dev.standgen.stand.Archetype;
import dev.standgen.stand.Skill;
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

    private final ModelPart humanoid, weapon, swarm, animals;

    public StandRenderer(EntityRendererFactory.Context ctx) {
        super(ctx);
        this.humanoid = ctx.getPart(StandModels.HUMANOID);
        this.weapon = ctx.getPart(StandModels.WEAPON);
        this.swarm = ctx.getPart(StandModels.SWARM);
        this.animals = ctx.getPart(StandModels.ANIMALS);
    }

    private static final String[] ELEMENT_NAMES = {"", "fire", "water", "ice", "sand"};
    private static final Identifier[] ELEMENT_TEX = new Identifier[ELEMENT_NAMES.length];
    private static final boolean[] ELEMENT_CHECKED = new boolean[ELEMENT_NAMES.length];

    /** Для стихійного стенда береться textures/entity/stand_<стихія>.png, якщо файл є, інакше спільна stand.png. */
    private static Identifier textureFor(int elementId) {
        if (elementId <= 0 || elementId >= ELEMENT_NAMES.length) return TEXTURE;
        if (!ELEMENT_CHECKED[elementId]) {
            ELEMENT_CHECKED[elementId] = true;
            Identifier id = new Identifier(StandGenMod.MOD_ID, "textures/entity/stand_" + ELEMENT_NAMES[elementId] + ".png");
            if (net.minecraft.client.MinecraftClient.getInstance().getResourceManager().getResource(id).isPresent()) {
                ELEMENT_TEX[elementId] = id;
            }
        }
        return ELEMENT_TEX[elementId] != null ? ELEMENT_TEX[elementId] : TEXTURE;
    }

    @Override
    public Identifier getTexture(StandEntity entity) { return textureFor(entity.getElementId()); }

    private static float[] rgb(int c) {
        return new float[]{(c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f};
    }

    @Override
    public void render(StandEntity e, float yaw, float tickDelta, MatrixStack m, VertexConsumerProvider vcp, int light) {
        if (!StandClientState.canSeeStands()) return;
        float bodyYaw = MathHelper.lerpAngleDegrees(tickDelta, e.prevBodyYaw, e.bodyYaw);
        float headYaw = MathHelper.lerpAngleDegrees(tickDelta, e.prevHeadYaw, e.headYaw) - bodyYaw;
        float pitch = MathHelper.lerp(tickDelta, e.prevPitch, e.getPitch());
        float age = e.age + tickDelta;
        int stage = e.getStandStage();
        int l = stage >= 3 ? FULLBRIGHT : light;
        float[] c1 = rgb(e.getColor1());
        float[] c2 = rgb(e.getColor2());
        VertexConsumer vc = vcp.getBuffer(RenderLayer.getEntityTranslucent(getTexture(e)));

        m.push();
        m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(180.0f - bodyYaw));
        m.scale(-1.0f, -1.0f, 1.0f);

        // перетворення: стара форма скручується й стискається, нова з'являється, розкручуючись
        Skill morph = e.getAnim();
        if (morph == Skill.SHIFT || morph == Skill.SHIFT_IN) {
            float mp = MathHelper.clamp(e.getAnimElapsed(tickDelta) / e.getAnimDur(), 0f, 1f);
            float ms = morph == Skill.SHIFT ? 1f - mp : mp;
            ms = ms * ms * (3f - 2f * ms);
            float sc = 0.04f + 0.96f * ms;
            m.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(mp * 540f));
            m.scale(sc, sc * (1f + 1.3f * (1f - ms)), sc);
        }

        switch (e.getForm()) {
            case WEAPON -> {
                float k = e.getHeightScale() * 0.8f + stage * 0.03f;
                m.scale(k, k, k);
                renderWeapon(e, age, tickDelta, m, vc, l, c1, c2, stage);
            }
            case SWARM -> renderSwarm(e, age, tickDelta, m, vc, l, c1, c2, stage);
            case ANIMAL -> renderAnimal(e, age, tickDelta, headYaw, pitch, m, vc, l, c1, c2, stage);
            default -> {
                float sx = e.getBulk();
                m.scale(sx, e.getHeightScale(), sx);
                m.translate(0.0f, -1.501f, 0.0f);
                renderBiped(humanoid, e, age, tickDelta, headYaw, pitch, m, vc, l, c1, c2, stage);
            }
        }
        m.pop();
    }

    // ---------------- Гуманоїд і Броня (однакова структура частин) ----------------
    private void renderBiped(ModelPart model, StandEntity e, float age, float td, float headYaw, float pitch, MatrixStack m,
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
        Skill anim = e.getAnim();
        if (anim != null) {
            float p = MathHelper.clamp(e.getAnimElapsed(td) / e.getAnimDur(), 0f, 1f);
            pose(anim, p, MathHelper.sin(p * MathHelper.PI), age, head, ra, la, m);
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

    private static float smooth(float x) {
        x = MathHelper.clamp(x, 0f, 1f);
        return x * x * (3f - 2f * x);
    }

    /** Поза гуманоїда під час здібності. p = 0..1 хід анімації, s = 0..1..0 (пік посередині). */
    private static void pose(Skill sk, float p, float s, float age, ModelPart head, ModelPart ra, ModelPart la, MatrixStack m) {
        switch (sk) {
            case GRAB -> {                       // випад правою рукою вперед
                ra.pitch = -1.57f - 0.25f * s; ra.roll = 0f;
                la.pitch = 0.4f * s;
                m.translate(0f, 0f, -0.5f * s);
            }
            case PIERCE, FLAME_LANCE, TIDAL_JET, FROST_BOLT, SAND_BLAST -> {   // обидві руки прицілюються вперед
                ra.pitch = -1.57f; la.pitch = -1.57f; ra.roll = 0f; la.roll = 0f;
                m.translate(0f, 0f, -0.15f * s);
            }
            case QUAKE, EXCAVATE -> {            // замах угору і удар об землю
                float swing = p < 0.45f
                        ? MathHelper.lerp(smooth(p / 0.45f), 0f, -3.0f)
                        : MathHelper.lerp(smooth((p - 0.45f) / 0.25f), -3.0f, 0.6f);
                ra.pitch = swing; la.pitch = swing; ra.roll = 0f; la.roll = 0f;
                if (p > 0.45f) m.translate(0f, 0.12f * s, -0.25f * s);
            }
            case TIMESTOP -> {                   // рука вгору-вперед, невеликий тремор
                float k = smooth(p * 5f);
                ra.pitch = MathHelper.lerp(k, ra.pitch, -2.5f);
                ra.roll = MathHelper.lerp(k, ra.roll, 0.35f);
                ra.yaw = MathHelper.sin(age * 1.5f) * 0.04f * k;
                la.pitch = MathHelper.lerp(k, la.pitch, -0.4f);
                head.pitch -= 0.25f * k;
            }
            case ARROW_RAIN, INFERNO, DELUGE, BLIZZARD, SANDSTORM -> {   // обидві руки вгору
                float k = smooth(p * 4f);
                ra.pitch = MathHelper.lerp(k, ra.pitch, -3.0f); la.pitch = MathHelper.lerp(k, la.pitch, -3.0f);
                ra.roll = 0.35f * k; la.roll = -0.35f * k;
                head.pitch -= 0.3f * k;
            }
            case BASTION, CITADEL -> {           // руки схрещені перед грудьми
                float k = smooth(p * 4f);
                ra.pitch = -1.1f * k; la.pitch = -1.1f * k;
                ra.yaw = -0.7f * k; la.yaw = 0.7f * k;
                ra.roll = 0f; la.roll = 0f;
            }
            case RESURGENCE, SECOND_CHANCE -> {  // руки в сторони
                float k = smooth(p * 4f);
                ra.pitch = 0f; la.pitch = 0f;
                ra.roll = 0.08f + 1.3f * k; la.roll = -0.08f - 1.3f * k;
                head.pitch -= 0.3f * k;
            }
            case DASH -> {                       // руки назад, корпус рвонув уперед
                ra.pitch = 0.9f * s; la.pitch = 0.9f * s;
                m.translate(0f, 0f, -0.6f * s);
            }
            default -> {                         // загальний жест
                ra.pitch = -1.3f * s; la.pitch = -1.3f * s;
            }
        }
    }

    // ---------------- Тварини ----------------
    private static final String[] ANIMAL_GROUPS = {"", "dog", "bear", "eagle", "horse"};

    private void renderAnimal(StandEntity e, float age, float td, float headYaw, float pitch, MatrixStack m,
                              VertexConsumer vc, int l, float[] c1, float[] c2, int stage) {
        int id = e.getAnimalId();
        if (id <= 0 || id >= ANIMAL_GROUPS.length) return;
        Animal an = Animal.byId(id);
        ModelPart g = animals.getChild(ANIMAL_GROUPS[id]);

        float k = an.scale * e.getHeightScale();
        m.scale(k, k, k);
        m.translate(0.0f, -1.501f, 0.0f);

        // швидкість руху з зсуву між тіками (стенд рухається телепортом, тож ходу рахуємо самі)
        float spd = MathHelper.clamp((float) Math.hypot(e.getX() - e.prevX, e.getZ() - e.prevZ) * 4f, 0f, 1f);
        float ph = age * (0.6f + spd * 0.5f);
        float swing = MathHelper.sin(ph) * 0.9f * spd;

        Skill anim = e.getAnim();
        float as = 0f;
        if (anim != null) {
            float ap = MathHelper.clamp(e.getAnimElapsed(td) / e.getAnimDur(), 0f, 1f);
            as = MathHelper.sin(ap * MathHelper.PI);
            float pulse = 1f + 0.1f * as;
            m.scale(pulse, pulse, pulse);
        }
        boolean atk = e.isPunching();

        ModelPart head = g.getChild("head"), tail = g.getChild("tail");
        head.yaw = MathHelper.clamp(headYaw * RAD, -0.7f, 0.7f);
        head.pitch = pitch * RAD * 0.5f - 0.5f * as;

        if (an == Animal.EAGLE) {
            boolean carry = e.isCarrying();
            ModelPart wl = g.getChild("wing_l"), wr = g.getChild("wing_r");
            ModelPart ll = g.getChild("leg_l"), lr = g.getChild("leg_r");
            float flap = carry ? 0.15f + MathHelper.sin(age * 0.1f) * 0.05f
                    : atk ? -0.5f : MathHelper.sin(age * 0.3f) * 0.55f + 0.15f;
            wl.roll = -flap; wr.roll = flap;
            tail.pitch = carry ? 0.1f : MathHelper.sin(age * 0.2f) * 0.1f + (atk ? -0.4f : 0f);
            float legs = carry ? 0.55f : atk ? -0.9f : 0.25f;
            ll.pitch = legs; lr.pitch = legs;
            if (carry) {   // тримає гравця лапами й повторює нахил його польоту
                float tilt = MathHelper.clamp(e.getPitch(), -80f, 80f) * RAD;
                m.translate(0f, 0.75f, 0f);
                m.multiply(RotationAxis.POSITIVE_X.rotation(tilt));
                m.translate(0f, -0.75f, 0f);
            }
        } else {
            ModelPart fl = g.getChild("leg_fl"), fr = g.getChild("leg_fr"), bl = g.getChild("leg_bl"), br = g.getChild("leg_br");
            fl.pitch = swing; br.pitch = swing;
            fr.pitch = -swing; bl.pitch = -swing;
            if (atk) {
                float hit = MathHelper.sin(age * 1.3f);
                switch (an) {
                    case DOG -> head.pitch += 0.5f * hit;                                  // швидкі укуси
                    case BEAR -> { fl.pitch = -1.2f + hit * 0.4f; fr.pitch = -1.2f - hit * 0.4f; head.pitch += 0.2f; }   // дибки, удар лапами
                    default -> { fl.pitch = -0.9f * Math.max(0f, hit); fr.pitch = -0.9f * Math.max(0f, -hit); }         // кінь б'є копитами
                }
            }
            switch (an) {
                case DOG -> { tail.pitch = 0.6f; tail.yaw = MathHelper.sin(age * 0.35f) * 0.5f; }
                case BEAR -> { tail.pitch = 0.2f; tail.yaw = MathHelper.sin(age * 0.1f) * 0.1f; }
                default -> {   // кінь: хвіст гойдається, голова трохи опущена
                    tail.pitch = 0.15f + MathHelper.sin(age * 0.12f) * 0.1f;
                    head.pitch += 0.35f;
                }
            }
        }

        g.render(m, vc, l, OverlayTexture.DEFAULT_UV, c1[0], c1[1], c1[2], 0.92f);
        if (stage >= 2) {
            m.push();
            m.scale(1.06f, 1.04f, 1.06f);
            m.translate(0.0f, -0.05f, 0.0f);
            g.render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c2[0], c2[1], c2[2], 0.3f);
            m.pop();
        }
    }

    // ---------------- Зброя ----------------
    private void renderWeapon(StandEntity e, float age, float td, MatrixStack m, VertexConsumer vc, int l,
                              float[] c1, float[] c2, int stage) {
        int kind = e.getKind().ordinal();
        for (int i = 0; i < GROUPS.length; i++) weapon.getChild(GROUPS[i]).visible = (i == kind);
        ModelPart g = weapon.getChild(GROUPS[kind]);
        Skill anim = e.getAnim();
        boolean thrown = anim == Skill.THROW;       // зброя відлетіла: на стенді її немає
        float as = 0f;
        if (anim != null && !thrown) {
            float ap = MathHelper.clamp(e.getAnimElapsed(td) / e.getAnimDur(), 0f, 1f);
            as = MathHelper.sin(ap * MathHelper.PI);
            float pulse = 1f + 0.25f * as;
            m.scale(pulse, pulse, pulse);
        }

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
            g.yaw = age * 0.06f + as * 5f;
            g.roll = 0.5f;
            g.pivotY -= 6f * as;
        }

        if (!thrown) g.render(m, vc, l, OverlayTexture.DEFAULT_UV, c1[0], c1[1], c1[2], 0.92f);
        if (stage >= 2 && !thrown) {
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
    private void renderSwarm(StandEntity e, float age, float td, MatrixStack m, VertexConsumer vc, int l,
                             float[] c1, float[] c2, int stage) {
        int count = Math.min(StandModels.SHARDS, 8 + stage * 2);
        boolean atk = e.isPunching();
        m.translate(0.0f, -0.3f, 0.0f); // центр орбіти трохи нижче рівня шиї

        // анімація здібності: Вибух розкидає уламки, Зупинка часу збирає їх у тугу орбіту
        float spread = 1f, spin = 1f, flash = 0f;
        Skill anim = e.getAnim();
        if (anim != null) {
            float ap = MathHelper.clamp(e.getAnimElapsed(td) / e.getAnimDur(), 0f, 1f);
            float as = MathHelper.sin(ap * MathHelper.PI);
            if (anim == Skill.BURST) { spread = 1f + 2.4f * as; flash = as; }
            else if (anim == Skill.TIMESTOP) { spread = 1f - 0.45f * smooth(ap * 5f); spin = 3f; }
            else { spread = 1f + 0.3f * as; spin = 1f + 2f * as; flash = as * 0.5f; }
        }

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
                float a = age * 0.05f * spin + i * (MathHelper.TAU / count);
                float r = (20f + (i % 3) * 4f) * spread;
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
        m.push();
        float cs = 1f + 0.9f * flash;
        m.scale(cs, cs, cs);
        core.render(m, vc, FULLBRIGHT, OverlayTexture.DEFAULT_UV, c2[0], c2[1], c2[2], 0.95f);
        m.pop();
    }
}
