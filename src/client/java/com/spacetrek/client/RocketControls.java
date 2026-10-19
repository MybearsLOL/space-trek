package com.spacetrek.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.spacetrek.RocketEntity;
import com.spacetrek.RocketInputPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.lwjgl.glfw.GLFW;

public final class RocketControls {

    private static int lastFlags = 0;
    private static int heartbeat = 0;

    private RocketControls() {
    }

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(RocketControls::tick);
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            lastFlags = 0;
            return;
        }

        if (!(player.getVehicle() instanceof RocketEntity)) {
            if (lastFlags != 0) {
                ClientPlayNetworking.send(new RocketInputPayload(0));
                lastFlags = 0;
            }
            return;
        }

        while (minecraft.options.keyDrop.consumeClick()) {
        }
        while (minecraft.options.keyInventory.consumeClick()) {
        }
        while (minecraft.options.keySwapOffhand.consumeClick()) {
        }

        int flags = 0;
        if (minecraft.screen == null) {
            long window = minecraft.getWindow().getWindow();
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_Q)) flags |= RocketEntity.INPUT_ROLL_LEFT;
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_E)) flags |= RocketEntity.INPUT_ROLL_RIGHT;
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_R)) flags |= RocketEntity.INPUT_THRUST;
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F)) flags |= RocketEntity.INPUT_DETHRUST;
        }

        heartbeat++;
        if (flags != lastFlags || heartbeat >= 5) {
            ClientPlayNetworking.send(new RocketInputPayload(flags));
            lastFlags = flags;
            heartbeat = 0;
        }
    }
}
