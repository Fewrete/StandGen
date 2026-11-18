package dev.standgen.client;

import dev.standgen.StandGenMod;
import net.minecraft.client.model.Dilation;
import net.minecraft.client.model.ModelData;
import net.minecraft.client.model.ModelPartBuilder;
import net.minecraft.client.model.ModelPartData;
import net.minecraft.client.model.ModelTransform;
import net.minecraft.client.model.TexturedModelData;
import net.minecraft.client.render.entity.model.EntityModelLayer;

/** Власні моделі стендів, зібрані з кубів у коді (без blockbench). */
public final class StandModels {
    public static final EntityModelLayer HUMANOID = new EntityModelLayer(StandGenMod.id("stand_humanoid"), "main");
    public static final EntityModelLayer WORN = new EntityModelLayer(StandGenMod.id("stand_worn"), "main");
    public static final EntityModelLayer WEAPON = new EntityModelLayer(StandGenMod.id("stand_weapon"), "main");
    public static final EntityModelLayer SWARM = new EntityModelLayer(StandGenMod.id("stand_swarm"), "main");
    public static final EntityModelLayer SUPPORT = new EntityModelLayer(StandGenMod.id("stand_support"), "main");
    public static final int SHARDS = 24;
    public static final int ORBS = 4;

    private static final ModelTransform N = ModelTransform.NONE;

    private StandModels() {}

    private static ModelPartBuilder b() { return ModelPartBuilder.create(); }

    /** Прикраси, що вмикаються за основним типом душі (видимість перемикає рендер). Спільні для двоногих форм. */
    private static void accessories(ModelPartData head, ModelPartData body, ModelPartData ra, ModelPartData la) {
        // Шахтар: лампа на лобі
        ModelPartData miner = head.addChild("acc_miner", b(), N);
        miner.addChild("band", b().uv(32, 0).cuboid(-4.4f, -8.6f, -4.4f, 8.8f, 1.2f, 8.8f), N);
        miner.addChild("lamp", b().uv(32, 0).cuboid(-1.5f, -9f, -5.3f, 3f, 2.5f, 1.4f), N);

        // Вижилий: німб над головою
        ModelPartData surv = head.addChild("acc_survivor", b(), N);
        surv.addChild("halo_f", b().uv(32, 0).cuboid(-5f, -13f, -5f, 10f, 0.8f, 1.2f), N);
        surv.addChild("halo_b", b().uv(32, 0).cuboid(-5f, -13f, 3.8f, 10f, 0.8f, 1.2f), N);
        surv.addChild("halo_l", b().uv(32, 0).cuboid(-5f, -13f, -3.8f, 1.2f, 0.8f, 7.6f), N);
        surv.addChild("halo_r", b().uv(32, 0).cuboid(3.8f, -13f, -3.8f, 1.2f, 0.8f, 7.6f), N);

        // Боєць: шипи на кулаках
        ModelPartData fr = ra.addChild("acc_fighter", b(), N);
        fr.addChild("knuckle", b().uv(40, 16).cuboid(-3.6f, 9f, -2.6f, 5.2f, 1.6f, 5.2f), N);
        fr.addChild("s1", b().uv(40, 16).cuboid(-3f, 9.4f, -3.8f, 1f, 1f, 1.4f), N);
        fr.addChild("s2", b().uv(40, 16).cuboid(-1.5f, 9.4f, -3.8f, 1f, 1f, 1.4f), N);
        fr.addChild("s3", b().uv(40, 16).cuboid(0f, 9.4f, -3.8f, 1f, 1f, 1.4f), N);
        ModelPartData fl = la.addChild("acc_fighter", b(), N);
        fl.addChild("knuckle", b().uv(40, 16).cuboid(-1.6f, 9f, -2.6f, 5.2f, 1.6f, 5.2f), N);
        fl.addChild("s1", b().uv(40, 16).cuboid(-1f, 9.4f, -3.8f, 1f, 1f, 1.4f), N);
        fl.addChild("s2", b().uv(40, 16).cuboid(0.5f, 9.4f, -3.8f, 1f, 1f, 1.4f), N);
        fl.addChild("s3", b().uv(40, 16).cuboid(2f, 9.4f, -3.8f, 1f, 1f, 1.4f), N);

        // Будівельник: сумки на поясі
        ModelPartData bld = body.addChild("acc_builder", b(), N);
        bld.addChild("strap", b().uv(16, 16).cuboid(-4.5f, 8.6f, -2.5f, 9f, 0.9f, 5f), N);
        bld.addChild("pouch_l", b().uv(16, 16).cuboid(-4.8f, 9f, -3.4f, 3f, 3.2f, 1.7f), N);
        bld.addChild("pouch_r", b().uv(16, 16).cuboid(1.8f, 9f, -3.4f, 3f, 3.2f, 1.7f), N);

        // Мандрівник: крильця-стабілізатори на спині
        ModelPartData run = body.addChild("acc_runner", b(), N);
        run.addChild("fin_l", b().uv(32, 0).cuboid(-0.5f, -1f, 0f, 1f, 9f, 6f), ModelTransform.of(-3f, 2f, 2f, 0.4f, 0f, -0.3f));
        run.addChild("fin_r", b().uv(32, 0).cuboid(-0.5f, -1f, 0f, 1f, 9f, 6f), ModelTransform.of(3f, 2f, 2f, 0.4f, 0f, 0.3f));

        // Стрілець: сагайдак зі стрілами
        ModelPartData arc = body.addChild("acc_archer", b(), N);
        arc.addChild("quiver", b().uv(16, 16).cuboid(-2.4f, -1.5f, 2.2f, 3f, 10f, 2.6f), N);
        arc.addChild("a1", b().uv(32, 0).cuboid(-2.0f, -4f, 3.0f, 0.8f, 2.6f, 0.8f), N);
        arc.addChild("a2", b().uv(32, 0).cuboid(-1.0f, -3.5f, 3.0f, 0.8f, 2.2f, 0.8f), N);
        arc.addChild("a3", b().uv(32, 0).cuboid(-0.1f, -4.2f, 3.0f, 0.8f, 2.8f, 0.8f), N);
    }

    /** Гуманоїд: стрункіша фігура з візором, емблемою, наплічниками й смугами. */
    public static TexturedModelData humanoid() {
        ModelData md = new ModelData();
        ModelPartData root = md.getRoot();

        ModelPartData head = root.addChild("head", b().uv(0, 0).cuboid(-4, -8, -4, 8, 8, 8), ModelTransform.pivot(0, 0, 0));
        head.addChild("visor", b().uv(0, 48).cuboid(-4.4f, -6f, -4.5f, 8.8f, 2.2f, 0.6f), N);
        head.addChild("crest", b().uv(32, 0).cuboid(-0.5f, -11f, -4f, 1f, 3f, 8f), N);
        head.addChild("horn_l", b().uv(32, 0).cuboid(3.5f, -11f, -1f, 1.4f, 4f, 1.4f), N);
        head.addChild("horn_r", b().uv(32, 0).cuboid(-4.9f, -11f, -1f, 1.4f, 4f, 1.4f), N);

        ModelPartData body = root.addChild("body", b().uv(16, 16).cuboid(-4, 0, -2, 8, 12, 4), ModelTransform.pivot(0, 0, 0));
        body.addChild("emblem", b().uv(0, 52).cuboid(-1.5f, 2f, -2.5f, 3f, 3f, 0.6f), N);
        body.addChild("belt", b().uv(16, 16).cuboid(-4.4f, 9.5f, -2.4f, 8.8f, 1.5f, 4.8f), N);
        body.addChild("scarf", b().uv(16, 16).cuboid(-4.6f, -0.6f, -2.6f, 9.2f, 1.6f, 5.2f), N);

        ModelPartData ra = root.addChild("right_arm", b().uv(40, 16).cuboid(-3, -2, -2, 4, 12, 4), ModelTransform.pivot(-5, 2, 0));
        ra.addChild("pad", b().uv(40, 16).cuboid(-3.6f, -3f, -2.6f, 5.2f, 2f, 5.2f), N);
        ra.addChild("band", b().uv(40, 16).cuboid(-3.4f, 6.3f, -2.4f, 4.8f, 1.5f, 4.8f), N);
        ModelPartData la = root.addChild("left_arm", b().uv(40, 16).cuboid(-1, -2, -2, 4, 12, 4), ModelTransform.pivot(5, 2, 0));
        la.addChild("pad", b().uv(40, 16).cuboid(-1.6f, -3f, -2.6f, 5.2f, 2f, 5.2f), N);
        la.addChild("band", b().uv(40, 16).cuboid(-1.4f, 6.3f, -2.4f, 4.8f, 1.5f, 4.8f), N);

        ModelPartData rl = root.addChild("right_leg", b().uv(0, 16).cuboid(-2, 0, -2, 4, 12, 4), ModelTransform.pivot(-1.9f, 12, 0));
        rl.addChild("knee", b().uv(0, 16).cuboid(-2.4f, 4.5f, -2.5f, 4.8f, 1.5f, 5f), N);
        ModelPartData ll = root.addChild("left_leg", b().uv(0, 16).cuboid(-2, 0, -2, 4, 12, 4), ModelTransform.pivot(1.9f, 12, 0));
        ll.addChild("knee", b().uv(0, 16).cuboid(-2.4f, 4.5f, -2.5f, 4.8f, 1.5f, 5f), N);

        accessories(head, body, ra, la);
        return TexturedModelData.of(md, 64, 64);
    }

    /**
     * Броня, яку гравець носить на собі (форма Броня). Частини повторюють скелет гравця.
     * Розміри плит як у ванільної броні (1.0 для шолома, нагрудника й чобіт, 0.5 для поножів), щоб вони лежали
     * ЗВЕРХУ шарів скіна, зокрема й 3D-шарів модів на кшталт 3D Skin Layers, і не мерехтіли разом з ними.
     * Основні плити фарбуються першим кольором, оздоблення другим. Видимість частин вмикає StandArmorRenderer.
     */
    public static TexturedModelData worn() {
        ModelData md = new ModelData();
        ModelPartData root = md.getRoot();
        Dilation outer = new Dilation(1.0f), inner = new Dilation(0.5f);

        ModelPartData head = root.addChild("head", b(), ModelTransform.pivot(0, 0, 0));
        head.addChild("helmet", b().uv(0, 16).cuboid(-4, -8, -4, 8, 8, 8, outer), N);
        head.addChild("visor", b().uv(0, 48).cuboid(-4.6f, -5.6f, -5.6f, 9.2f, 2.2f, 0.7f), N);
        head.addChild("crest", b().uv(32, 0).cuboid(-0.5f, -12.5f, -5f, 1f, 4f, 10f), N);
        head.addChild("horn_l", b().uv(0, 0).cuboid(4.6f, -12.5f, -1f, 1.5f, 5f, 1.5f), N);
        head.addChild("horn_r", b().uv(0, 0).cuboid(-6.1f, -12.5f, -1f, 1.5f, 5f, 1.5f), N);

        ModelPartData body = root.addChild("body", b(), ModelTransform.pivot(0, 0, 0));
        body.addChild("chest", b().uv(16, 16).cuboid(-4, 0, -2, 8, 12, 4, outer), N);
        body.addChild("plate", b().uv(16, 16).cuboid(-5.8f, 0.5f, -4.0f, 11.6f, 7f, 1.4f), N);
        body.addChild("back", b().uv(16, 16).cuboid(-5.8f, 0.5f, 2.6f, 11.6f, 7f, 1.4f), N);
        body.addChild("emblem", b().uv(0, 52).cuboid(-1.5f, 2.5f, -4.6f, 3f, 3f, 0.6f), N);
        body.addChild("belt", b().uv(16, 16).cuboid(-5.4f, 9f, -3.4f, 10.8f, 2.2f, 6.8f), N);

        ModelPartData ra = root.addChild("right_arm", b(), ModelTransform.pivot(-5, 2, 0));
        ra.addChild("sleeve", b().uv(40, 16).cuboid(-3, -2, -2, 4, 12, 4, outer), N);
        ra.addChild("pauldron", b().uv(40, 16).cuboid(-5f, -4.6f, -3.8f, 7.6f, 3.4f, 7.6f), N);
        ra.addChild("gauntlet", b().uv(40, 16).cuboid(-4.6f, 5.5f, -3.6f, 7.2f, 4.6f, 7.2f), N);
        ModelPartData la = root.addChild("left_arm", b(), ModelTransform.pivot(5, 2, 0));
        la.addChild("sleeve", b().uv(40, 16).cuboid(-1, -2, -2, 4, 12, 4, outer), N);
        la.addChild("pauldron", b().uv(40, 16).cuboid(-2.6f, -4.6f, -3.8f, 7.6f, 3.4f, 7.6f), N);
        la.addChild("gauntlet", b().uv(40, 16).cuboid(-2.6f, 5.5f, -3.6f, 7.2f, 4.6f, 7.2f), N);

        ModelPartData rl = root.addChild("right_leg", b(), ModelTransform.pivot(-1.9f, 12, 0));
        rl.addChild("thigh", b().uv(0, 16).cuboid(-2, 0, -2, 4, 12, 4, inner), N);
        rl.addChild("boot", b().uv(0, 16).cuboid(-2, 8, -2, 4, 4, 4, outer), N);
        ModelPartData ll = root.addChild("left_leg", b(), ModelTransform.pivot(1.9f, 12, 0));
        ll.addChild("thigh", b().uv(0, 16).cuboid(-2, 0, -2, 4, 12, 4, inner), N);
        ll.addChild("boot", b().uv(0, 16).cuboid(-2, 8, -2, 4, 4, 4, outer), N);

        wornAccessories(head, body, ra, la);
        return TexturedModelData.of(md, 64, 64);
    }

    /** Прикраси за типом душі для броні: усі винесені назовні від великих плит. Імена ті самі, що в accessories(). */
    private static void wornAccessories(ModelPartData head, ModelPartData body, ModelPartData ra, ModelPartData la) {
        ModelPartData miner = head.addChild("acc_miner", b(), N);
        miner.addChild("band", b().uv(32, 0).cuboid(-5.4f, -9.4f, -5.4f, 10.8f, 1.2f, 10.8f), N);
        miner.addChild("lamp", b().uv(32, 0).cuboid(-1.5f, -8.8f, -6.2f, 3f, 2.5f, 1.4f), N);

        ModelPartData surv = head.addChild("acc_survivor", b(), N);
        surv.addChild("halo_f", b().uv(32, 0).cuboid(-6f, -14f, -6f, 12f, 0.8f, 1.2f), N);
        surv.addChild("halo_b", b().uv(32, 0).cuboid(-6f, -14f, 4.8f, 12f, 0.8f, 1.2f), N);
        surv.addChild("halo_l", b().uv(32, 0).cuboid(-6f, -14f, -4.8f, 1.2f, 0.8f, 9.6f), N);
        surv.addChild("halo_r", b().uv(32, 0).cuboid(4.8f, -14f, -4.8f, 1.2f, 0.8f, 9.6f), N);

        ModelPartData fr = ra.addChild("acc_fighter", b(), N);
        fr.addChild("s1", b().uv(40, 16).cuboid(-3.8f, 6.5f, -4.8f, 1f, 1f, 1.4f), N);
        fr.addChild("s2", b().uv(40, 16).cuboid(-1.8f, 6.5f, -4.8f, 1f, 1f, 1.4f), N);
        fr.addChild("s3", b().uv(40, 16).cuboid(0.2f, 6.5f, -4.8f, 1f, 1f, 1.4f), N);
        ModelPartData fl = la.addChild("acc_fighter", b(), N);
        fl.addChild("s1", b().uv(40, 16).cuboid(-1.2f, 6.5f, -4.8f, 1f, 1f, 1.4f), N);
        fl.addChild("s2", b().uv(40, 16).cuboid(0.8f, 6.5f, -4.8f, 1f, 1f, 1.4f), N);
        fl.addChild("s3", b().uv(40, 16).cuboid(2.8f, 6.5f, -4.8f, 1f, 1f, 1.4f), N);

        ModelPartData bld = body.addChild("acc_builder", b(), N);
        bld.addChild("pouch_l", b().uv(16, 16).cuboid(-5.4f, 9.2f, -4.4f, 3.4f, 3.4f, 1.8f), N);
        bld.addChild("pouch_r", b().uv(16, 16).cuboid(2f, 9.2f, -4.4f, 3.4f, 3.4f, 1.8f), N);

        ModelPartData run = body.addChild("acc_runner", b(), N);
        run.addChild("fin_l", b().uv(32, 0).cuboid(-0.5f, -1f, 0f, 1f, 9f, 6f), ModelTransform.of(-4f, 2f, 3.2f, 0.4f, 0f, -0.3f));
        run.addChild("fin_r", b().uv(32, 0).cuboid(-0.5f, -1f, 0f, 1f, 9f, 6f), ModelTransform.of(4f, 2f, 3.2f, 0.4f, 0f, 0.3f));

        ModelPartData arc = body.addChild("acc_archer", b(), N);
        arc.addChild("quiver", b().uv(16, 16).cuboid(-2.6f, -1.5f, 3.2f, 3.2f, 10f, 2.8f), N);
        arc.addChild("a1", b().uv(32, 0).cuboid(-2.2f, -4f, 4f, 0.8f, 2.6f, 0.8f), N);
        arc.addChild("a2", b().uv(32, 0).cuboid(-1.0f, -3.5f, 4f, 0.8f, 2.2f, 0.8f), N);
        arc.addChild("a3", b().uv(32, 0).cuboid(0.0f, -4.2f, 4f, 0.8f, 2.8f, 0.8f), N);
    }

    /** Шість видів зброї + 4 руни-орби навколо. Імена груп = порядок Archetype. */
    public static TexturedModelData weapon() {
        ModelData md = new ModelData();
        ModelPartData root = md.getRoot();

        ModelPartData pick = root.addChild("pick", b(), N);
        pick.addChild("shaft", b().uv(0, 0).cuboid(-1, -26, -1, 2, 34, 2), N);
        pick.addChild("head", b().uv(8, 0).cuboid(-12, -28, -1.5f, 24, 3, 3), N);
        pick.addChild("tip_l", b().uv(8, 8).cuboid(-13.5f, -28, -1, 2, 8, 2), N);
        pick.addChild("tip_r", b().uv(8, 8).cuboid(11.5f, -28, -1, 2, 8, 2), N);

        ModelPartData sword = root.addChild("sword", b(), N);
        sword.addChild("blade", b().uv(0, 0).cuboid(-2, -30, -0.5f, 4, 26, 1), N);
        sword.addChild("tip", b().uv(0, 0).cuboid(-1, -33, -0.5f, 2, 3, 1), N);
        sword.addChild("guard", b().uv(8, 0).cuboid(-7, -4, -1.5f, 14, 2.5f, 3), N);
        sword.addChild("grip", b().uv(0, 8).cuboid(-1, -2, -1, 2, 8, 2), N);
        sword.addChild("pommel", b().uv(0, 8).cuboid(-1.5f, 6, -1.5f, 3, 2.5f, 3), N);

        ModelPartData hammer = root.addChild("hammer", b(), N);
        hammer.addChild("shaft", b().uv(0, 0).cuboid(-1, -22, -1, 2, 32, 2), N);
        hammer.addChild("head", b().uv(0, 16).cuboid(-9, -32, -6, 18, 11, 12), N);
        hammer.addChild("cap", b().uv(0, 16).cuboid(-1.5f, 10, -1.5f, 3, 3, 3), N);

        ModelPartData spear = root.addChild("spear", b(), N);
        spear.addChild("shaft", b().uv(0, 0).cuboid(-0.75f, -34, -0.75f, 1.5f, 48, 1.5f), N);
        spear.addChild("ring", b().uv(0, 8).cuboid(-1.5f, -30, -1.5f, 3, 2, 3), N);
        spear.addChild("tip", b().uv(8, 8).cuboid(-2.5f, -2.5f, -0.5f, 5, 5, 1),
                ModelTransform.of(0, -38, 0, 0, 0, (float) Math.PI / 4f));

        ModelPartData bow = root.addChild("bow", b(), N);
        bow.addChild("grip", b().uv(0, 0).cuboid(-1, -4, -1, 2, 8, 2), N);
        bow.addChild("upper", b().uv(0, 8).cuboid(-1, -16, -1, 2, 16, 2), ModelTransform.of(0, -4, 0, 0, 0, 0.35f));
        bow.addChild("lower", b().uv(0, 8).cuboid(-1, 0, -1, 2, 16, 2), ModelTransform.of(0, 4, 0, 0, 0, -0.35f));
        bow.addChild("string", b().uv(8, 0).cuboid(5.3f, -19, -0.25f, 0.5f, 38, 0.5f), N);

        ModelPartData shield = root.addChild("shield", b(), N);
        shield.addChild("face", b().uv(0, 0).cuboid(-9, -12, -1, 18, 24, 2), N);
        shield.addChild("boss", b().uv(0, 26).cuboid(-3, -4, -2.5f, 6, 8, 2), N);
        shield.addChild("rim_t", b().uv(0, 26).cuboid(-10, -13, -1.5f, 20, 2, 3), N);
        shield.addChild("rim_b", b().uv(0, 26).cuboid(-10, 11, -1.5f, 20, 2, 3), N);

        for (int i = 0; i < ORBS; i++) {
            root.addChild("orb" + i, b().uv(0, 0).cuboid(-1.5f, -1.5f, -1.5f, 3, 3, 3), N);
        }
        return TexturedModelData.of(md, 64, 64);
    }

    /** Хмара уламків навколо яскравого ядра. */
    public static TexturedModelData swarm() {
        ModelData md = new ModelData();
        ModelPartData root = md.getRoot();
        for (int i = 0; i < SHARDS; i++) {
            root.addChild("shard" + i, b().uv(0, 0).cuboid(-2, -2, -2, 4, 4, 4), N);
        }
        root.addChild("core", b().uv(0, 0).cuboid(-3, -3, -3, 6, 6, 6), N);
        return TexturedModelData.of(md, 64, 64);
    }

    /** Підтримка: сяюче ядро з крилами, німбом і супутниками. Нічого не б'є. */
    public static TexturedModelData support() {
        ModelData md = new ModelData();
        ModelPartData root = md.getRoot();
        root.addChild("core", b().uv(0, 0).cuboid(-3, -3, -3, 6, 6, 6), N);
        root.addChild("wing_l1", b().uv(0, 8).cuboid(0, -0.8f, -0.5f, 12, 1.6f, 1), N);
        root.addChild("wing_l2", b().uv(0, 8).cuboid(0, -0.7f, -0.5f, 9, 1.4f, 1), N);
        root.addChild("wing_r1", b().uv(0, 8).cuboid(-12, -0.8f, -0.5f, 12, 1.6f, 1), N);
        root.addChild("wing_r2", b().uv(0, 8).cuboid(-9, -0.7f, -0.5f, 9, 1.4f, 1), N);
        root.addChild("halo_f", b().uv(0, 8).cuboid(-6, -0.4f, -6, 12, 0.8f, 1.2f), N);
        root.addChild("halo_b", b().uv(0, 8).cuboid(-6, -0.4f, 4.8f, 12, 0.8f, 1.2f), N);
        root.addChild("halo_l", b().uv(0, 8).cuboid(-6, -0.4f, -4.8f, 1.2f, 0.8f, 9.6f), N);
        root.addChild("halo_r", b().uv(0, 8).cuboid(4.8f, -0.4f, -4.8f, 1.2f, 0.8f, 9.6f), N);
        for (int i = 0; i < 3; i++) root.addChild("sat" + i, b().uv(0, 0).cuboid(-1.5f, -1.5f, -1.5f, 3, 3, 3), N);
        return TexturedModelData.of(md, 64, 64);
    }
}
