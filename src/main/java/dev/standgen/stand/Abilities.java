package dev.standgen.stand;

import dev.standgen.entity.StandEntity;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.Entity;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
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

    public static int cooldown(Archetype a, StandData d) {
        return Math.max(100, 300 - (int) (d.speed * 2.5f));
    }

    /** @return true якщо здібність спрацювала (тоді йде перезарядка) */
    public static boolean execute(Archetype a, ServerPlayerEntity p, StandEntity stand, StandData d) {
        ServerWorld w = (ServerWorld) p.getWorld();
        switch (a) {
            case FIGHTER -> {
                stand.startBarrage(40 + d.stage * 5);
                return true;
            }
            case MINER -> { return excavate(p, w, d); }
            case BUILDER -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 600, 1 + d.stage / 2));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 200, 1));
                w.spawnParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.8, 0.6, 0.2);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_ANVIL_PLACE, SoundCategory.PLAYERS, 0.8f, 1.3f);
                return true;
            }
            case RUNNER -> { return dash(p, w, d); }
            case ARCHER -> { return pierce(p, w, d); }
            case SURVIVOR -> {
                p.heal(6 + d.stage * 2);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 100, 1));
                w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, SoundCategory.PLAYERS, 0.5f, 1.5f);
                return true;
            }
        }
        return false;
    }

    private static boolean excavate(ServerPlayerEntity p, ServerWorld w, StandData d) {
        double reach = 5 + d.range * 0.08;
        HitResult hit = p.raycast(reach, 1.0f, false);
        if (hit.getType() != HitResult.Type.BLOCK) {
            p.sendMessage(Text.literal("Немає блока, на який можна націлитись").formatted(Formatting.RED), true);
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
            p.sendMessage(Text.literal("Немає місця для ривка").formatted(Formatting.RED), true);
            return false;
        }
        w.spawnParticles(ParticleTypes.CLOUD, start.x, start.y + 1, start.z, 25, 0.3, 0.5, 0.3, 0.05);
        p.requestTeleport(last.x, last.y, last.z);
        p.fallDistance = 0;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 100, 1 + d.stage / 3));
        w.playSound(null, last.x, last.y, last.z, SoundEvents.ENTITY_ENDERMAN_TELEPORT, SoundCategory.PLAYERS, 0.6f, 1.6f);
        return true;
    }

    private static boolean pierce(ServerPlayerEntity p, ServerWorld w, StandData d) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        double max = 25 + d.range * 0.3;
        float dmg = 4 + d.power * 0.12f + d.precision * 0.05f;
        Set<Entity> hit = new HashSet<>();
        for (double t = 1; t <= max; t += 0.5) {
            Vec3d pt = eye.add(look.multiply(t));
            BlockPos bp = BlockPos.ofFloored(pt);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) break;
            w.spawnParticles(ParticleTypes.END_ROD, pt.x, pt.y, pt.z, 1, 0, 0, 0, 0);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(pt, pt).expand(0.6),
                    x -> x != p && !(x instanceof StandEntity) && x.isAlive())) {
                if (hit.add(e)) {
                    e.timeUntilRegen = 0;
                    e.damage(w.getDamageSources().playerAttack(p), dmg);
                }
            }
        }
        w.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ARROW_SHOOT, SoundCategory.PLAYERS, 1f, 1.8f);
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
        int cap = (eff == StatusEffects.STRENGTH || eff == StatusEffects.RESISTANCE) ? 1 : 2;
        int amp = d.primary == Archetype.SURVIVOR ? 0 : Math.min(d.stage / 3, cap);
        p.addStatusEffect(new StatusEffectInstance(eff, 80, amp, true, false));
    }
}
