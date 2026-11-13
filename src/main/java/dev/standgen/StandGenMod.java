package dev.standgen;

import dev.standgen.entity.StandEntity;
import dev.standgen.item.StandArrowItem;
import dev.standgen.stand.*;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.fabricmc.fabric.api.item.v1.FabricItemSettings;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.item.Item;
import net.minecraft.item.ItemGroups;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Rarity;

public class StandGenMod implements ModInitializer {
    public static final String MOD_ID = "standgen";
    public static final Identifier ACTION = new Identifier(MOD_ID, "action");

    public static Identifier id(String path) { return new Identifier(MOD_ID, path); }

    public static final Item STAND_ARROW = Registry.register(Registries.ITEM, id("stand_arrow"),
            new StandArrowItem(new FabricItemSettings().maxCount(16).rarity(Rarity.RARE)));

    public static final EntityType<StandEntity> STAND = Registry.register(Registries.ENTITY_TYPE, id("stand"),
            FabricEntityTypeBuilder.create(SpawnGroup.MISC, StandEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(1)
                    .build());

    @Override
    public void onInitialize() {
        FabricDefaultAttributeRegistry.register(STAND, StandEntity.createStandAttributes());

        ItemGroupEvents.modifyEntriesEvent(ItemGroups.TOOLS).register(e -> e.add(STAND_ARROW));

        // Клавіші з клієнта: 0 = виклик/прибрати, 1 = здібність 1, 2 = здібність 2
        ServerPlayNetworking.registerGlobalReceiver(ACTION, (server, player, handler, buf, sender) -> {
            int action = buf.readInt();
            server.execute(() -> {
                switch (action) {
                    case 0 -> StandRuntime.toggle(player);
                    case 1 -> StandRuntime.useAbility(player, 0);
                    case 2 -> StandRuntime.useAbility(player, 1);
                    default -> {}
                }
            });
        });

        // Стенд атакує ціль, яку вдарив власник
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hit) -> {
            if (!world.isClient && player instanceof ServerPlayerEntity sp
                    && entity instanceof LivingEntity le && !(entity instanceof StandEntity)) {
                StandEntity s = StandRuntime.getStand(sp);
                if (s != null) s.setTarget(le);
            }
            return ActionResult.PASS;
        });

        // Еволюція: раз на 5 секунд перевіряємо прогрес
        ServerTickEvents.END_SERVER_TICK.register(server -> {
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
                    p.sendMessage(Text.literal("✦ Твій стенд еволюціонував! Стадія " + ns + ": "
                            + Progression.stageName(ns)).formatted(Formatting.LIGHT_PURPLE, Formatting.BOLD));
                    if (ns == 2) p.sendMessage(Text.literal("Відкрилась друга здібність: "
                            + d.secondary.abilityName).formatted(Formatting.GREEN));
                }
            }
        });

        ServerLifecycleEvents.SERVER_STOPPED.register(s -> StandRuntime.clear());

        // Команди
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> dispatcher.register(
                CommandManager.literal("stand")
                        .then(CommandManager.literal("info").executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            StandData d = StandManager.get(ctx.getSource().getServer()).get(p.getUuid());
                            if (d == null) {
                                ctx.getSource().sendFeedback(() -> Text.literal("У тебе ще немає стенда."), false);
                                return 0;
                            }
                            for (Text t : d.describe()) ctx.getSource().sendFeedback(() -> t, false);
                            ctx.getSource().sendFeedback(() -> Text.literal(d.scoreLine()).formatted(Formatting.DARK_GRAY), false);
                            return 1;
                        }))
                        .then(CommandManager.literal("awaken").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            StandGenerator.awaken(p);
                            return 1;
                        }))
                        .then(CommandManager.literal("reset").requires(src -> src.hasPermissionLevel(2)).executes(ctx -> {
                            ServerPlayerEntity p = ctx.getSource().getPlayerOrThrow();
                            StandEntity s = StandRuntime.getStand(p);
                            if (s != null) s.discard();
                            StandManager.get(ctx.getSource().getServer()).remove(p.getUuid());
                            ctx.getSource().sendFeedback(() -> Text.literal("Стенд скинуто."), false);
                            return 1;
                        }))));
    }
}
