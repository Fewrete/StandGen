package dev.standgen;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import dev.standgen.entity.StandEntity;
import dev.standgen.entity.StandFxEntity;
import dev.standgen.item.StandArmorItem;
import dev.standgen.item.StandArmorMaterial;
import dev.standgen.item.StandArrowItem;
import dev.standgen.item.StandWeaponItems;
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
import net.fabricmc.fabric.api.networking.v1.EntityTrackingEvents;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
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
import net.minecraft.network.PacketByteBuf;
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
    /** Сервер -> клієнт: чи є в цього гравця стенд (без стенда чужі стенди не рендеряться). */
    public static final Identifier HAS_STAND = new Identifier(MOD_ID, "has_stand");
    /** Сервер -> клієнт: повні дані стенда для меню. */
    public static final Identifier STAND_DATA = new Identifier(MOD_ID, "stand_data");
    /** Сервер -> клієнт: відкрити меню стенда. */
    public static final Identifier OPEN_MENU = new Identifier(MOD_ID, "open_menu");
    /** Клієнт -> сервер: тестове редагування душі (лише креатив або оператор). */
    public static final Identifier EDIT = new Identifier(MOD_ID, "edit");
    /** Сервер -> клієнт: стенд Підтримка на спині цього гравця ввімкнено/вимкнено (+ кольори). */
    public static final Identifier SUPPORT_BACK = new Identifier(MOD_ID, "support_back");

    /** Коди дій з клієнта. */
    public static final int ACT_SUMMON = 0, ACT_SKILL1 = 1, ACT_SKILL2 = 2, ACT_SKILL3 = 3, ACT_SKILL4 = 4,
            ACT_MODE = 5, ACT_HOLD_ON = 6, ACT_HOLD_OFF = 7, ACT_MENU = 8, ACT_MOUNT = 9;

    public static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    public static void sendHasStand(ServerPlayerEntity p, boolean has) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeBoolean(has);
        ServerPlayNetworking.send(p, HAS_STAND, buf);
        sendStandData(p);
    }

    /** Редагувати душу можна лише для тестів: креатив або оператор. */
    public static boolean canEdit(ServerPlayerEntity p) {
        return p.getAbilities().creativeMode || p.hasPermissionLevel(2);
    }

    public static void sendStandData(ServerPlayerEntity p) {
        StandData d = StandManager.get(p.getServer()).get(p.getUuid());
        PacketByteBuf b = PacketByteBufs.create();
        b.writeBoolean(d != null);
        if (d != null) {
            b.writeString(d.name);
            b.writeInt(d.primary.ordinal()); b.writeInt(d.secondary.ordinal()); b.writeInt(d.baseForm().ordinal());
            b.writeInt(d.stage); b.writeInt(d.milestones);
            b.writeInt(d.power); b.writeInt(d.speed); b.writeInt(d.range); b.writeInt(d.durability);
            b.writeInt(d.precision); b.writeInt(d.mastery);
            for (int i = 0; i < Archetype.COUNT; i++) b.writeFloat(d.scores[i]);
            b.writeBoolean(canEdit(p));
            b.writeInt(d.element.id);
            b.writeInt(d.animal.id);
        }
        ServerPlayNetworking.send(p, STAND_DATA, b);
    }

    /** Свіжі дані + відкрити меню на клієнті (клавіша, /stand info, /stand menu). */
    public static void openMenu(ServerPlayerEntity p) {
        if (StandManager.get(p.getServer()).get(p.getUuid()) == null) {
            p.sendMessage(Text.translatable("msg.standgen.no_stand_hint").formatted(Formatting.RED), true);
            return;
        }
        sendStandData(p);
        ServerPlayNetworking.send(p, OPEN_MENU, PacketByteBufs.create());
    }

    private static void applyEdit(ServerPlayerEntity p, int type, int a, float v) {
        if (!canEdit(p)) return;
        StandManager m = StandManager.get(p.getServer());
        StandData d = m.get(p.getUuid());
        if (d == null) return;
        if (type == 0 && a >= 0 && a < Archetype.COUNT) {
            // лише міняє бал (характеристики); тип стенда обирається окремо, інакше великий бал із реальної гри перебивав би вибір
            d.scores[a] = Math.max(0f, Math.min(3f, v));
        } else if ((type == 2 || type == 3) && a >= 0 && a < Archetype.COUNT) {
            // прямий вибір типу: 2 = основний, 3 = додатковий (якщо збігається з іншим, вони міняються місцями)
            Archetype pick = Archetype.values()[a];
            if (type == 2) {
                if (d.secondary == pick) d.secondary = d.primary;
                d.primary = pick;
            } else {
                if (d.primary == pick) d.primary = d.secondary;
                d.secondary = pick;
            }
        } else if (type == 4) {
            // тестовий вибір стихії: 0 = немає (форма Стихія знімається), 1..COUNT (форма Стихія вмикається)
            StandRuntime.dismiss(p);
            d.shift = null;
            d.animal = Animal.NONE;
            d.element = Element.byId(Math.floorMod(a, Element.COUNT + 1));
            if (d.element == Element.NONE) {
                if (d.formId == StandForm.ELEMENTAL.ordinal() || d.formId == StandForm.ANIMAL.ordinal()) d.formId = StandForm.HUMANOID.ordinal();
            } else {
                d.formId = StandForm.ELEMENTAL.ordinal();
            }
        } else if (type == 5) {
            // тестовий вибір тварини: 0 = немає (форма Тварина знімається), 1..COUNT (форма Тварина вмикається)
            StandRuntime.dismiss(p);
            d.shift = null;
            d.element = Element.NONE;
            d.animal = Animal.byId(Math.floorMod(a, Animal.COUNT + 1));
            if (d.animal == Animal.NONE) {
                if (d.formId == StandForm.ELEMENTAL.ordinal() || d.formId == StandForm.ANIMAL.ordinal()) d.formId = StandForm.HUMANOID.ordinal();
            } else {
                d.formId = StandForm.ANIMAL.ordinal();
            }
        } else if (type == 1) {
            StandRuntime.dismiss(p);
            d.shift = null;
            d.formId = Math.floorMod(a, StandForm.values().length);
            // Стихія з формою Стихія, тварина з формою Тварина: йдуть парами
            if (d.formId == StandForm.ELEMENTAL.ordinal()) {
                d.animal = Animal.NONE;
                if (d.element == Element.NONE) d.element = Element.FIRE;
            } else if (d.formId == StandForm.ANIMAL.ordinal()) {
                d.element = Element.NONE;
                if (d.animal == Animal.NONE) d.animal = Animal.DOG;
            } else {
                d.element = Element.NONE;
                d.animal = Animal.NONE;
            }
        } else {
            return;
        }
        d.recalc();
        m.markDirty();
        StandRuntime.refreshItems(p, d);
        sendStandData(p);
    }

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

    public static final Item STAND_PICKAXE = Registry.register(Registries.ITEM, id("stand_pickaxe"),
            new StandWeaponItems.Pickaxe(new FabricItemSettings().maxCount(1).fireproof()));
    public static final Item STAND_SWORD = Registry.register(Registries.ITEM, id("stand_sword"),
            new StandWeaponItems.Sword(new FabricItemSettings().maxCount(1).fireproof()));
    public static final Item STAND_HAMMER = Registry.register(Registries.ITEM, id("stand_hammer"),
            new StandWeaponItems.Hammer(new FabricItemSettings().maxCount(1).fireproof()));
    public static final Item STAND_SPEAR = Registry.register(Registries.ITEM, id("stand_spear"),
            new StandWeaponItems.Spear(new FabricItemSettings().maxCount(1).fireproof()));
    public static final Item STAND_BOW = Registry.register(Registries.ITEM, id("stand_bow"),
            new StandWeaponItems.Bow(new FabricItemSettings().maxCount(1).fireproof()));

    public static final EntityType<StandEntity> STAND = Registry.register(Registries.ENTITY_TYPE, id("stand"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, StandEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build());

    /** Ефекти здібностей: метальна зброя, сфера зупинки часу, ударна хвиля. */
    public static final EntityType<StandFxEntity> FX = Registry.register(Registries.ENTITY_TYPE, id("fx"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, StandFxEntity::new)
                    .dimensions(EntityDimensions.fixed(0.5f, 0.5f))
                    .trackRangeBlocks(96)
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
                    case ACT_MENU -> openMenu(player);
                    case ACT_MOUNT -> AnimalRide.mount(player);
                    default -> {}
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(EDIT, (server, player, handler, buf, sender) -> {
            int type = buf.readInt(), a = buf.readInt();
            float v = buf.readFloat();
            server.execute(() -> applyEdit(player, type, a, v));
        });

        // Стенд атакує ціль, яку вдарив власник (лише в авто-режимі)
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity cs && entity instanceof LivingEntity ce) {
                StandRuntime.weaponCrit(cs, ce, hand);   // криті зброї в руках
            }
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

        ElementSignals.register();
        AnimalRide.register();
        AnimalSignals.register();

        // Еволюція: раз на 5 секунд перевіряємо прогрес
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            StandRuntime.tickArmor(server);
            TimeStop.tick(server);
            ElementZone.tick(server);
            AnimalRide.tick(server);
            if (server.getTicks() % 100 != 0) return;
            StandManager m = StandManager.get(server);
            for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                StandData d = m.get(p.getUuid());
                if (d == null) continue;
                int mask = Progression.mask(p);
                int ns = Integer.bitCount(mask);
                if (ns > d.stage) {
                    int old = d.stage;
                    d.stage = ns;
                    d.milestones = mask;
                    d.recalc();
                    m.markDirty();
                    StandRuntime.refreshItems(p, d);
                    sendStandData(p);
                    p.sendMessage(Text.translatable("msg.standgen.evolved", ns, Progression.labelText(mask))
                            .formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
                    for (int slot = 1; slot < 4; slot++) {
                        if (d.unlockStage(slot) > old && d.unlockStage(slot) <= ns) {
                            p.sendMessage(Text.translatable("msg.standgen.skill_unlocked", d.skill(slot).text())
                                    .formatted(Formatting.GREEN));
                        }
                    }
                } else if (ns == d.stage && mask != d.milestones) {
                    d.milestones = mask;   // стадія та сама, лише виправляємо назву етапу
                    m.markDirty();
                    sendStandData(p);
                }
            }
        });

        EntityTrackingEvents.START_TRACKING.register((tracked, viewer) -> {
            if (tracked instanceof ServerPlayerEntity owner) StandRuntime.sendSupportTo(owner, viewer);
        });
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                sendHasStand(handler.player, StandManager.get(server).get(handler.player.getUuid()) != null));
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> { StandRuntime.dismiss(handler.player); StandRuntime.forget(handler.player.getUuid()); });
        ServerLifecycleEvents.SERVER_STOPPING.register(s -> { TimeStop.endAll(); ElementZone.endAll(); AnimalRide.endAll(); });
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
                            return 1;
                        }))
                        .then(CommandManager.literal("menu").executes(ctx -> {
                            openMenu(ctx.getSource().getPlayerOrThrow());
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
                            sendHasStand(p, false);
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
                                    d.milestones = (1 << d.stage) - 1;
                                    d.recalc();
                                    m.markDirty();
                                    StandRuntime.refreshItems(p, d);
                                    sendStandData(p);
                                    ctx.getSource().sendFeedback(() -> Text.translatable("msg.standgen.stage_set", d.stage), false);
                                    return 1;
                                })))));
    }
}
