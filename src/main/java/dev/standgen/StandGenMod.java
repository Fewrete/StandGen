package dev.standgen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.standgen.entity.StandEntity;
import dev.standgen.item.StandArmorItem;
import dev.standgen.item.StandArmorMaterial;
import dev.standgen.item.StandArrowItem;
import dev.standgen.stand.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.fabricmc.fabric.api.loot.v2.LootTableEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.item.ArmorItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.loot.LootPool;
import net.minecraft.loot.condition.RandomChanceLootCondition;
import net.minecraft.loot.entry.ItemEntry;
import net.minecraft.loot.provider.number.ConstantLootNumberProvider;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.tag.DamageTypeTags;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

import java.util.HashMap;
import java.util.Map;

public class StandGenMod implements ModInitializer {
    public static final String MOD_ID = "standgen";
    public static final Identifier ACTION = new Identifier(MOD_ID, "action");

    /** Коди дій з клієнта. */
    public static final int ACT_SUMMON = 0, ACT_SKILL1 = 1, ACT_SKILL2 = 2, ACT_SKILL3 = 3, ACT_SKILL4 = 4,
            ACT_MODE = 5, ACT_HOLD_ON = 6, ACT_HOLD_OFF = 7;

    public static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    public static final Item STAND_ARROW = Registry.register(Registries.ITEM, id("stand_arrow"),
            new StandArrowItem(new FabricItemSettings().maxCount(16).rarity(Rarity.RARE)));

    public static final Item STAND_HELMET = Registry.register(Registries.ITEM, id("stand_helmet"),
            new StandArmorItem(StandArmorMaterial.INSTANCE, ArmorItem.Type.HELMET, new FabricItemSettings().maxCount(1).fireproof()));
    public static final Item STAND_CHESTPLATE = Registry.register(Registries.ITEM, id("stand_chestplate"),
            new StandArmorItem(StandArmorMaterial.INSTANCE, ArmorItem.Type.CHESTPLATE, new FabricItemSettings().maxCount(1).fireproof()));
    public static final Item STAND_LEGGINGS = Registry.register(Registries.ITEM, id("stand_leggings"),
            new StandArmorItem(StandArmorMaterial.INSTANCE, ArmorItem.Type.LEGGINGS, new FabricItemSettings().maxCount(1).fireproof()));
    public static final Item STAND_BOOTS = Registry.register(Registries.ITEM, id("stand_boots"),
            new StandArmorItem(StandArmorMaterial.INSTANCE, ArmorItem.Type.BOOTS, new FabricItemSettings().maxCount(1).fireproof()));

    public static final EntityType<StandEntity> STAND = Registry.register(Registries.ENTITY_TYPE, id("stand"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, StandEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build());

    /** Де ще можна знайти Стрілу Стенда (шанс 0..1 на таблицю). */
    private static final Map<Identifier, Float> LOOT = new HashMap<>();
    static {
        LOOT.put(new Identifier("chests/desert_pyramid"), 0.30f);
        LOOT.put(new Identifier("chests/jungle_temple"), 0.30f);
        LOOT.put(new Identifier("chests/pillager_outpost"), 0.30f);
        LOOT.put(new Identifier("chests/shipwreck_treasure"), 0.25f);
        LOOT.put(new Identifier("chests/buried_treasure"), 0.25f);
        LOOT.put(new Identifier("chests/woodland_mansion"), 0.50f);
        LOOT.put(new Identifier("chests/stronghold_library"), 0.35f);
        LOOT.put(new Identifier("chests/village/village_temple"), 0.15f);
        LOOT.put(new Identifier("chests/ruined_portal"), 0.20f);
        LOOT.put(new Identifier("chests/ancient_city"), 0.40f);
        LOOT.put(new Identifier("chests/bastion_treasure"), 0.40f);
        LOOT.put(new Identifier("chests/end_city_treasure"), 0.50f);
        LOOT.put(new Identifier("gameplay/fishing/treasure"), 0.04f);   // риболови теж мають шанс
        LOOT.put(new Identifier("entities/evoker"), 0.25f);
        LOOT.put(new Identifier("entities/piglin_brute"), 0.10f);
        LOOT.put(new Identifier("entities/elder_guardian"), 0.50f);
        LOOT.put(new Identifier("entities/wither_skeleton"), 0.02f);
    }

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(STAND, StandEntity.createStandAttributes());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(STAND_ARROW));

        // ---- інші способи отримати стрілу: скрині, моби, риболовля ----
        LootTableEvents.MODIFY.register((resourceManager, lootManager, id, tableBuilder, source) -> {
            Float chance = LOOT.get(id);
            if (chance != null && source.isBuiltin()) {
                tableBuilder.pool(LootPool.builder()
                        .rolls(ConstantLootNumberProvider.create(1))
                        .conditionally(RandomChanceLootCondition.builder(chance))
                        .with(ItemEntry.builder(STAND_ARROW)));
            }
        });

        // ---- клавіші з клієнта ----
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, sender) -> {
            int action = buf.readInt();
            server.execute(() -> {
                switch (action) {
                    case ACT_SUMMON -> StandRuntime.toggle(player);
                    case ACT_SKILL1 -> StandRuntime.useAbility(player, 0);
                    case ACT_SKILL2 -> StandRuntime.useAbility(player, 1);
                    case ACT_SKILL3 -> StandRuntime.useAbility(player, 2);
                    case ACT_SKILL4 -> StandRuntime.useAbility(player, 3);
                    case ACT_MODE -> StandRuntime.toggleManual(player);
                    case ACT_HOLD_ON -> StandRuntime.setHold(player, true);
                    case ACT_HOLD_OFF -> StandRuntime.setHold(player, false);
                    default -> {}
                }
            });
        });

        // Стенд атакує ціль, яку вдарив власник (лише в авто-режимі)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp
                    && entity instanceof LivingEntity le && !(entity instanceof StandEntity)
                    && !StandRuntime.isManual(sp.getUuid())) {
                StandEntity s = StandRuntime.getStand(sp);
                if (s != null) s.setTarget(le);
            }
            return ActionResult.PASS;
        });

        // Стріла Стенда, що влучила в гравця, пробуджує йому стенд
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity target && source.getSource() != null
                    && source.getSource().getCommandTags().contains(StandArrowItem.TAG)
                    && StandManager.get(target.getServer()).get(target.getUuid()) == null) {
                boolean ok = StandGenerator.awakenByArrow(target);
                if (source.getAttacker() instanceof ServerPlayerEntity shooter && shooter != target) {
                    shooter.sendMessage(Text.translatable(ok ? "msg.standgen.shot_awakened" : "msg.standgen.shot_unworthy",
                            target.getName()).formatted(Formatting.LIGHT_PURPLE), true);
                }
                if (!ok) return false;
            }
            return true;
        });

        // "Другий шанс": відбиває смертельну шкоду
        ServerLivingEntityEvents.ALLOW_DEATH.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayerEntity p && !source.isIn(DamageTypeTags.BYPASSES_INVULNERABILITY)
                    && StandRuntime.consumeRevive(p)) {
                p.setHealth(p.getMaxHealth() * 0.5f);
                p.clearStatusEffects();
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.REGENERATION, 200, 1));
                p.addStatusEffect(new StatusEffectInstance(StatusEffects.ABSORPTION, 200, 1));
                p.getWorld().sendEntityStatus(p, (byte) 35);
                p.sendMessage(Text.translatable("msg.standgen.revive_used").formatted(Formatting.GOLD), true);
                return false;
            }
            return true;
        });

        // Еволюція: раз на 5 секунд перевіряємо прогрес
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            StandRuntime.tickArmor(server);
            if (server.getTicks() % 100 != 0) return;
            StandManager m = StandManager.get(server);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                StandData d = m.get(p.getUuid());
                if (d == null) continue;
                int ns = Progression.stage(p);
                if (ns > d.stage) {
                    d.stage = ns;
                    d.recalc();
                    m.markDirty();
                    StandRuntime.refreshArmor(p, d);
                    p.sendMessage(Text.translatable("msg.standgen.evolved", ns, Progression.stageText(ns))
                            .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
                    for (int slot = 1; slot < 4; slot++) {
                        if (d.unlockStage(slot) == ns) {
                            p.sendMessage(Text.translatable("msg.standgen.skill_unlocked", d.skill(slot).text())
                                    .formatted(Formatting.GREEN));
                        }
                    }
                }
            }
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { StandRuntime.dismiss(handler.player); StandRuntime.forget(handler.player.getUuid()); });
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> StandRuntime.clear());

        // ---- команди ----
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("stand")
                        .then(CommandManager.literal("info").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            StandData d = StandManager.get(ctx.getSource().getServer()).get(p.getUuid());
                            if (d == null) {
                                ctx.getSource().sendFeedback(() -> Text.translatable("msg.standgen.no_stand"), false);
                                return 0;
                            }
                            for (Text t : d.describe()) ctx.getSource().sendFeedback(() -> t, false);
                            ctx.getSource().sendFeedback(d::scoreLine, false);
                            return 1;
                        }))
                        .then(CommandManager.literal("awaken").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            StandRuntime.dismiss(p);
                            StandGenerator.awaken(p);
                            return 1;
                        }))
                        .then(CommandManager.literal("reset").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            StandRuntime.dismiss(p);
                            StandManager.get(ctx.getSource().getServer()).remove(p.getUuid());
                            ctx.getSource().sendFeedback(() -> Text.translatable("msg.standgen.reset"), false);
                            return 1;
                        }))
                        // для тестування: /stand stage 0..8 (реальний прогрес може підняти стадію назад)
                        .then(CommandManager.literal("stage").requires(src -> src.hasPermissionLevel(2))
                                .then(CommandManager.argument("n", IntegerArgumentType.integer(0, 8)).executes(ctx -> {
                                    ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                                    StandManager m = StandManager.get(ctx.getSource().getServer());
                                    StandData d = m.get(p.getUuid());
                                    if (d == null) {
                                        ctx.getSource().sendFeedback(() -> Text.translatable("msg.standgen.no_stand"), false);
                                        return 0;
                                    }
                                    d.stage = IntegerArgumentType.getInteger(ctx, "n");
                                    d.recalc();
                                    m.markDirty();
                                    StandRuntime.refreshArmor(p, d);
                                    ctx.getSource().sendFeedback(() -> Text.translatable("msg.standgen.stage_set", d.stage), false);
                                    return 1;
                                })))));
    }
}
