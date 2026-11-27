package dev.standgen.stand;

import dev.standgen.entity.StandEntity;
import dev.standgen.entity.StandFxEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.effect.StatusEffect;
import net.minecraft.entity.effect.StatusEffectCategory;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.particle.DustColorTransitionParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.registry.tag.TagKey;
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

import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

    /** Лінія частинок кольорів стенда між двома точками (ланцюг захоплення). */
    private static void chain(ServerWorld w, Vec3d from, Vec3d to, StandData d) {
        DustColorTransitionParticleEffect fx = new DustColorTransitionParticleEffect(rgb(d.color1), rgb(d.color2), 1.2f);
        double len = from.distanceTo(to);
        int n = Math.max(2, (int) Math.ceil(len / 0.3));
        for (int i = 0; i <= n; i++) {
            Vec3d pt = from.lerp(to, i / (double) n);
            w.spawnParticles(fx, pt.x, pt.y, pt.z, 1, 0.03, 0.03, 0.03, 0.0);
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

    // ---------------- форма Підтримка: допоміжні ----------------
    private static final List<TagKey<Block>> ORE_TAGS = List.of(BlockTags.COAL_ORES, BlockTags.IRON_ORES, BlockTags.COPPER_ORES,
            BlockTags.GOLD_ORES, BlockTags.REDSTONE_ORES, BlockTags.LAPIS_ORES, BlockTags.EMERALD_ORES, BlockTags.DIAMOND_ORES);

    private static boolean isOre(BlockState st) {
        for (TagKey<Block> t : ORE_TAGS) if (st.isIn(t)) return true;
        return st.isOf(Blocks.ANCIENT_DEBRIS) || st.isOf(Blocks.NETHER_GOLD_ORE) || st.isOf(Blocks.NETHER_QUARTZ_ORE);
    }

    /** Гравець і всі гравці в радіусі (спостерігачі не рахуються). */
    private static List<ServerPlayerEntity> allies(ServerWorld w, ServerPlayerEntity p, double r) {
        return w.getPlayers(x -> !x.isSpectator() && x.squaredDistanceTo(p) <= r * r);
    }

    private static void cleanse(ServerPlayerEntity a) {
        for (StatusEffectInstance e : new ArrayList<>(a.getStatusEffects())) {
            if (e.getEffectType().getCategory() == StatusEffectCategory.HARMFUL) a.removeStatusEffect(e.getEffectType());
        }
    }

    private static void supportBurst(ServerWorld w, ServerPlayerEntity p, StandData d, double r) {
        DustColorTransitionParticleEffect fx = new DustColorTransitionParticleEffect(rgb(d.color1), rgb(d.color2), 1.4f);
        ring(w, fx, p.getPos(), r * 0.5, 24);
        ring(w, fx, p.getPos(), r, 40);
        w.spawnParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 1.2, p.getZ(), 12, 0.5, 0.7, 0.5, 0.03);
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_POWER_SELECT, 0.8f, 1.4f);
    }

    /** Чуття руди: Поспіх, нічний зір і частинки на всіх рудах навколо (бачить лише власник). */
    private static boolean oreSense(ServerPlayerEntity p, ServerWorld w, StandData d) {
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.HASTE, d.dur(600), 1));
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, d.dur(600), 0));
        int r = (int) Math.min(22, d.radius(8 + d.stage * 2));
        BlockPos c = p.getBlockPos();
        int found = 0;
        for (int dx = -r; dx <= r; dx++)
            for (int dy = -r; dy <= r; dy++)
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = c.add(dx, dy, dz);
                    if (!isOre(w.getBlockState(pos))) continue;
                    found++;
                    if (found <= 80) w.spawnParticles(p, ParticleTypes.END_ROD, true, pos.getX() + 0.5, pos.getY() + 0.5,
                            pos.getZ() + 0.5, 4, 0.3, 0.3, 0.3, 0.02);
                }
        p.sendMessage(Text.translatable("msg.standgen.ores_found", found).formatted(Formatting.AQUA), true);
        supportBurst(w, p, d, d.radius(4));
        return true;
    }

    /** Розвідка: підсвічує ворогів навколо й дає нічний зір. */
    private static boolean scout(ServerPlayerEntity p, ServerWorld w, StandData d) {
        double r = 20 + d.range * 0.2;
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r),
                x -> x instanceof Monster && x.isAlive() && x.squaredDistanceTo(p) <= r * r)) {
            e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, d.dur(200), 0));
        }
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.NIGHT_VISION, d.dur(400), 0));
        supportBurst(w, p, d, d.radius(4));
        return true;
    }

    // ---------------- виконання ----------------
    /** @return true якщо здібність спрацювала (тоді йде перезарядка) */
    public static boolean execute(Skill s, ServerPlayerEntity p, StandEntity stand, StandData d) {
        boolean ok = run(s, p, stand, d);
        // поза стенда під час здібності (метання сама керує своєю анімацією)
        if (ok && stand != null && s != Skill.THROW && s != Skill.BARRAGE && s != Skill.FURY) stand.playAnim(s);
        return ok;
    }

    private static boolean run(Skill s, ServerPlayerEntity p, StandEntity stand, StandData d) {
        ServerWorld w = (ServerWorld) p.getWorld();
        switch (s) {
            case EXCAVATE -> { return excavate(p, w, d); }
            case BARRAGE -> {
                StandRuntime.startBarrage(p, stand, 40 + d.stage * 5);
                return true;
            }
            case BASTION -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, d.dur(400), Math.min(3, 1 + d.stage / 3) + d.ampBonus()));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, d.dur(200), 1));
                w.spawnParticles(ParticleTypes.ENCHANT, p.getX(), p.getY() + 1, p.getZ(), 40, 0.6, 0.8, 0.6, 0.2);
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_ANVIL_PLACE, 0.8f, 1.3f);
                return true;
            }
            case DASH -> { return dash(p, w, d); }
            case PIERCE -> { return pierce(p, w, d); }
            case RESURGENCE -> {
                p.heal(d.heal(4 + d.stage));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, d.dur(100), 1));
                w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.2, p.getZ(), 10, 0.5, 0.5, 0.5, 0.1);
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TOTEM_USE, 0.5f, 1.5f);
                return true;
            }
            case GRAB -> { return grab(p, w, d); }
            case WALL -> { return false; }   // це перемикач: обробляється в StandRuntime.toggleStance
            // ---- форма Підтримка: нічого не б'ють, баффають гравця й союзників поруч ----
            case PROSPECT -> { return oreSense(p, w, d); }
            case RALLY -> {
                for (ServerPlayerEntity a : allies(w, p, d.radius(10))) {
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, d.dur(400), 0));
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, d.dur(400), 0));
                }
                supportBurst(w, p, d, d.radius(5));
                return true;
            }
            case FORTIFY -> {
                for (ServerPlayerEntity a : allies(w, p, d.radius(8))) {
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, d.dur(400), Math.min(2, 1 + d.stage / 4) + d.ampBonus()));
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, d.dur(200), 0));
                }
                supportBurst(w, p, d, d.radius(4));
                return true;
            }
            case TAILWIND -> {
                for (ServerPlayerEntity a : allies(w, p, d.radius(10))) {
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, d.dur(400), 1));
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, d.dur(400), 1));
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, d.dur(400), 0));
                }
                supportBurst(w, p, d, d.radius(5));
                return true;
            }
            case SCOUT -> { return scout(p, w, d); }
            case MEND -> {
                for (ServerPlayerEntity a : allies(w, p, d.radius(8))) {
                    a.heal(d.heal(4 + d.stage));
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, d.dur(100), 0));
                }
                supportBurst(w, p, d, d.radius(4));
                w.spawnParticles(ParticleTypes.HEART, p.getX(), p.getY() + 1.4, p.getZ(), 8, 0.6, 0.5, 0.6, 0.1);
                return true;
            }
            case PURIFY -> {
                for (ServerPlayerEntity a : allies(w, p, d.radius(8))) {
                    cleanse(a);
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, d.dur(100), 0));
                }
                supportBurst(w, p, d, d.radius(4));
                return true;
            }
            case SANCTUARY -> {
                for (ServerPlayerEntity a : allies(w, p, d.radius(12))) {
                    cleanse(a);
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, d.dur(300), 1));
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, d.dur(300), 1));
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, d.dur(300), 1));
                }
                supportBurst(w, p, d, d.radius(12));
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1f, 1.2f);
                return true;
            }
            case FLAME_LANCE, TIDAL_JET, FROST_BOLT, SAND_BLAST -> { return elementBolt(p, w, stand, d); }
            case INFERNO, DELUGE, BLIZZARD, SANDSTORM -> {
                ElementZone.start(p, w, d, s);
                return true;
            }
            case SHIFT -> { return StandRuntime.shiftForm(p, d); }
            case POUNCE, SCENT, HOWL, MAUL, ROAR, HIBERNATE, TALON_DIVE, UPDRAFT, GALE, TRAMPLE, KICK, GALLOP -> {
                return beastSkill(s, p, w, d);
            }
            case THROW -> { return thrust(p, w, stand, d); }
            case BURST -> { return burst(p, w, d); }
            case QUAKE -> { return quake(p, w, d); }
            case FURY -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, d.dur(300), 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, d.dur(300), 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, d.dur(300), 0));
                StandRuntime.startBarrage(p, stand, d.dur(100));
                w.spawnParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 1, p.getZ(), 40, 0.5, 0.8, 0.5, 0.05);
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.4f);
                return true;
            }
            case CITADEL -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, d.dur(600), 2));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, d.dur(200), 0));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, d.dur(200), 0));
                for (ServerPlayerEntity o : w.getPlayers(x -> x != p && x.squaredDistanceTo(p) < d.radius(8) * d.radius(8))) {
                    o.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, d.dur(600), 1));
                    o.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, d.dur(200), 0));
                }
                ring(w, ParticleTypes.ENCHANT, p.getPos(), 3, 40);
                sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.BLOCK_BEACON_ACTIVATE, 1f, 1f);
                return true;
            }
            case TIMESTOP -> { return timestop(p, w, d); }
            case ARROW_RAIN -> { return arrowRain(p, w, d); }
            case SECOND_CHANCE -> {
                StandRuntime.armRevive(p, d.dur(600));
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
        double max = Math.min(10.0, 2.0 + d.speed * 0.07);   // дальність залежить від швидкості стенда
        Vec3d last = start;
        for (double t = 0.5; t <= max; t += 0.5) {
            Vec3d next = start.add(look.multiply(t));
            Box box = p.getBoundingBox().offset(next.subtract(start));
            if (!w.isSpaceEmpty(p, box)) break;
            last = next;
        }
        if (last.squaredDistanceTo(start) < 0.8) {
            fail(p, "msg.standgen.fail_no_room");
            return false;
        }
        dashTrail(w, start, last, d);
        p.requestTeleport(last.x, last.y, last.z);
        p.fallDistance = 0;
        p.setVelocity(look.multiply(0.6));          // інерція: гравець не зупиняється різко
        p.velocityModified = true;
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, d.dur(100), 1 + d.stage / 3));
        sound(w, start.x, start.y, start.z, SoundEvents.ITEM_TRIDENT_RIPTIDE_1, 0.9f, 1.3f);
        return true;
    }

    private static Vector3f rgb(int c) {
        return new Vector3f((c >> 16 & 255) / 255f, (c >> 8 & 255) / 255f, (c & 255) / 255f);
    }

    /** Слід ривка: частинки кольорів стенда вздовж усього пройденого шляху, спалахи на початку й у кінці. */
    private static void dashTrail(ServerWorld w, Vec3d from, Vec3d to, StandData d) {
        DustColorTransitionParticleEffect fx = new DustColorTransitionParticleEffect(rgb(d.color1), rgb(d.color2), 1.6f);
        DustParticleEffect core = new DustParticleEffect(rgb(d.color1), 2.2f);
        double len = from.distanceTo(to);
        int n = Math.max(2, (int) Math.ceil(len / 0.4));
        for (int i = 0; i <= n; i++) {
            Vec3d pt = from.lerp(to, i / (double) n);
            w.spawnParticles(fx, pt.x, pt.y + 0.9, pt.z, 3, 0.2, 0.5, 0.2, 0.0);
            w.spawnParticles(core, pt.x, pt.y + 0.9, pt.z, 1, 0.05, 0.3, 0.05, 0.0);
            if (i % 2 == 0) w.spawnParticles(ParticleTypes.END_ROD, pt.x, pt.y + 0.9, pt.z, 1, 0.1, 0.4, 0.1, 0.02);
            if (i % 4 == 0) w.spawnParticles(ParticleTypes.POOF, pt.x, pt.y + 0.1, pt.z, 1, 0.1, 0.0, 0.1, 0.01);
        }
        w.spawnParticles(fx, from.x, from.y + 1, from.z, 25, 0.35, 0.6, 0.35, 0.0);   // залишковий образ на старті
        w.spawnParticles(fx, to.x, to.y + 1, to.z, 15, 0.3, 0.5, 0.3, 0.0);           // спалах у точці прибуття
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

    // ================= тварини =================

    /** Цілі в конусі перед гравцем. */
    private static List<LivingEntity> cone(ServerWorld w, ServerPlayerEntity p, double range, double minDot) {
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(range), x -> validTarget(x, p))) {
            Vec3d to = e.getPos().add(0, e.getHeight() * 0.5, 0).subtract(eye);
            double len = to.length();
            if (len > range || len < 1.0E-3) continue;
            if (to.normalize().dotProduct(look) >= minDot) out.add(e);
        }
        return out;
    }

    /** Усі цілі в смузі вздовж погляду гравця (кожна один раз). */
    private static List<LivingEntity> lane(ServerWorld w, ServerPlayerEntity p, double len, double radius) {
        Vec3d from = p.getPos().add(0, p.getHeight() * 0.5, 0);
        Vec3d dir = p.getRotationVec(1f);
        List<LivingEntity> out = new ArrayList<>();
        for (double t = 0.5; t <= len; t += 0.5) {
            Vec3d pt = from.add(dir.multiply(t));
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(pt, pt).expand(radius), x -> validTarget(x, p))) {
                if (!out.contains(e)) out.add(e);
            }
        }
        return out;
    }

    private static void hurt(ServerWorld w, ServerPlayerEntity p, LivingEntity e, float dmg) {
        e.timeUntilRegen = 0;
        e.damage(w.getDamageSources().playerAttack(p), dmg);
    }

    private static boolean beastSkill(Skill s, ServerPlayerEntity p, ServerWorld w, StandData d) {
        Vec3d look = p.getRotationVec(1f);
        Vec3d flat = new Vec3d(look.x, 0, look.z);
        flat = flat.lengthSquared() < 1.0E-4 ? new Vec3d(0, 0, 1) : flat.normalize();
        float dmg = 4 + d.power * 0.08f;
        Vec3d at = p.getPos();
        switch (s) {
            case POUNCE -> {   // собака: стрибок на ціль і укус
                LivingEntity t = lookedEntity(w, p, 12);
                if (t == null) t = nearestHostile(w, p, 12);
                Vec3d leap = t != null ? t.getPos().subtract(at).multiply(1, 0, 1) : flat;
                leap = leap.lengthSquared() < 1.0E-4 ? flat : leap.normalize();
                AnimalRide.launch(p, new Vec3d(leap.x * 1.3, 0.45, leap.z * 1.3));
                if (t != null) {
                    hurt(w, p, t, dmg);
                    t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 40, 1));
                    StandEntity gs = StandRuntime.getStand(p);
                    chain(w, gs != null ? gs.getPos().add(0, 0.6, 0) : p.getEyePos(), t.getPos().add(0, t.getHeight() * 0.5, 0), d);
                    w.spawnParticles(ParticleTypes.CRIT, t.getX(), t.getBodyY(0.5), t.getZ(), 14, 0.3, 0.3, 0.3, 0.2);
                }
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_WOLF_GROWL, 1f, 1.1f);
            }
            case SCENT -> {    // собака: підсвічує всіх ворогів довкола, власник швидшає
                int n = 0;
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(28), x -> x instanceof Monster && x.isAlive())) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, d.dur(240), 0, false, false));
                    n++;
                }
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, d.dur(240), 0));
                ring(w, ParticleTypes.POOF, at, 3, 24);
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_FOX_SNIFF, 1f, 0.8f);
            }
            case HOWL -> {     // собака: виття зграї, союзники сильніші й швидші, вороги лякаються
                double r = 14;
                for (ServerPlayerEntity a : allies(w, p, r)) {
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.STRENGTH, d.dur(240), 0));
                    a.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, d.dur(240), 0));
                }
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r), x -> validTarget(x, p))) {
                    if (e.squaredDistanceTo(p) > r * r || e instanceof PlayerEntity) continue;
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 140, 0));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 0));
                    if (e instanceof MobEntity mob) mob.setTarget(null);
                }
                StandFxEntity.spawnPulse(w, at, d.color1, d.color2, (float) r, 18);
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_WOLF_HOWL, 1.5f, 0.9f);
            }
            case MAUL -> {     // ведмідь: важкий удар лапою по конусу
                List<LivingEntity> hit = cone(w, p, 4.5, 0.45);
                if (hit.isEmpty()) return false;
                for (LivingEntity e : hit) {
                    hurt(w, p, e, 6 + d.power * 0.12f);
                    e.takeKnockback(1.3, -look.x, -look.z);
                    e.addVelocity(0, 0.25, 0);
                    e.velocityModified = true;
                    w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.5), e.getZ(), 12, 0.3, 0.4, 0.3, 0.2);
                }
                Vec3d mid = p.getEyePos().add(look.multiply(2.2));
                w.spawnParticles(ParticleTypes.SWEEP_ATTACK, mid.x, mid.y - 0.3, mid.z, 3, 0.6, 0.2, 0.6, 0.0);
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_RAVAGER_ATTACK, 1f, 0.8f);
            }
            case ROAR -> {     // ведмідь: рев відкидає й послаблює ворогів, власник отримує щит
                double r = 9;
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r), x -> validTarget(x, p))) {
                    if (e.squaredDistanceTo(p) > r * r) continue;
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.WEAKNESS, 160, 0));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 100, 1));
                    e.takeKnockback(1.0, p.getX() - e.getX(), p.getZ() - e.getZ());
                    if (e instanceof MobEntity mob) mob.setTarget(null);
                }
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, Math.min(3, d.stage / 2)));
                StandFxEntity.spawnPulse(w, at, d.color1, d.color2, (float) r, 16);
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_RAVAGER_ROAR, 1.5f, 0.8f);
            }
            case HIBERNATE -> {   // ведмідь: зимова сплячка, міцний захист і відновлення, але повільний
                int dur = d.dur(140);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, 2));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, dur, 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, dur, 0));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, dur, 0));
                ring(w, ParticleTypes.HEART, at, 1.5, 16);
                StandFxEntity.spawnPulse(w, at, d.color1, d.color2, 4f, 14);
                sound(w, at.x, at.y, at.z, SoundEvents.BLOCK_BEACON_POWER_SELECT, 1f, 0.6f);
            }
            case TALON_DIVE -> {  // орел: стрімкий налет уздовж погляду
                AnimalRide.launch(p, look.multiply(1.5).add(0, 0.2, 0));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 60, 0, true, false));
                for (LivingEntity e : lane(w, p, 9, 1.3)) {
                    hurt(w, p, e, dmg);
                    e.takeKnockback(0.8, -look.x, -look.z);
                    w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.5), e.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                }
                for (double t = 0.5; t <= 9; t += 0.5) {
                    Vec3d pt = p.getEyePos().add(look.multiply(t));
                    w.spawnParticles(ParticleTypes.CLOUD, pt.x, pt.y, pt.z, 1, 0.05, 0.05, 0.05, 0.0);
                }
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_PHANTOM_SWOOP, 1f, 1.2f);
            }
            case UPDRAFT -> {     // орел: висхідний потік підкидає власника вгору, ворогів поруч теж
                AnimalRide.launch(p, new Vec3d(look.x * 0.3, 1.25, look.z * 0.3));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOW_FALLING, 200, 0, true, false));
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(4), x -> validTarget(x, p))) {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.LEVITATION, 20, 1));
                }
                ring(w, ParticleTypes.CLOUD, at, 3, 28);
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_PHANTOM_FLAP, 1.2f, 0.8f);
            }
            case GALE -> {        // орел: буревій відкидає всіх навколо, власник швидшає
                double r = 11;
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, p.getBoundingBox().expand(r), x -> validTarget(x, p))) {
                    if (e.squaredDistanceTo(p) > r * r) continue;
                    Vec3d away = e.getPos().subtract(at).multiply(1, 0, 1);
                    away = away.lengthSquared() < 1.0E-4 ? flat : away.normalize();
                    hurt(w, p, e, 2 + d.power * 0.04f);
                    e.setVelocity(away.x * 1.8, 0.7, away.z * 1.8);
                    e.velocityModified = true;
                }
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, d.dur(160), 1));
                StandFxEntity.spawnPulse(w, at, d.color1, d.color2, (float) r, 18);
                ring(w, ParticleTypes.CLOUD, at, r * 0.6, 40);
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 1.0f);
            }
            case TRAMPLE -> {     // кінь: розгін і затоптування всього на шляху
                AnimalRide.launch(p, new Vec3d(flat.x * 1.6, 0.15, flat.z * 1.6));
                for (LivingEntity e : lane(w, p, 7, 1.5)) {
                    hurt(w, p, e, dmg);
                    e.takeKnockback(1.0, -flat.x, -flat.z);
                    w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.5), e.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                }
                ring(w, ParticleTypes.POOF, at, 1.2, 14);
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_HORSE_GALLOP, 1.2f, 0.9f);
            }
            case KICK -> {        // кінь: короткий сильний копняк, що оглушає
                List<LivingEntity> hit = cone(w, p, 3.4, 0.3);
                if (hit.isEmpty()) return false;
                for (LivingEntity e : hit) {
                    hurt(w, p, e, 3 + d.power * 0.06f);
                    e.takeKnockback(2.2, -look.x, -look.z);
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 5));
                    w.spawnParticles(ParticleTypes.CRIT, e.getX(), e.getBodyY(0.5), e.getZ(), 10, 0.3, 0.3, 0.3, 0.2);
                }
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_HORSE_ANGRY, 1f, 1.0f);
            }
            default -> {          // GALLOP, кінь: галоп, швидкість, стрибок і захист
                int dur = d.dur(160);
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, 2));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, dur, 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, dur, 0));
                ring(w, ParticleTypes.POOF, at, 1.5, 20);
                StandFxEntity.spawnPulse(w, at, d.color1, d.color2, 5f, 12);
                sound(w, at.x, at.y, at.z, SoundEvents.ENTITY_HORSE_GALLOP, 1.5f, 1.1f);
            }
        }
        return true;
    }

    /** Особливий ефект звичайного удару тварини. */
    public static void beastHit(ServerWorld w, ServerPlayerEntity owner, StandData d, LivingEntity t, boolean barrage) {
        switch (d.animal) {
            case BEAR -> {      // розмашистий удар зачіпає сусідів
                for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, t.getBoundingBox().expand(2.2),
                        x -> x != t && validTarget(x, owner))) {
                    hurt(w, owner, e, d.standDamage() * 0.5f);
                    if (!barrage) e.takeKnockback(0.7, owner.getX() - e.getX(), owner.getZ() - e.getZ());
                }
                if (!barrage) t.takeKnockback(0.7, owner.getX() - t.getX(), owner.getZ() - t.getZ());
            }
            case HORSE -> { if (!barrage) t.takeKnockback(0.9, owner.getX() - t.getX(), owner.getZ() - t.getZ()); }
            case DOG -> t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 20, 0));
            case EAGLE -> t.addStatusEffect(new StatusEffectInstance(StatusEffects.GLOWING, 100, 0, false, false));
            default -> {}
        }
    }

    /** Постійні бонуси власника від тварини (раз на 2 с). */
    private static void animalPassive(ServerPlayerEntity p, StandData d) {
        if (d.form != StandForm.ANIMAL) return;
        switch (d.animal) {
            case DOG -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 80, 0, true, false));
            case BEAR -> p.addStatusEffect(new StatusEffectInstance(StatusEffects.RESISTANCE, 80, 0, true, false));
            case HORSE -> {
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, 80, 0, true, false));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.JUMP_BOOST, 80, 0, true, false));
            }
            default -> {}
        }
    }

    /** Стихія додає невеликий ефект до звичайних ударів стенда й броні. */
    public static void elementHit(LivingEntity t, StandData d) {
        switch (d.element) {
            case FIRE -> t.setOnFireFor(2);
            case WATER -> t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 25, 0));
            case ICE -> {
                t.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0));
                t.setFrozenTicks(Math.min(t.getFrozenTicks() + 25, t.getMinFreezeDamageTicks() + 40));
            }
            case SAND -> t.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 25, 0));
            default -> {}
        }
    }

    /** Промінь стихії (слот 2): летить до прицілу, зупиняється на блоці, діє на кожного ворога раз. */
    private static boolean elementBolt(ServerPlayerEntity p, ServerWorld w, StandEntity stand, StandData d) {
        Element el = d.element;
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        double max = armored(d) ? 6.0 : 12 + d.range * 0.2;
        float dmg = 3 + d.power * 0.07f + d.precision * 0.02f;
        Vec3d from = stand != null ? stand.getPos().add(0, 1.1, 0) : eye.add(look.multiply(0.6)).subtract(0, 0.3, 0);
        HitResult hr = p.raycast(max, 1f, false);
        Vec3d aim = hr.getType() == HitResult.Type.MISS ? eye.add(look.multiply(max)) : hr.getPos();
        Vec3d dir = aim.subtract(from);
        if (dir.lengthSquared() < 1.0E-4) dir = look;
        double len = Math.min(max, dir.length());
        dir = dir.normalize();
        ParticleEffect main = ElementZone.particle(el), second = ElementZone.particle2(el);
        DustColorTransitionParticleEffect line = new DustColorTransitionParticleEffect(rgb(d.color1), rgb(d.color2), 1.1f);
        Set<Entity> hit = new HashSet<>();
        for (double t = 0.5; t <= len; t += 0.5) {
            Vec3d pt = from.add(dir.multiply(t));
            BlockPos bp = BlockPos.ofFloored(pt);
            if (!w.getBlockState(bp).getCollisionShape(w, bp).isEmpty()) {
                w.spawnParticles(main, pt.x, pt.y, pt.z, 14, 0.3, 0.3, 0.3, 0.1);
                break;
            }
            w.spawnParticles(main, pt.x, pt.y, pt.z, 2, 0.08, 0.08, 0.08, 0.01);
            w.spawnParticles(line, pt.x, pt.y, pt.z, 1, 0.02, 0.02, 0.02, 0.0);
            if (((int) (t * 2)) % 3 == 0) w.spawnParticles(second, pt.x, pt.y, pt.z, 1, 0.12, 0.12, 0.12, 0.01);
            for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, new Box(pt, pt).expand(0.7), x -> validTarget(x, p))) {
                if (!hit.add(e)) continue;
                e.timeUntilRegen = 0;
                switch (el) {
                    case FIRE -> {
                        e.damage(w.getDamageSources().playerAttack(p), dmg);
                        e.setOnFireFor(4 + d.stage / 2);
                    }
                    case WATER -> {
                        e.damage(w.getDamageSources().playerAttack(p), dmg * 0.7f);
                        e.extinguish();
                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 60, 1));
                        e.takeKnockback(1.4, -dir.x, -dir.z);
                    }
                    case ICE -> {
                        e.damage(w.getDamageSources().playerAttack(p), dmg);
                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 80, 2));
                        e.setFrozenTicks(e.getMinFreezeDamageTicks() + 60);
                    }
                    default -> {   // SAND
                        e.damage(w.getDamageSources().playerAttack(p), dmg * 0.9f);
                        e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 60, 0));
                        e.takeKnockback(0.9, -dir.x, -dir.z);
                    }
                }
                w.spawnParticles(main, e.getX(), e.getBodyY(0.5), e.getZ(), 16, 0.3, 0.4, 0.3, 0.12);
            }
        }
        sound(w, p.getX(), p.getY(), p.getZ(), ElementZone.sound(el), 1f, 1.1f);
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
        StandEntity gs = StandRuntime.getStand(p);
        chain(w, gs != null ? gs.getPos().add(0, 1.1, 0) : p.getEyePos().subtract(0, 0.3, 0), t.getPos().add(0, t.getHeight() * 0.5, 0), d);
        w.spawnParticles(ParticleTypes.CRIT, t.getX(), t.getBodyY(0.5), t.getZ(), 15, 0.3, 0.5, 0.3, 0.2);
        sound(w, t.getX(), t.getY(), t.getZ(), SoundEvents.ENTITY_PLAYER_ATTACK_KNOCKBACK, 1f, 0.8f);
        return true;
    }

    /** Метання: зброя реально летить уздовж погляду, пробиває ворогів, відбивається від стін і повертається до стенда. */
    private static boolean thrust(ServerPlayerEntity p, ServerWorld w, StandEntity stand, StandData d) {
        double max = 14 + d.range * 0.15;
        float dmg = (5 + d.power * 0.07f) * 0.8f;          // вражає при польоті вперед і назад
        Vec3d eye = p.getEyePos();
        Vec3d look = p.getRotationVec(1f);
        Vec3d from = stand != null ? stand.getPos().add(0, 0.9, 0) : eye.add(look.multiply(0.8)).subtract(0, 0.3, 0);
        // ціль: точка, куди дивиться гравець (до першого блока), щоб влучати в приціл
        HitResult hr = p.raycast(max, 1f, false);
        Vec3d aim = hr.getType() == HitResult.Type.MISS ? eye.add(look.multiply(max)) : hr.getPos();
        Vec3d dir = aim.subtract(from);
        if (dir.lengthSquared() < 1.0E-4) dir = look;
        float size = 0.9f + d.stage * 0.04f;
        StandFxEntity.spawnThrown(w, p, d, from, dir, max, dmg, size);
        if (stand != null) stand.playAnim(Skill.THROW, 200);   // зброя покинула стенд, поки летить
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ITEM_TRIDENT_THROW, 1f, 0.8f);
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
        StandFxEntity.spawnPulse(w, p.getPos(), d.color1, d.color2, (float) r, 14);
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
        StandFxEntity.spawnPulse(w, p.getPos(), d.color1, d.color2, (float) r, 18);
        ring(w, ParticleTypes.POOF, p.getPos(), 3, 30);
        ring(w, ParticleTypes.POOF, p.getPos(), 6, 50);
        w.spawnParticles(ParticleTypes.EXPLOSION_EMITTER, p.getX(), p.getY() + 0.5, p.getZ(), 1, 0, 0, 0, 0);
        sound(w, p.getX(), p.getY(), p.getZ(), SoundEvents.ENTITY_GENERIC_EXPLODE, 1f, 0.6f);
        return true;
    }

    /** Зупинка часу: сфера, у якій моби й чужі снаряди по-справжньому завмирають (див. TimeStop). */
    private static boolean timestop(ServerPlayerEntity p, ServerWorld w, StandData d) {
        double r = Math.min(26, 12 + d.range * 0.1);
        int dur = d.dur(100 + d.stage * 10);
        TimeStop.start(p, w, d, r, dur);
        p.addStatusEffect(new StatusEffectInstance(StatusEffects.SPEED, dur, 2));
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
        animalPassive(p, d);
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
