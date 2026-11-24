package dev.standgen.entity;

import dev.standgen.StandGenMod;
import dev.standgen.stand.Abilities;
import dev.standgen.stand.Skill;
import dev.standgen.stand.StandData;
import dev.standgen.stand.StandManager;
import dev.standgen.stand.StandRuntime;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.EntitySpawnS2CPacket;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Візуальні та ігрові ефекти здібностей. Один тип сутності, вид задається полем FX:
 * THROWN (зброя, що реально летить і повертається), SPHERE (сфера зупинки часу), PULSE (ударна хвиля).
 */
public class StandFxEntity extends Entity {
    public static final int THROWN = 0, SPHERE = 1, PULSE = 2;

    private static final TrackedData<Integer> FX = DataTracker.registerData(StandFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> COLOR1 = DataTracker.registerData(StandFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> COLOR2 = DataTracker.registerData(StandFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> LIFE = DataTracker.registerData(StandFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Integer> WEAPON = DataTracker.registerData(StandFxEntity.class, TrackedDataHandlerRegistry.INTEGER);
    private static final TrackedData<Float> SIZE = DataTracker.registerData(StandFxEntity.class, TrackedDataHandlerRegistry.FLOAT);

    /** Лічильник тіків (однаково йде на сервері й на клієнті). */
    private int ticks;

    // --- лише сервер: політ зброї ---
    private UUID ownerId;
    private Vec3d dir = Vec3d.ZERO;
    private boolean returning;
    private double travelled, maxDist;
    private float damage;
    private final Set<UUID> hit = new HashSet<>();

    public StandFxEntity(EntityType<? extends StandFxEntity> type, World world) {
        super(type, world);
        this.noClip = true;
        this.setNoGravity(true);
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(FX, THROWN);
        dataTracker.startTracking(COLOR1, 0xFFFFFF);
        dataTracker.startTracking(COLOR2, 0xFFFFFF);
        dataTracker.startTracking(LIFE, 40);
        dataTracker.startTracking(WEAPON, 0);
        dataTracker.startTracking(SIZE, 1f);
    }

    // ---- для рендера ----
    public int getFx() { return dataTracker.get(FX); }
    public int getColor1() { return dataTracker.get(COLOR1); }
    public int getColor2() { return dataTracker.get(COLOR2); }
    public int getLife() { return dataTracker.get(LIFE); }
    public int getWeaponKind() { return dataTracker.get(WEAPON); }
    public float getSize() { return dataTracker.get(SIZE); }
    public int getTicks() { return ticks; }

    // ---- створення ----
    private static StandFxEntity make(ServerWorld w, int fx, Vec3d pos, int c1, int c2, float size, int life) {
        StandFxEntity e = new StandFxEntity(StandGenMod.FX, w);
        e.setPosition(pos.x, pos.y, pos.z);
        e.dataTracker.set(FX, fx);
        e.dataTracker.set(COLOR1, c1);
        e.dataTracker.set(COLOR2, c2);
        e.dataTracker.set(SIZE, size);
        e.dataTracker.set(LIFE, life);
        return e;
    }

    /** Сфера зупинки часу: розширюється, тримається, стискається. */
    public static void spawnSphere(ServerWorld w, Vec3d pos, int c1, int c2, float radius, int life) {
        w.spawnEntity(make(w, SPHERE, pos, c1, c2, radius, life));
    }

    /** Ударна хвиля, що розходиться від точки. */
    public static void spawnPulse(ServerWorld w, Vec3d pos, int c1, int c2, float radius, int life) {
        w.spawnEntity(make(w, PULSE, pos, c1, c2, radius, life));
    }

    /** Зброя, що летить у напрямку dir, і повертається до стенда (або до гравця). */
    public static void spawnThrown(ServerWorld w, ServerPlayerEntity owner, StandData d, Vec3d from, Vec3d dir,
                                   double maxDist, float damage, float size) {
        StandFxEntity e = make(w, THROWN, from, d.color1, d.color2, size, 160);
        e.dataTracker.set(WEAPON, d.primary.ordinal());
        e.ownerId = owner.getUuid();
        e.dir = dir.normalize();
        e.maxDist = maxDist;
        e.damage = damage;
        e.aim(e.dir);
        w.spawnEntity(e);
    }

    private void aim(Vec3d d) {
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Math.toDegrees(-Math.asin(MathHelper.clamp(d.y, -1.0, 1.0)));
        setYaw(yaw);
        setPitch(pitch);
    }

    // ---- логіка ----
    @Override
    public void tick() {
        super.tick();
        ticks++;
        if (getWorld().isClient) return;
        ServerWorld w = (ServerWorld) getWorld();
        if (ticks > getLife()) { finish(w); return; }
        switch (getFx()) {
            case THROWN -> tickThrown(w);
            case SPHERE -> {
                // тихий тікаючий годинник, поки час зупинено
                if (ticks % 10 == 0 && ticks < getLife() - 10) {
                    w.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_COMPARATOR_CLICK,
                            SoundCategory.PLAYERS, 0.5f, 0.6f);
                }
            }
            default -> {}
        }
    }

    private void finish(ServerWorld w) {
        if (getFx() == THROWN && ownerId != null) {
            PlayerEntity po = w.getPlayerByUuid(ownerId);
            if (po instanceof ServerPlayerEntity owner) {
                StandEntity s = StandRuntime.getStand(owner);
                if (s != null) s.stopAnim(Skill.THROW);
            }
        }
        discard();
    }

    private Vec3d homePoint(ServerWorld w, ServerPlayerEntity owner) {
        StandEntity s = StandRuntime.getStand(owner);
        return s != null ? s.getPos().add(0, 0.9, 0) : owner.getEyePos().subtract(0, 0.3, 0);
    }

    private void tickThrown(ServerWorld w) {
        PlayerEntity po = ownerId == null ? null : w.getPlayerByUuid(ownerId);
        if (!(po instanceof ServerPlayerEntity owner) || !owner.isAlive()) { finish(w); return; }
        StandData d = StandManager.get(w.getServer()).get(ownerId);
        if (d == null) { finish(w); return; }

        double speed = returning ? 2.0 : 1.7;
        if (!returning) {
            Vec3d next = getPos().add(dir.multiply(speed));
            BlockPos bp = BlockPos.ofFloored(next);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) {
                turnBack(w);
                w.spawnParticles(ParticleTypes.CRIT, getX(), getY(), getZ(), 12, 0.3, 0.3, 0.3, 0.3);
                w.playSound(null, getX(), getY(), getZ(), SoundEvents.BLOCK_ANVIL_LAND, SoundCategory.PLAYERS, 0.5f, 1.6f);
            } else {
                setPosition(next.x, next.y, next.z);
                travelled += speed;
                if (travelled >= maxDist) turnBack(w);
            }
        } else {
            Vec3d to = homePoint(w, owner).subtract(getPos());
            double len = to.length();
            if (len < 1.3 || getPos().squaredDistanceTo(owner.getPos()) > 70 * 70) { finish(w); return; }
            Vec3d step = to.multiply(Math.min(speed, len) / len);
            setPosition(getX() + step.x, getY() + step.y, getZ() + step.z);
            dir = to.multiply(1.0 / len);
            aim(dir);
        }

        // шкода всім, кого зачепила зброя (один раз за прохід)
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(getPos(), getPos()).expand(1.2),
                x -> Abilities.validTarget(x, owner))) {
            if (!hit.add(e.getUuid())) continue;
            e.timeUntilRegen = 0;
            e.damage(w.getDamageSources().playerAttack(owner), damage);
            e.takeKnockback(0.5, -dir.x, -dir.z);
            w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.5), e.getZ(), 8, 0.25, 0.25, 0.25, 0.2);
            w.playSound(null, e.getX(), e.getY(), e.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP,
                    SoundCategory.PLAYERS, 0.8f, returning ? 1.3f : 0.9f);
        }

        // слід
        DustParticleEffect trail = new DustParticleEffect(new Vector3f(
                (d.color1 >> 16 & 255) / 255f, (d.color1 >> 8 & 255) / 255f, (d.color1 & 255) / 255f), 1.3f);
        w.spawnParticles(trail, getX(), getY(), getZ(), 2, 0.1, 0.1, 0.1, 0.0);
        if (ticks % 2 == 0) w.spawnParticles(ParticleTypes.END_ROD, getX(), getY(), getZ(), 1, 0.05, 0.05, 0.05, 0.0);
        if (ticks % 6 == 0) w.playSound(null, getX(), getY(), getZ(), SoundEvents.ITEM_TRIDENT_THROW,
                SoundCategory.PLAYERS, 0.4f, 1.6f);
    }

    private void turnBack(ServerWorld w) {
        returning = true;
        hit.clear();
    }

    // ---- службове ----
    @Override public boolean shouldSave() { return false; }
    @Override public boolean isFireImmune() { return true; }
    @Override public boolean canHit() { return false; }
    @Override protected void readCustomDataFromNbt(NbtCompound nbt) {}
    @Override protected void writeCustomDataToNbt(NbtCompound nbt) {}

    @Override
    public Packet<ClientPlayPacketListener> createSpawnPacket() {
        return new EntitySpawnS2CPacket(this);
    }
}
