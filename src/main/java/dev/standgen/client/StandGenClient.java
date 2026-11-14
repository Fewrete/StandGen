package dev.standgen.client;

import dev.standgen.StandGenMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ArmorRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

public class StandGenClient implements ClientModInitializer {
    private static final String CAT = "key.categories.standgen";
    private static KeyBinding summon, skill1, skill2, skill3, skill4, mode, hold;
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
        EntityModelLayerRegistry.registerModelLayer(StandModels.SWARM, StandModels::swarm);
        EntityRendererRegistry.register(StandGenMod.STAND, StandRenderer::new);

        // броня-стенд: малюється на гравці й фарбується кольорами стенда (з NBT предмета)
        ArmorRenderer.register(new StandArmorRenderer(), StandGenMod.STAND_HELMET, StandGenMod.STAND_CHESTPLATE,
                StandGenMod.STAND_LEGGINGS, StandGenMod.STAND_BOOTS);
        ColorProviderRegistry.ITEM.register((stack, tintIndex) ->
                        stack.hasNbt() && stack.getNbt().contains("c1") ? stack.getNbt().getInt("c1") : 0xFFFFFF,
                StandGenMod.STAND_HELMET, StandGenMod.STAND_CHESTPLATE, StandGenMod.STAND_LEGGINGS, StandGenMod.STAND_BOOTS);

        summon = key("key.standgen.summon", GLFW.GLFW_KEY_R);
        skill1 = key("key.standgen.skill1", GLFW.GLFW_KEY_X);
        skill2 = key("key.standgen.skill2", GLFW.GLFW_KEY_C);
        skill3 = key("key.standgen.skill3", GLFW.GLFW_KEY_V);
        skill4 = key("key.standgen.skill4", GLFW.GLFW_KEY_B);
        mode = key("key.standgen.mode", GLFW.GLFW_KEY_G);
        hold = key("key.standgen.attack", GLFW.GLFW_KEY_Z);

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (summon.wasPressed()) send(StandGenMod.ACT_SUMMON);
            while (skill1.wasPressed()) send(StandGenMod.ACT_SKILL1);
            while (skill2.wasPressed()) send(StandGenMod.ACT_SKILL2);
            while (skill3.wasPressed()) send(StandGenMod.ACT_SKILL3);
            while (skill4.wasPressed()) send(StandGenMod.ACT_SKILL4);
            while (mode.wasPressed()) send(StandGenMod.ACT_MODE);

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
