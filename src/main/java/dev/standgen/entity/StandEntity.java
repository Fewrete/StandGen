package dev.standgen.entity;

import dev.standgen.stand.Abilities;
import dev.standgen.stand.Archetype;
import dev.standgen.stand.StandData;
import dev.standgen.stand.StandForm;
import dev.standgen.stand.StandManager;
import dev.standgen.stand.Element;
import dev.standgen.stand.ElementZone;
import dev.standgen.stand.Skill;
import dev.standgen.stand.StandRuntime;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.UUID;

public class StandEntity extends LivingEntity {
    private static final TrackedData<Integer> COLOR1 = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> COLOR2 = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> STAGE = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Boolean> ATTACKING = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.BOOLEAN);
    private static final TrackedData<Float> BULK = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Float> HEIGHT = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.FLOAT);
    private static final TrackedData<Integer> FORM = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> KIND = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> STYLE = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ELEMENT = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    /** Анімація здібності: 0 = немає, інакше Skill.ordinal()+1. */
    private static final TrackedData<Integer> ANIM = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ANIM_LEFT = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> ANIM_DUR = DataTracker.registerData(StandEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private UUID ownerId;
    private LivingEntity target;
    private int targetTimeout;
    private int hitCooldown;
    private int barrageTicks;
    private int animLeft;

    public StandEntity(EntityType<? extends StandEntity> type, World world) {
        super(type, world);
        this.setNoGravity(true);
        this.noClip = true;
    }

    public static DefaultAttributeContainer.Builder createStandAttributes() {
        return LivingEntity.createLivingAttributes();
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        dataTracker.startTracking(COLOR1, 0xFFFFFF);
        dataTracker.startTracking(COLOR2, 0xFFFFFF);
        dataTracker.startTracking(STAGE, 0);
        dataTracker.startTracking(ATTACKING, false);
        dataTracker.startTracking(BULK, 1f);
        dataTracker.startTracking(HEIGHT, 1f);
        dataTracker.startTracking(FORM, 0);
        dataTracker.startTracking(KIND, 0);
        dataTracker.startTracking(STYLE, 0);
        dataTracker.startTracking(ELEMENT, 0);
        dataTracker.startTracking(ANIM, 0);
        dataTracker.startTracking(ANIM_LEFT, 0);
        dataTracker.startTracking(ANIM_DUR, 1);
    }

    // ---- для рендера ----
    public int getColor1() { return dataTracker.get(COLOR1); }
    public int getColor2() { return dataTracker.get(COLOR2); }
    public int getStandStage() { return dataTracker.get(STAGE); }
    public boolean isPunching() { return dataTracker.get(ATTACKING); }
    public float getBulk() { return dataTracker.get(BULK); }
    public float getHeightScale() { return dataTracker.get(HEIGHT); }
    public StandForm getForm() { return StandForm.values()[MathHelper.clamp(dataTracker.get(FORM), 0, StandForm.values().length - 1)]; }
    public Archetype getKind() { return Archetype.values()[MathHelper.clamp(dataTracker.get(KIND), 0, Archetype.COUNT - 1)]; }
    public int getStyle() { return dataTracker.get(STYLE); }

    // ---- анімації здібностей ----
    /** Скільки тіків триває поза для здібності. */
    public static int animTicks(Skill s) {
        return switch (s) {
            case TIMESTOP -> 36;
            case QUAKE -> 22;
            case EXCAVATE -> 16;
            case GRAB -> 14;
            case PIERCE -> 12;
            case BURST -> 16;
            case ARROW_RAIN -> 30;
            case BASTION, CITADEL -> 24;
            case RESURGENCE, SECOND_CHANCE -> 24;
            case DASH -> 10;
            case FLAME_LANCE, TIDAL_JET, FROST_BOLT, SAND_BLAST -> 12;
            case INFERNO, DELUGE, BLIZZARD, SANDSTORM -> 30;
            default -> 14;
        };
    }

    public void playAnim(Skill s) { playAnim(s, animTicks(s)); }

    public void playAnim(Skill s, int ticks) {
        animLeft = ticks;
        dataTracker.set(ANIM, s.ordinal() + 1);
        dataTracker.set(ANIM_DUR, Math.max(1, ticks));
        dataTracker.set(ANIM_LEFT, ticks);
    }

    /** Зупинити анімацію, якщо зараз грає саме ця. */
    public void stopAnim(Skill s) {
        if (dataTracker.get(ANIM) == s.ordinal() + 1) {
            animLeft = 0;
            dataTracker.set(ANIM, 0);
            dataTracker.set(ANIM_LEFT, 0);
        }
    }

    public Skill getAnim() {
        int a = dataTracker.get(ANIM);
        Skill[] v = Skill.values();
        return a <= 0 || a > v.length ? null : v[a - 1];
    }

    public int getAnimDur() { return Math.max(1, dataTracker.get(ANIM_DUR)); }

    /** Скільки тіків минуло від початку анімації (плавно, з tickDelta). */
    public float getAnimElapsed(float tickDelta) {
        float e = dataTracker.get(ANIM_DUR) - (dataTracker.get(ANIM_LEFT) - tickDelta);
        return MathHelper.clamp(e, 0f, (float) getAnimDur());
    }

    public void setOwner(UUID id) { this.ownerId = id; }

    public void applyData(StandData d) {
        dataTracker.set(COLOR1, d.color1);
        dataTracker.set(COLOR2, d.color2);
        dataTracker.set(STAGE, d.stage);
        dataTracker.set(BULK, d.bulk);
        dataTracker.set(HEIGHT, d.height);
        dataTracker.set(FORM, d.form.ordinal());
        dataTracker.set(KIND, d.primary.ordinal());
        dataTracker.set(STYLE, d.style);
        dataTracker.set(ELEMENT, d.element.id);
    }

    public int getElementId() { return dataTracker.get(ELEMENT); }

    public void setTarget(LivingEntity t) {
        this.target = t;
        this.targetTimeout = 100;
    }

    public void startBarrage(int ticks) {
        this.barrageTicks = ticks;
        this.hitCooldown = 0;
    }

    private static ParticleEffect ambient(Archetype a) {
        return switch (a) {
            case MINER -> ParticleTypes.ASH;
            case FIGHTER -> ParticleTypes.CRIT;
            case BUILDER -> ParticleTypes.ENCHANT;
            case RUNNER -> ParticleTypes.CLOUD;
            case ARCHER -> ParticleTypes.END_ROD;
            case SURVIVOR -> ParticleTypes.HAPPY_VILLAGER;
        };
    }

    @Override
    public void tick() {
        super.tick();
        if (getWorld().isClient) return;

        ServerWorld w = (ServerWorld) getWorld();
        PlayerEntity po = ownerId == null ? null : w.getPlayerByUuid(ownerId);
        if (!(po instanceof ServerPlayerEntity owner) || !owner.isAlive() || owner.isSpectator()) {
            discard();
            return;
        }
        StandData d = StandManager.get(w.getServer()).get(ownerId);
        if (d == null) { discard(); return; }

        if (age % 20 == 0) applyData(d);
        if (animLeft > 0) {
            animLeft--;
            dataTracker.set(ANIM_LEFT, animLeft);
            if (animLeft == 0) dataTracker.set(ANIM, 0);
        }
        if (age % 40 == 0 && d.form != StandForm.SUPPORT) Abilities.applyPassive(owner, d);

        int now = w.getServer().getTicks();
        boolean manual = StandRuntime.isManual(ownerId);
        boolean support = d.form == StandForm.SUPPORT;
        boolean holding = manual && !support && StandRuntime.isHolding(ownerId, now);
        boolean swarm = d.form == StandForm.SWARM;
        double fullLeash = d.leash();
        // в авто-режимі стенд тримається ближче до власника (крім Рою)
        double autoLeash = swarm ? fullLeash : Math.min(fullLeash, 4.5 + d.range * 0.03);
        double aggro = swarm ? 4.0 + d.range * 0.06 : 3.0 + d.range * 0.02;
        double reach = 2.5 + d.range * 0.04;

        // ---- цілі ----
        if (holding) {
            target = Abilities.lookedEntity(w, owner, reach);
            targetTimeout = 10;
        } else {
            double maxDist = (manual || barrageTicks > 0) ? fullLeash : autoLeash;
            if (target != null && (!target.isAlive() || target.getWorld() != w
                    || target.squaredDistanceTo(owner) > maxDist * maxDist || --targetTimeout <= 0)) {
                target = null;
            }
            if (target == null) {
                if (barrageTicks > 0) {
                    LivingEntity looked = Abilities.lookedEntity(w, owner, fullLeash);
                    target = looked != null ? looked : findTarget(w, owner, fullLeash);
                    targetTimeout = 40;
                } else if (!manual && age % 5 == 0) {
                    target = findTarget(w, owner, aggro);
                    targetTimeout = 40;
                }
            }
        }

        if (support) { target = null; barrageTicks = 0; }   // стенд підтримки нічого не б'є

        // ---- рух ----
        Vec3d goal;
        double lerp = 0.35;
        float faceYaw = owner.getHeadYaw();
        if (target != null) {
            Vec3d tp = target.getPos();
            Vec3d dir = tp.subtract(owner.getPos()).multiply(1, 0, 1);
            if (dir.lengthSquared() < 1.0E-4) dir = new Vec3d(0, 0, 1);
            dir = dir.normalize();
            goal = tp.subtract(dir.multiply(1.1)).add(0, 0.1, 0);
            faceYaw = (float) Math.toDegrees(Math.atan2(-(tp.x - getX()), tp.z - getZ()));
            lerp = 0.6;
        } else if (holding) {
            Vec3d aim = owner.getEyePos().add(owner.getRotationVec(1f).multiply(Math.min(reach, 2.4)));
            goal = new Vec3d(aim.x, aim.y - 1.0, aim.z);
            lerp = 0.6;
        } else {
            float yawRad = owner.getBodyYaw() * 0.017453292f;
            Vec3d fwd = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad));
            Vec3d right = new Vec3d(-MathHelper.cos(yawRad), 0, -MathHelper.sin(yawRad));
            goal = owner.getPos().add(fwd.multiply(-0.9)).add(right.multiply(0.8))
                    .add(0, 0.35 + Math.sin(age * 0.12) * 0.08, 0);
        }
        Vec3d np = getPos().lerp(goal, lerp);
        double far = fullLeash + 10;
        if (np.squaredDistanceTo(owner.getPos()) > far * far) np = goal;
        setPosition(np.x, np.y, np.z);
        float ny = MathHelper.lerpAngleDegrees(0.4f, getYaw(), faceYaw);
        setYaw(ny);
        setBodyYaw(ny);
        setHeadYaw(ny);

        // ---- атака ----
        boolean fighting = target != null && squaredDistanceTo(target) < 9.0;
        dataTracker.set(ATTACKING, fighting || holding);
        if (hitCooldown > 0) hitCooldown--;
        if (fighting && hitCooldown <= 0) {
            boolean barrage = barrageTicks > 0;
            hitCooldown = barrage ? 3 : Math.max(8, 20 - d.speed / 10);
            boolean crit = w.random.nextFloat() < d.critChance();
            float dmg = d.standDamage() * (barrage ? 0.3f : 1f) * (crit ? 1.5f : 1f);
            target.timeUntilRegen = 0;
            if (target.damage(w.getDamageSources().playerAttack(owner), dmg)) {
                if (!barrage) target.takeKnockback(0.4, owner.getX() - target.getX(), owner.getZ() - target.getZ());
                Abilities.elementHit(target, d);
                w.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), crit ? 14 : 3, 0.2, 0.2, 0.2, 0.1);
                w.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.6f, barrage ? 1.4f : 1.0f);
            }
        }
        if (barrageTicks > 0) barrageTicks--;

        // Пасивка Підтримки "Магніт душі": тягне предмети до власника (вимикається в ручному режимі)
        if (support && !manual && age % 4 == 0) {
            double mr = 4.0 + d.stage * 0.5;
            Vec3d pull = owner.getPos().add(0, 0.6, 0);
            for (ItemEntity it : w.getEntitiesByClass(ItemEntity.class, owner.getBoundingBox().expand(mr),
                    x -> x.getItemAge() > 45)) {
                Vec3d dir = pull.subtract(it.getPos());
                if (dir.lengthSquared() < 0.25) continue;
                it.setVelocity(dir.normalize().multiply(0.3));
                it.velocityModified = true;
            }
        }

        // ---- візуальні деталі: ефект від типу душі, слід зі стадії 3 ----
        if (d.stage >= 1 && age % 8 == 0) {
            seenParticles(w, ambient(d.primary), 1, 0.25, 0.4, 0.25, 0.01);
        }
        if (d.stage >= 3 && age % 6 == 0) {
            seenParticles(w, ParticleTypes.END_ROD, 1, 0.2, 0.4, 0.2, 0.0);
        }
        if (d.element != Element.NONE && age % 5 == 0) {
            seenParticles(w, ElementZone.particle(d.element), 1, 0.3, 0.5, 0.3, 0.01);
        }
    }

    /** Частинки самого стенда бачать лише гравці, у яких теж є стенд. */
    private void seenParticles(ServerWorld w, ParticleEffect type, int count, double dx, double dy, double dz, double speed) {
        StandManager m = StandManager.get(w.getServer());
        for (ServerPlayerEntity v : w.getPlayers()) {
            if (v.squaredDistanceTo(this) < 1024 && m.get(v.getUuid()) != null) {
                w.spawnParticles(v, type, false, getX(), getY() + 1.0, getZ(), count, dx, dy, dz, speed);
            }
        }
    }

    private LivingEntity findTarget(ServerWorld w, ServerPlayerEntity owner, double radius) {
        Box box = owner.getBoundingBox().expand(radius);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box,
                x -> x instanceof Monster && !(x instanceof EndermanEntity) && x.isAlive())) {
            double dd = e.squaredDistanceTo(owner);
            if (dd < bd && dd <= radius * radius) { bd = dd; best = e; }
        }
        return best;
    }

    // ---- LivingEntity обов'язкові методи ----
    @Override public Iterable<ItemStack> getArmorItems() { return Collections.emptyList(); }
    @Override public ItemStack getEquippedStack(EquipmentSlot slot) { return ItemStack.EMPTY; }
    @Override public void equipStack(EquipmentSlot slot, ItemStack stack) {}
    @Override public Arm getMainArm() { return Arm.RIGHT; }

    // ---- стенд невразливий і не заважає ----
    @Override
    public boolean damage(DamageSource source, float amount) {
        if (source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)) return super.damage(source, amount);
        return false;
    }
    @Override public boolean isPushable() { return false; }
    @Override public void pushAway(Entity entity) {}
    @Override public boolean canHit() { return false; }
    @Override public boolean isAttackable() { return false; }
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isFireImmune() { return true; }
    @Override public boolean canBreatheInWater() { return true; }
}
