package dev.standgen.stand;

import dev.standgen.entity.StandFxEntity;
import net.minecraft.block.Blocks;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * Ультимейти стихій: поле навколо місця, де гравець активував здібність. Тримається кілька секунд,
 * кожні 10 тіків діє на всіх у радіусі. Блоки світу не змінює.
 */
public final class ElementZone {
    private ElementZone() {}

    private static final class Zone {
        final UUID owner;
        final ServerWorld world;
        final Vec3d center;
        final double radius;
        final int end;
        final Skill skill;
        final float damage;      // шкода за імпульс
        final float heal;        // лікування союзників за імпульс (Потоп)
        final int amp;           // бонус сили ефектів
        Zone(UUID owner, ServerWorld world, Vec3d center, double radius, int end, Skill skill, float damage, float heal, int amp) {
            this.owner = owner; this.world = world; this.center = center; this.radius = radius; this.end = end;
            this.skill = skill; this.damage = damage; this.heal = heal; this.amp = amp;
        }
    }

    private static final Map<UUID, Zone> ZONES = new HashMap<>();

    /** Основна частинка стихії (для променя, поля й навколо стенда). */
    public static ParticleEffect particle(Element e) {
        return switch (e) {
            case FIRE -> ParticleTypes.FLAME;
            case WATER -> ParticleTypes.SPLASH;
            case ICE -> ParticleTypes.SNOWFLAKE;
            case SAND -> new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.SAND.getDefaultState());
            default -> ParticleTypes.END_ROD;
        };
    }

    /** Другорядна частинка (дим, бульбашки, хмарка). */
    public static ParticleEffect particle2(Element e) {
        return switch (e) {
            case FIRE -> ParticleTypes.SMOKE;
            case WATER -> ParticleTypes.BUBBLE;
            case ICE -> ParticleTypes.CLOUD;
            case SAND -> ParticleTypes.POOF;
            default -> ParticleTypes.END_ROD;
        };
    }

    public static SoundEvent sound(Element e) {
        return switch (e) {
            case FIRE -> SoundEvents.ITEM_FIRECHARGE_USE;
            case WATER -> SoundEvents.ENTITY_GENERIC_SPLASH;
            case ICE -> SoundEvents.BLOCK_GLASS_BREAK;
            case SAND -> SoundEvents.BLOCK_SAND_BREAK;
            default -> SoundEvents.ENTITY_ARROW_SHOOT;
        };
    }

    public static void start(ServerPlayerEntity p, ServerWorld w, StandData d, Skill skill) {
        ZONES.remove(p.getUuid());
        double r = Math.min(14, d.radius(8 + d.stage * 0.5));
        int dur = d.dur(140);
        Vec3d c = p.getPos();
        float dmg = 1.5f + d.power * 0.035f;
        ZONES.put(p.getUuid(), new Zone(p.getUuid(), w, c, r, p.getServer().getTicks() + dur, skill, dmg, d.heal(1.0f), d.ampBonus()));
        StandFxEntity.spawnPulse(w, c, d.color1, d.color2, (float) r, 16);
        w.playSound(null, c.x, c.y, c.z, sound(d.element), SoundCategory.PLAYERS, 1.2f, 0.7f);
        w.playSound(null, c.x, c.y, c.z, SoundEvents.BLOCK_BEACON_ACTIVATE, SoundCategory.PLAYERS, 0.8f, 0.7f);
        // стихійний імунітет власника на час поля
        if (skill == Skill.INFERNO) p.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, dur + 20, 0, false, false));
        if (skill == Skill.DELUGE) {
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.WATER_BREATHING, dur + 20, 0, false, false));
            p.addStatusEffect(new StatusEffectInstance(StatusEffects.DOLPHINS_GRACE, dur + 20, 0, false, false));
        }
    }

    private static Element elementOf(Skill s) {
        return switch (s) {
            case INFERNO -> Element.FIRE;
            case DELUGE -> Element.WATER;
            case BLIZZARD -> Element.ICE;
            default -> Element.SAND;
        };
    }

    public static void tick(MinecraftServer server) {
        if (ZONES.isEmpty()) return;
        int now = server.getTicks();
        Iterator<Zone> it = ZONES.values().iterator();
        while (it.hasNext()) {
            Zone z = it.next();
            ServerPlayerEntity owner = server.getPlayerManager().getPlayer(z.owner);
            if (now >= z.end || owner == null || !owner.isAlive() || owner.getWorld() != z.world) {
                it.remove();
                continue;
            }
            visuals(z, now);
            if (now % 10 == 0) pulse(z, owner);
        }
    }

    public static void endAll() { ZONES.clear(); }

    private static void visuals(Zone z, int now) {
        Element e = elementOf(z.skill);
        ServerWorld w = z.world;
        ParticleEffect main = particle(e), second = particle2(e);
        java.util.Random rnd = new java.util.Random(now * 31L + z.owner.hashCode());
        for (int i = 0; i < 8; i++) {
            double a = rnd.nextDouble() * Math.PI * 2;
            double rr = Math.sqrt(rnd.nextDouble()) * z.radius;
            double x = z.center.x + Math.cos(a) * rr, zz = z.center.z + Math.sin(a) * rr;
            double y = z.center.y + 0.1 + rnd.nextDouble() * 3.0;
            w.spawnParticles(main, x, y, zz, 1, 0.1, 0.2, 0.1, 0.02);
            if (i % 2 == 0) w.spawnParticles(second, x, y, zz, 1, 0.1, 0.2, 0.1, 0.01);
        }
        // кільце по краю поля, що обертається
        if (now % 2 == 0) {
            for (int i = 0; i < 24; i++) {
                double a = i * Math.PI * 2 / 24 + now * 0.08;
                w.spawnParticles(main, z.center.x + Math.cos(a) * z.radius, z.center.y + 0.2 + (i % 3) * 0.5,
                        z.center.z + Math.sin(a) * z.radius, 1, 0, 0, 0, 0.0);
            }
        }
        if (z.skill == Skill.DELUGE) {
            for (int i = 0; i < 4; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, rr = Math.sqrt(rnd.nextDouble()) * z.radius;
                w.spawnParticles(ParticleTypes.FALLING_WATER, z.center.x + Math.cos(a) * rr, z.center.y + 4.5,
                        z.center.z + Math.sin(a) * rr, 2, 0.2, 0.1, 0.2, 0.0);
            }
        }
    }

    private static void pulse(Zone z, ServerPlayerEntity owner) {
        ServerWorld w = z.world;
        double r = z.radius;
        Box box = new Box(z.center.x - r, z.center.y - 6, z.center.z - r, z.center.x + r, z.center.y + 6, z.center.z + r);
        // стійкі ефекти власника
        if (z.skill == Skill.INFERNO) owner.addStatusEffect(new StatusEffectInstance(StatusEffects.FIRE_RESISTANCE, 40, 0, false, false));
        if (z.skill == Skill.DELUGE) {
            for (ServerPlayerEntity a : w.getPlayers(x -> x.isAlive() && !x.isSpectator())) {
                if (a.squaredDistanceTo(z.center) > r * r) continue;
                a.heal(z.heal);
                a.extinguish();
            }
        }
        for (LivingEntity e : w.getEntitiesByClass(LivingEntity.class, box, x -> Abilities.validTarget(x, owner))) {
            double dx = e.getX() - z.center.x, dz = e.getZ() - z.center.z;
            if (dx * dx + dz * dz > r * r) continue;
            if (z.skill == Skill.DELUGE && e instanceof ServerPlayerEntity) continue;   // ліквідація вже зцілила гравців
            e.timeUntilRegen = 0;
            switch (z.skill) {
                case INFERNO -> {
                    e.setOnFireFor(4 + z.amp * 2);
                    e.damage(w.getDamageSources().playerAttack(owner), z.damage);
                }
                case DELUGE -> {
                    e.extinguish();
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 1 + z.amp));
                    e.damage(w.getDamageSources().playerAttack(owner), z.damage * 0.5f);
                }
                case BLIZZARD -> {
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 2 + z.amp));
                    e.setFrozenTicks(e.getMinFreezeDamageTicks() + 40);
                    e.damage(w.getDamageSources().playerAttack(owner), z.damage * 0.8f);
                }
                default -> {   // SANDSTORM: засліплює й закручує
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.BLINDNESS, 40, 0));
                    e.addStatusEffect(new StatusEffectInstance(StatusEffects.SLOWNESS, 30, 0 + z.amp));
                    e.damage(w.getDamageSources().playerAttack(owner), z.damage * 0.9f);
                    double len = Math.max(0.5, Math.sqrt(dx * dx + dz * dz));
                    e.addVelocity(-dz / len * 0.25, 0.12, dx / len * 0.25);
                    e.velocityModified = true;
                }
            }
        }
    }
}
