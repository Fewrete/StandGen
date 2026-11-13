package dev.standgen.entity;

import dev.standgen.stand.Abilities;
import dev.standgen.stand.Archetype;
import dev.standgen.stand.StandForm;
import dev.standgen.stand.StandData;
import dev.standgen.stand.StandManager;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
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

    private UUID ownerId;
    private LivingEntity target;
    private int targetTimeout;
    private int hitCooldown;
    private int barrageTicks;

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
    }

    public void setTarget(LivingEntity t) {
        this.target = t;
        this.targetTimeout = 100;
    }

    public void startBarrage(int ticks) {
        this.barrageTicks = ticks;
        this.hitCooldown = 0;
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
        if (age % 40 == 0) Abilities.applyPassive(owner, d);

        // ---- цілі ----
        double leash = d.leash();
        if (target != null && (!target.isAlive() || target.getWorld() != w
                || target.squaredDistanceTo(owner) > leash * leash || --targetTimeout <= 0)) {
            target = null;
        }
        if (target == null && (barrageTicks > 0 || age % 5 == 0)) {
            LivingEntity found = findTarget(w, owner, barrageTicks > 0 ? leash : 4.0);
            if (found != null) {
                target = found;
                targetTimeout = 40;
            }
        }

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
        } else {
            float yawRad = owner.getBodyYaw() * 0.017453292f;
            Vec3d fwd = new Vec3d(-MathHelper.sin(yawRad), 0, MathHelper.cos(yawRad));
            Vec3d right = new Vec3d(-MathHelper.cos(yawRad), 0, -MathHelper.sin(yawRad));
            goal = owner.getPos().add(fwd.multiply(-0.9)).add(right.multiply(0.8))
                    .add(0, 0.35 + Math.sin(age * 0.12) * 0.08, 0);
        }
        Vec3d np = getPos().lerp(goal, lerp);
        if (np.squaredDistanceTo(owner.getPos()) > (leash + 10) * (leash + 10)) np = goal;
        setPosition(np.x, np.y, np.z);
        float ny = MathHelper.lerpAngleDegrees(0.4f, getYaw(), faceYaw);
        setYaw(ny);
        setBodyYaw(ny);
        setHeadYaw(ny);

        // ---- атака ----
        boolean fighting = target != null && squaredDistanceTo(target) < 9.0;
        dataTracker.set(ATTACKING, fighting);
        if (hitCooldown > 0) hitCooldown--;
        if (fighting && hitCooldown <= 0) {
            boolean barrage = barrageTicks > 0;
            hitCooldown = barrage ? 3 : Math.max(8, 20 - d.speed / 10);
            float dmg = d.standDamage() * (barrage ? 0.3f : 1f);
            target.timeUntilRegen = 0;
            if (target.damage(w.getDamageSources().playerAttack(owner), dmg)) {
                if (!barrage) target.takeKnockback(0.4, owner.getX() - target.getX(), owner.getZ() - target.getZ());
                w.spawnParticles(ParticleTypes.CRIT, target.getX(), target.getBodyY(0.5), target.getZ(), 3, 0.2, 0.2, 0.2, 0.1);
                w.playSound(null, target.getX(), target.getY(), target.getZ(),
                        SoundEvents.ENTITY_PLAYER_ATTACK_STRONG, SoundCategory.PLAYERS, 0.6f, barrage ? 1.4f : 1.0f);
            }
        }
        if (barrageTicks > 0) barrageTicks--;

        // слід від стадії 3+
        if (d.stage >= 3 && age % 3 == 0) {
            w.spawnParticles(ParticleTypes.END_ROD, getX(), getY() + 1.0, getZ(), 1, 0.2, 0.4, 0.2, 0.0);
        }
    }

    private LivingEntity findTarget(ServerWorld w, ServerPlayerEntity owner, double radius) {
        Box box = owner.getBoundingBox().expand(radius);
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box,
                x -> x instanceof Monster && !(x instanceof EndermanEntity) && x.isAlive())) {
            double dd = e.squaredDistanceTo(owner);
            if (dd < bd) { bd = dd; best = e; }
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
