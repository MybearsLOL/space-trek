package com.spacetrek.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.spacetrek.RocketEntity;
import com.spacetrek.RocketInputPayload;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public final class RocketControls {

    private static int lastFlags = 0;
    private static int heartbeat = 0;
    private static boolean thrustOn = false;
    private static boolean rWasDown = false;

    private RocketControls() {
    }

    public static void init() {
        ClientTickEvents.START_CLIENT_TICK.register(RocketControls::tick);
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null) {
            lastFlags = 0;
            thrustOn = false;
            return;
        }

        if (!(player.getVehicle() instanceof RocketEntity)) {
            if (lastFlags != 0) {
                ClientPlayNetworking.send(new RocketInputPayload(0));
                lastFlags = 0;
            }
            thrustOn = false;
            rWasDown = false;
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
            boolean rDown = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_R);
            if (rDown && !rWasDown) {
                thrustOn = !thrustOn;
                player.displayClientMessage(Component.literal(thrustOn ? "Thrust ON" : "Thrust OFF"), true);
            }
            rWasDown = rDown;
            if (InputConstants.isKeyDown(window, GLFW.GLFW_KEY_F)) {
                flags |= RocketEntity.INPUT_DETHRUST;
                thrustOn = false;
            }
        }
        if (thrustOn) flags |= RocketEntity.INPUT_THRUST;

        heartbeat++;
        if (flags != lastFlags || heartbeat >= 5) {
            ClientPlayNetworking.send(new RocketInputPayload(flags));
            lastFlags = flags;
            heartbeat = 0;
        }
    }
}
