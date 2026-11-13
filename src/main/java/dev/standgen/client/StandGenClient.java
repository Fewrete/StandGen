package dev.standgen.client;

import dev.standgen.StandGenMod;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.EntityModelLayerRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry;
import net.fabricmc.fabric.api.networking.v1.PacketByteBufs;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.PacketByteBuf;
import org.lwjgl.glfw.GLFW;

public class StandGenClient implements ClientModInitializer {
    private static KeyBinding summon, ability1, ability2;

    @Override
    public void onInitializeClient() {
        EntityModelLayerRegistry.registerModelLayer(StandModels.ARMORED, StandModels::armored);
        EntityModelLayerRegistry.registerModelLayer(StandModels.WEAPON, StandModels::weapon);
        EntityModelLayerRegistry.registerModelLayer(StandModels.SWARM, StandModels::swarm);
        EntityRendererRegistry.register(StandGenMod.STAND, StandRenderer::new);

        summon = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.standgen.summon",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_R, "key.categories.standgen"));
        ability1 = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.standgen.ability1",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_X, "key.categories.standgen"));
        ability2 = KeyBindingHelper.registerKeyBinding(new KeyBinding("key.standgen.ability2",
                InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_C, "key.categories.standgen"));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.player == null) return;
            while (summon.wasPressed()) send(0);
            while (ability1.wasPressed()) send(1);
            while (ability2.wasPressed()) send(2);
        });
    }

    private static void send(int action) {
        PacketByteBuf buf = PacketByteBufs.create();
        buf.writeInt(action);
        ClientPlayNetworking.send(StandGenMod.ACTION, buf);
    }
}
