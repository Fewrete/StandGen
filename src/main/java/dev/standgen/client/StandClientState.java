package dev.standgen.client;

import net.minecraft.client.MinecraftClient;

/** Клієнтський стан: чи є в локального гравця стенд. Без стенда чужі стенди не видно (як у оригіналі). */
public final class StandClientState {
    public static volatile boolean hasStand;

    private StandClientState() {}

    public static boolean canSeeStands() {
        if (hasStand) return true;
        var p = MinecraftClient.getInstance().player;
        return p != null && p.isSpectator();   // спостерігачі бачать усе
    }
}
