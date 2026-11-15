package dev.standgen.stand;

import dev.standgen.entity.StandEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashSet;
import java.util.Set;

public final class Abilities {
    private Abilities() {}

    public static int cooldown(Skill s, StandData d) {
        if (s == Skill.BARRAGE) return s.baseCooldown;          // фіксовані ~11.5 с
        return Math.max(40, Math.round(s.baseCooldown * (1f - d.speed * 0.003f)));
    }

    // ---------------- допоміжні ----------------
    public static boolean validTarget(LivingEntity x, ServerPlayerEntity owner) {
        if (x == owner || x instanceof StandEntity || x instanceof ArmorStandEntity || !x.isAlive() || x.isSpectator())
            return false;
        return !(x instanceof TameableEntity t && t.isOwner(owner));
    }

    /** Жива істота, на яку дивиться гравець (до reach блоків, не крізь стіни). */
    public static LivingEntity lookedEntity(ServerWorld w, ServerPlayerEntity p, double reach) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        for (double t = 0.8; t <= reach; t += 0.5) {
            Vec3d pt = eye.add(look.multiply(t));
            BlockPos bp = BlockPos.ofFloored(pt);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) return null;
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(pt, pt).expand(0.7),
                    x -> validTarget(x, p))) {
                return e;
            }
        }
        return null;
    }

    private static void fail(ServerPlayerEntity p, String key) {
        p.sendMessage(Text.translatable(key).formatted(Formatting.RED), true);
    }

    private static void sound(ServerWorld w, double x, double y, double z, SoundEvent e, float vol, float pitch) {
        w.playSound(null, x, y, z, e, SoundCategory.PLAYERS, vol, pitch);
    }

    private static void ring(ServerWorld w, ParticleEffect type, Vec3d c, double radius, int n) {
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n;
            w.spawnParticles(type, c.x + Math.cos(a) * radius, c.y + 0.2, c.z + Math.sin(a) * radius, 1, 0, 0.1, 0, 0.02);
        }
    }

    /** Форма Броня носиться на гравці й не може бити далеко: її здібності працюють лише поблизу. */
    private static boolean armored(StandData d) { return d.form == StandForm.ARMORED; }

    public static LivingEntity nearestHostile(ServerWorld w, ServerPlayerEntity p, double radius) {
        LivingEntity best = null;
        double bd = Double.MAX_VALUE;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(radius),
                x -> x instanceof Monster && !(x instanceof EndermanEntity) && validTarget(x, p))) {
            double dd = e.squaredDistanceTo(p);
            if (dd < bd && dd <= radius * radius) { bd = dd; best = e; }
        }
        return best;
    }

    // ---------------- виконання ----------------
    /** @return true якщо здібність спрацювала (тоді йде перезарядка) */
    public static boolean execute(Skill s, ServerPlayerEntity p, StandEntity stand, StandData d) {
        ServerWorld w = (ServerWorld) p.getWorld();
        switch (s) {
            case EXCAVATE -> { return excavate(p, w, d); }
            case BARRAGE -> {
                StandRuntime.startBarrage(p, stand, 40 + d.stage * 5);
                return true;
            }
            case BASTION -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 400, Math.min(3, 1 + d.stage / 3)));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1));
                w.spawnParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.8, 0.6, 0.2);
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_ANVIL_PLACE, 0.8f, 1.3f);
                return true;
            }
            case DASH -> { return dash(p, w, d); }
            case PIERCE -> { return pierce(p, w, d); }
            case RESURGENCE -> {
                p.heal(4 + d.stage);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
                w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, 0.5f, 1.5f);
                return true;
            }
            case GRAB -> { return grab(p, w, d); }
            case WALL -> { return false; }   // це перемикач: обробляється в StandRuntime.toggleStance
            case THROW -> { return thrust(p, w, d); }
            case BURST -> { return burst(p, w, d); }
            case QUAKE -> { return quake(p, w, d); }
            case FURY -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, 300, 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 300, 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 300, 0));
                StandRuntime.startBarrage(p, stand, 100);
                w.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
                return true;
            }
            case CITADEL -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 2));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 0));
                for (ServerPlayerEntity o : w.getPlayers(x -> x != p && x.squaredDistanceTo(p) < 64)) {
                    o.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 1));
                    o.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 0));
                }
                ring(w, ParticleTypes.ENCHANT, p.getPos(), 3, 40);
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1f, 1f);
                return true;
            }
            case TIMESTOP -> { return timestop(p, w, d); }
            case ARROW_RAIN -> { return arrowRain(p, w, d); }
            case SECOND_CHANCE -> {
                StandRuntime.armRevive(p);
                p.heal(4);
                w.spawnParticles(ParticleTypes.TOTEM_OF_UNDYING, p.getX(), p.getY() + 1, p.getZ(), 30, 0.5, 0.8, 0.5, 0.3);
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, 0.6f, 1.2f);
                p.sendMessage(Text.translatable("msg.standgen.revive_armed").formatted(Formatting.GOLD));
                return true;
            }
        }
        return false;
    }

    private static boolean excavate(ServerPlayerEntity p, ServerWorld w, StandData d) {
        double reach = armored(d) ? 5.0 : 5 + d.range * 0.08;
        HitResult hit = p.raycast(reach, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            fail(p, "msg.standgen.fail_no_block");
            return false;
        }
        BlockPos c = ((BlockHitResult) hit).getBlockPos();
        int r = 1 + (d.stage >= 3 ? 1 : 0) + (d.stage >= 6 ? 1 : 0);
        float maxHardness = 5 + d.stage * 8;
        int broken = 0;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = c.add(dx, dy, dz);
                    BlockState st = w.getBlockState(pos);
                    if (st.isAir()) continue;
                    float h = st.getHardness(w, pos);
                    if (h < 0 || h > maxHardness) continue;
                    if (w.getBlockEntity(pos) != null) continue;
                    if (!w.canPlayerModifyAt(p, pos)) continue;
                    if (w.breakBlock(pos, true, p)) broken++;
                }
        return broken > 0;
    }

    private static boolean dash(ServerPlayerEntity p, ServerWorld w, StandData d) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d start = p.getPos();
        double max = 6 + d.speed * 0.12;
        Vec3d last = start;
        for (double t = 0.5; t <= max; t += 0.5) {
            Vec3d next = start.add(look.multiply(t));
            Box box = p.getBoundingBox().offset(next.subtract(start));
            if (!w.isSpaceEmpty(p, box)) break;
            last = next;
        }
        if (last.squaredDistanceTo(start) < 1.0) {
            fail(p, "msg.standgen.fail_no_room");
            return false;
        }
        w.spawnParticles(ParticleTypes.CLOUD, start.x, start.y + 1, start.z, 25, 0.3, 0.5, 0.3, 0.05);
        p.requestTeleport(last.x, last.y, last.z);
        p.fallDistance = 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 1 + d.stage / 3));
        sound(w, last.x, last.y, last.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6f, 1.6f);
        return true;
    }

    private static boolean pierce(ServerPlayerEntity p, ServerWorld w, StandData d) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        double max = armored(d) ? 4.5 : 25 + d.range * 0.3;
        float dmg = 3 + d.power * 0.08f + d.precision * 0.03f;
        Set<Entity> hit = new HashSet<>();
        for (double t = 1; t <= max; t += 0.5) {
            Vec3d pt = eye.add(look.multiply(t));
            BlockPos bp = BlockPos.ofFloored(pt);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) break;
            w.spawnParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(pt, pt).expand(0.6),
                    x -> validTarget(x, p))) {
                if (hit.add(e)) {
                    e.timeUntilRegen = 0;
                    e.damage(w.getDamageSources().playerAttack(p), dmg);
                }
            }
        }
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, 1f, 1.8f);
        return true;
    }

    // ---- форми ----
    private static boolean grab(ServerPlayerEntity p, ServerWorld w, StandData d) {
        LivingEntity t = lookedEntity(w, p, armored(d) ? 3.5 : 8 + d.range * 0.1);
        if (t == null) { fail(p, "msg.standgen.fail_no_target"); return false; }
        Vec3d pull = p.getPos().subtract(t.getPos());
        double len = pull.length();
        Vec3d v = len < 0.01 ? Vec3d.ZERO : pull.multiply(1.0 / len).multiply(Math.min(1.6, 0.4 + len * 0.15)).add(0, 0.25, 0);
        t.setVelocity(v);
        t.velocityModified = true;
        int dur = 80 + d.stage * 10;
        t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, dur, 2));
        t.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, dur, 1));
        t.timeUntilRegen = 0;
        t.damage(w.getDamageSources().playerAttack(p), d.standDamage());
        w.spawnParticles(ParticleTypes.CRIT, t.getX(), t.getBodyY(0.5), t.getZ(), 15, 0.3, 0.5, 0.3, 0.2);
        sound(w, t.getX(), t.getY(), t.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
        return true;
    }

    private static boolean thrust(ServerPlayerEntity p, ServerWorld w, StandData d) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        double max = 14 + d.range * 0.15;
        float dmg = 5 + d.power * 0.07f;
        Set<Entity> hit = new HashSet<>();
        Vec3d end = eye;
        for (double t = 1; t <= max; t += 0.5) {
            Vec3d pt = eye.add(look.multiply(t));
            BlockPos bp = BlockPos.ofFloored(pt);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) break;
            end = pt;
            w.spawnParticles(ParticleTypes.CRIT, pt.x, pt.y, pt.z, 2, 0.1, 0.1, 0.1, 0.0);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(pt, pt).expand(1.0),
                    x -> validTarget(x, p))) {
                if (hit.add(e)) {
                    e.timeUntilRegen = 0;
                    e.damage(w.getDamageSources().playerAttack(p), dmg);
                    e.takeKnockback(1.0, -look.x, -look.z);
                }
            }
        }
        w.spawnParticles(ParticleTypes.EXPLOSION, end.x, end.y, end.z, 1, 0, 0, 0, 0);
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_SWEEP, 1f, 0.8f);
        return true;
    }

    private static boolean burst(ServerPlayerEntity p, ServerWorld w, StandData d) {
        double r = 4 + d.range * 0.04;
        float dmg = 4 + d.power * 0.06f;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r),
                x -> validTarget(x, p) && x.squaredDistanceTo(p) <= r * r)) {
            e.timeUntilRegen = 0;
            e.damage(w.getDamageSources().playerAttack(p), dmg);
            e.takeKnockback(0.9, p.getX() - e.getX(), p.getZ() - e.getZ());
        }
        ring(w, ParticleTypes.END_ROD, p.getPos(), r * 0.6, 30);
        ring(w, ParticleTypes.CRIT, p.getPos(), r, 40);
        w.spawnParticles(ParticleTypes.EXPLOSION, p.getX(), p.getY() + 1, p.getZ(), 1, 0, 0, 0, 0);
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, 0.7f, 1.4f);
        return true;
    }

    // ---- ультимейти ----
    private static boolean quake(ServerPlayerEntity p, ServerWorld w, StandData d) {
        double r = 6;
        float dmg = 5 + d.power * 0.06f;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r),
                x -> validTarget(x, p) && x.squaredDistanceTo(p) <= r * r)) {
            e.timeUntilRegen = 0;
            e.damage(w.getDamageSources().playerAttack(p), dmg);
            e.setVelocity(e.getVelocity().add(0, 0.9, 0));
            e.velocityModified = true;
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 2));
        }
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, 200, 2));
        ring(w, ParticleTypes.POOF, p.getPos(), 3, 30);
        ring(w, ParticleTypes.POOF, p.getPos(), 6, 50);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0, 0, 0, 0);
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        return true;
    }

    private static boolean timestop(ServerPlayerEntity p, ServerWorld w, StandData d) {
        double r = 12 + d.range * 0.1;
        int dur = 100 + d.stage * 10;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r),
                x -> validTarget(x, p) && !(x instanceof PlayerEntity) && x.squaredDistanceTo(p) <= r * r)) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, dur, 6));
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, dur, 3));
            e.setVelocity(Vec3d.ZERO);
            e.velocityModified = true;
        }
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, 2));
        ring(w, ParticleTypes.END_ROD, p.getPos(), 2, 30);
        ring(w, ParticleTypes.END_ROD, p.getPos(), r * 0.7, 60);
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1f, 0.5f);
        return true;
    }

    private static boolean arrowRain(ServerPlayerEntity p, ServerWorld w, StandData d) {
        double reach = armored(d) ? 8.0 : 30 + d.range * 0.2;
        HitResult hr = p.raycast(reach, 1f, false);
        Vec3d center = hr.getType() == HitResult.Type.MISS
                ? p.getEyePos().add(p.getRotationVec(1f).multiply(Math.min(reach, 18)))
                : hr.getPos();
        int n = Math.min(24, 10 + d.stage * 2);
        for (int i = 0; i < n; i++) {
            ArrowEntity a = new ArrowEntity(w,
                    center.x + (w.random.nextDouble() - 0.5) * 8,
                    center.y + 14 + w.random.nextDouble() * 4,
                    center.z + (w.random.nextDouble() - 0.5) * 8);
            a.setOwner(p);
            a.setVelocity(0, -1, 0, 2.2f, 4.0f);
            a.setCritical(true);
            a.setDamage(1.2 + d.power * 0.015);
            a.pickupType = PersistentProjectileEntity.PickupPermission.DISALLOWED;
            w.spawnEntity(a);
        }
        ring(w, ParticleTypes.END_ROD, center, 4, 40);
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, 1f, 0.6f);
        return true;
    }

    /** Пасивний бонус від основного типу, поки стенд викликано. */
    public static void applyPassive(ServerPlayerEntity p, StandData d) {
        StatusEffect eff = switch (d.primary) {
            case MINER -> StatusEffects.HASTE;
            case FIGHTER -> StatusEffects.STRENGTH;
            case BUILDER -> StatusEffects.RESISTANCE;
            case RUNNER -> StatusEffects.SPEED;
            case ARCHER -> StatusEffects.JUMP_BOOST;
            case SURVIVOR -> StatusEffects.REGENERATION;
        };
        if (d.primary == Archetype.SURVIVOR && d.stage < 2) return;
        if (d.primary == Archetype.FIGHTER && d.stage < 3) return;
        int cap = eff == StatusEffects.STRENGTH ? 0 : eff == StatusEffects.RESISTANCE ? 1 : 2;
        int amp = d.primary == Archetype.SURVIVOR ? 0 : Math.min(d.stage / 3, cap);
        p.addStatusEffect(new StatusEffectInstance(eff, 80, amp, true, false));
    }
}
