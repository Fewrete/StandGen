package dev.standgen.client;

import dev.standgen.StandGenMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityFeatureRendererRegistrationCallback;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.render.entity.PlayerEntityRenderer;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

import java.util.UUID;

public class StandGenClient implements ClientModInitializer {
    private static final String CAT = "key.categories.standgen";
    private static KeyBinding summon, skill1, skill2, skill3, skill4, mode, hold, mountKey;
    public static KeyBinding menuKey;
    private static boolean holdDown;
    private static int holdTicks;

    private static KeyBinding key(String name, int code) {
        return KeyBindingHelper.registerKeyBinding(new KeyBinding(name, InputUtil.Type.KEYSYM, code, CAT));
    }

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(StandModels.HUMANOID, StandModels::humanoid);
        EntityModelLayerRegistry.registerModelLayer(StandModels.WORN, StandModels::worn);
        EntityModelLayerRegistry.registerModelLayer(StandModels.WEAPON, StandModels::weapon);
        EntityModelLayerRegistry.registerModelLayer(StandModels.ANIMALS, StandModels::animals);
        EntityModelLayerRegistry.registerModelLayer(StandModels.SWARM, StandModels::swarm);
        EntityModelLayerRegistry.registerModelLayer(StandModels.BACK, StandModels::back);
        // стенд Підтримка носиться на спині: шар на моделі гравця
        LivingEntityFeatureRendererRegistrationCallback.EVENT.register((entityType, entityRenderer, registrationHelper, context) -> {
            if (entityRenderer instanceof PlayerEntityRenderer per) {
                registrationHelper.register(new SupportBackFeature(per, context.getModelLoader()));
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(StandGenMod.SUPPORT_BACK, (client, handler, buf, sender) -> {
            UUID id = buf.readUuid();
            boolean on = buf.readBoolean();
            SupportBackState.Data data = null;
            if (on) {
                int c1 = buf.readInt(), c2 = buf.readInt(), st = buf.readInt(), sy = buf.readInt(), k = buf.readInt();
                data = new SupportBackState.Data(c1, c2, st, sy, k);
            }
            SupportBackState.Data fin = data;
            client.execute(() -> SupportBackState.set(id, fin));
        });
        EntityRendererRegistry.register(StandGenMod.STAND, StandRenderer::new);
        EntityRendererRegistry.register(StandGenMod.FX, StandFxRenderer::new);

        // броня-стенд: малюється на гравці й фарбується кольорами стенда (з NBT предмета)
        ArmorRenderer.register(new StandArmorRenderer(), StandGenMod.STAND_HELMET, StandGenMod.STAND_CHESTPLATE,
                StandGenMod.STAND_LEGGINGS, StandGenMod.STAND_BOOTS);
        ColorProviderRegistry.ITEM.register((stack, tintIndex) ->
                        stack.hasNbt() && stack.getNbt().contains("c1") ? stack.getNbt().getInt("c1") : 0xFFFFFF,
                StandGenMod.STAND_HELMET, StandGenMod.STAND_CHESTPLATE, StandGenMod.STAND_LEGGINGS, StandGenMod.STAND_BOOTS);
        ColorProviderRegistry.ITEM.register((stack, tintIndex) ->
                        stack.hasNbt() && stack.getNbt().contains("c1") ? stack.getNbt().getInt("c1") : 0xFFFFFF,
                StandGenMod.STAND_PICKAXE, StandGenMod.STAND_SWORD, StandGenMod.STAND_HAMMER,
                StandGenMod.STAND_SPEAR, StandGenMod.STAND_BOW);

        summon = key("key.standgen.summon", GLFW.GLFW_KEY_R);
        skill1 = key("key.standgen.skill1", GLFW.GLFW_KEY_X);
        skill2 = key("key.standgen.skill2", GLFW.GLFW_KEY_C);
        skill3 = key("key.standgen.skill3", GLFW.GLFW_KEY_V);
        skill4 = key("key.standgen.skill4", GLFW.GLFW_KEY_B);
        mode = key("key.standgen.mode", GLFW.GLFW_KEY_G);
        hold = key("key.standgen.attack", GLFW.GLFW_KEY_Z);
        menuKey = key("key.standgen.menu", GLFW.GLFW_KEY_K);
        mountKey = key("key.standgen.mount", GLFW.GLFW_KEY_H);

        ClientPlayNetworking.registerGlobalReceiver(StandGenMod.HAS_STAND, (client, handler, buf, sender) -> {
            boolean has = buf.readBoolean();
            client.execute(() -> StandClientState.hasStand = has);
        });
        ClientPlayNetworking.registerGlobalReceiver(StandGenMod.STAND_DATA, (client, handler, buf, sender) -> {
            StandClientData data = null;
            if (buf.readBoolean()) {
                String name = buf.readString();
                int pr = buf.readInt(), se = buf.readInt(), fo = buf.readInt(), st = buf.readInt(), ms = buf.readInt();
                int po = buf.readInt(), sp = buf.readInt(), ra = buf.readInt(), du = buf.readInt(), pc = buf.readInt(), ma = buf.readInt();
                float[] sc = new float[dev.standgen.stand.Archetype.COUNT];
                for (int i = 0; i < sc.length; i++) sc[i] = buf.readFloat();
                boolean edit = buf.readBoolean();
                int el = buf.readInt();
                int an = buf.readInt();
                data = new StandClientData(name, pr, se, fo, st, ms, po, sp, ra, du, pc, ma, sc, edit, el, an);
            }
            StandClientData fin = data;
            client.execute(() -> StandClientData.current = fin);
        });
        ClientPlayNetworking.registerGlobalReceiver(StandGenMod.OPEN_MENU, (client, handler, buf, sender) ->
                client.execute(() -> client.setScreen(new StandScreen())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> {
            StandClientData.current = null;
            StandClientState.hasStand = false;
            SupportBackState.clear();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (summon.wasPressed()) send(StandGenMod.ACT_SUMMON);
            while (skill1.wasPressed()) send(StandGenMod.ACT_SKILL1);
            while (skill2.wasPressed()) send(StandGenMod.ACT_SKILL2);
            while (skill3.wasPressed()) send(StandGenMod.ACT_SKILL3);
            while (skill4.wasPressed()) send(StandGenMod.ACT_SKILL4);
            while (mode.wasPressed()) send(StandGenMod.ACT_MODE);
            while (menuKey.wasPressed()) send(StandGenMod.ACT_MENU);
            while (mountKey.wasPressed()) send(StandGenMod.ACT_MOUNT);

            // ручна атака: утримувати; пінг кожні ~0.75 с, щоб сервер не скинув стан
            boolean down = hold.isPressed();
            if (down) {
                if (!holdDown || ++holdTicks >= 15) {
                    send(StandGenMod.ACT_HOLD_ON);
                    holdTicks = 0;
                }
            } else if (holdDown) {
                send(StandGenMod.ACT_HOLD_OFF);
            }
            holdDown = down;
        });
    }

    private static void send(int action) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(action);
        ClientPlayNetworking.send(StandGenMod.ACTION, buf);
    }
}
