package dev.standgen.stand;

import dev.standgen.entity.StandFxEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Зупинка часу: у сфері навколо місця здібності моби й чужі снаряди по-справжньому завмирають
 * (без руху, без гравітації, без цілі), а власник рухається вільно. Коли час спливає, усе відновлюється.
 */
public final class TimeStop {
    private TimeStop() {}

    private static final class Frozen {
        final boolean noGravity;
        final Vec3d velocity;
        Frozen(boolean noGravity, Vec3d velocity) { this.noGravity = noGravity; this.velocity = velocity; }
    }

    private static final class Zone {
        final UUID owner;
        final ServerWorld world;
        final Vec3d center;
        final double radius;
        final int end;
        final int color1, color2;
        final Map<UUID, Frozen> frozen = new HashMap<>();
        Zone(UUID owner, ServerWorld world, Vec3d center, double radius, int end, int c1, int c2) {
            this.owner = owner; this.world = world; this.center = center; this.radius = radius;
            this.end = end; this.color1 = c1; this.color2 = c2;
        }
    }

    private static final Map<UUID, Zone> ZONES = new HashMap<>();

    public static boolean isActive(UUID owner) { return ZONES.containsKey(owner); }

    public static void start(ServerPlayerEntity p, ServerWorld w, StandData d, double radius, int dur) {
        Zone old = ZONES.remove(p.getUuid());
        if (old != null) release(old, false);
        Vec3d c = p.getPos();
        ZONES.put(p.getUuid(), new Zone(p.getUuid(), w, c, radius, p.getServer().getTicks() + dur, d.color1, d.color2));
        // сфера живе трохи довше за заморозку: останні тіки вона стискається, і час "повертається"
        StandFxEntity.spawnSphere(w, c, d.color1, d.color2, (float) radius, dur + 10);
        StandFxEntity.spawnPulse(w, c, d.color1, d.color2, (float) radius, 14);
        w.playSound(null, c.x, c.y, c.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 1f, 0.5f);
        w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 1f, 0.5f);
    }

    /** Щотіка (END_SERVER_TICK). */
    public static void tick(MinecraftServer server) {
        if (ZONES.isEmpty()) return;
        int now = server.getTicks();
        Iterator<Zone> it = ZONES.values().iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(z.owner);
            if (now >= z.end || owner == null || !owner.isAlive() || owner.getWorld() != z.world) {
                release(z, true);
                it.remove();
                continue;
            }
            apply(z, owner, now);
        }
    }

    /** Зупинка сервера: повернути всім гравітацію, щоб "NoGravity" не збереглося у світі. */
    public static void endAll() {
        for (Zone z : ZONES.values()) release(z, false);
        ZONES.clear();
    }

    private static void apply(Zone z, ServerPlayerEntity owner, int now) {
        double r = z.radius;
        Box box = new Box(z.center.x - r, z.center.y - r, z.center.z - r, z.center.x + r, z.center.y + r, z.center.z + r);
        Set<UUID> seen = new HashSet<>();
        for (Entity e : z.world.getOtherEntities(owner, box)) {
            if (e.squaredDistanceTo(z.center) > r * r) continue;
            UUID id = e.getUuid();
            Frozen f = z.frozen.get(id);
            if (e instanceof ProjectileEntity pe) {
                if (pe.getOwner() == owner) continue;                       // власні снаряди летять далі
                if (f == null) {
                    if (pe.getVelocity().lengthSquared() < 0.0025) continue;  // уже встромлена стріла
                    z.frozen.put(id, new Frozen(pe.hasNoGravity(), pe.getVelocity()));
                    pe.setNoGravity(true);
                    pe.setVelocity(Vec3d.ZERO);
                    pe.velocityModified = true;
                } else {
                    pe.setVelocity(Vec3d.ZERO);
                    if (now % 10 == 0) pe.velocityModified = true;
                }
                seen.add(id);
            } else if (e instanceof LivingEntity le) {
                if (le instanceof PlayerEntity || !Abilities.validTarget(le, owner)) continue;
                if (f == null) {
                    z.frozen.put(id, new Frozen(le.hasNoGravity(), Vec3d.ZERO));
                    le.setNoGravity(true);
                    le.velocityModified = true;
                }
                le.setVelocity(Vec3d.ZERO);
                if (now % 4 == 0) {
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 8, 6, false, false));
                    le.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 8, 3, false, false));
                    if (le instanceof MobEntity mob) {
                        mob.setTarget(null);          // не атакує і не підпалює кріпер
                        mob.getNavigation().stop();
                    }
                }
                seen.add(id);
            }
        }
        // хто вийшов із зони або зник: відпустити
        Iterator<Map.Entry<UUID, Frozen>> it = z.frozen.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Frozen> en = it.next();
            if (seen.contains(en.getKey())) continue;
            Entity ent = z.world.getEntity(en.getKey());
            if (ent != null) restore(ent, en.getValue());
            it.remove();
        }
    }

    private static void restore(Entity e, Frozen f) {
        e.setNoGravity(f.noGravity);
        if (e instanceof ProjectileEntity) {
            e.setVelocity(f.velocity);
            e.velocityModified = true;
        }
    }

    private static void release(Zone z, boolean effects) {
        for (Map.Entry<UUID, Frozen> en : z.frozen.entrySet()) {
            Entity ent = z.world.getEntity(en.getKey());
            if (ent != null) restore(ent, en.getValue());
        }
        z.frozen.clear();
        if (effects) {
            Vec3d c = z.center;
            StandFxEntity.spawnPulse(z.world, c, z.color2, z.color1, (float) z.radius, 14);
            z.world.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_DEACTIVATE, SoundCategory.PLAYERS, 1f, 0.6f);
        }
    }
}
