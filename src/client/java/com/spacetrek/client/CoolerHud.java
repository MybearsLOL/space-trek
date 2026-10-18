package com.spacetrek.client;

import com.spacetrek.CoolerControllerBlockEntity;
import com.spacetrek.CoolerPortBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class CoolerHud {

    public static void render(GuiGraphics gui, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockEntity be = mc.level.getBlockEntity(((BlockHitResult) hit).getBlockPos());
        CoolerControllerBlockEntity cooler = null;
        if (be instanceof CoolerControllerBlockEntity controller) {
            cooler = controller;
        } else if (be instanceof CoolerPortBlockEntity port) {
            cooler = port.getController();
        }
        if (cooler == null) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int panelW = 170;
        int panelH = 82;
        int panelX = (screenW - panelW) / 2;
        int panelY = screenH - panelH - 50;

        gui.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC0A0A12);
        gui.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF7FD8FF);
        gui.drawCenteredString(mc.font, Component.literal("CRYO COOLER"), panelX + panelW / 2, panelY + 6, 0xFF7FD8FF);

        int max = CoolerControllerBlockEntity.MAX_FLUID;
        bar(gui, mc, panelX + 10, panelY + 20, panelW - 20, "Hot crude", cooler.getHot(), max, 0xFFD2693C);
        bar(gui, mc, panelX + 10, panelY + 34, panelW - 20, "Cooled", cooler.getCooled(), max, 0xFFB5835A);
        bar(gui, mc, panelX + 10, panelY + 48, panelW - 20, "Power", cooler.getEnergy(), CoolerControllerBlockEntity.MAX_ENERGY, 0xFF3DD16B);

        String status;
        int color;
        if (!cooler.isFormed()) {
            status = "Needs a 3x2x2 casing frame with 2 ports";
            color = 0xFFFF5555;
        } else if (cooler.getCooldown() > 0) {
            status = "Overheated - cooling down " + (cooler.getCooldown() + 19) / 20 + "s";
            color = 0xFFFFAA00;
        } else if (cooler.getProgress() > 0) {
            status = "Cooling... " + cooler.getProgress() * 100 / CoolerControllerBlockEntity.PROCESS_TICKS + "%";
            color = 0xFF55FF55;
        } else if (cooler.getEnergy() < CoolerControllerBlockEntity.POWER_PER_TICK) {
            status = "No power";
            color = 0xFFFF5555;
        } else if (cooler.getHot() < CoolerControllerBlockEntity.BUCKET_AMOUNT) {
            status = "Needs hot crude oil (60-200 F)";
            color = 0xFF888888;
        } else {
            status = "Output full";
            color = 0xFFFFAA00;
        }
        gui.drawCenteredString(mc.font, Component.literal(status), panelX + panelW / 2, panelY + 66, color);
    }

    private static void bar(GuiGraphics gui, Minecraft mc, int x, int y, int w, String label, int value, int max, int color) {
        gui.drawString(mc.font, Component.literal(label), x, y - 1, color, false);
        int barX = x + 62;
        int barW = w - 62;
        gui.fill(barX, y, barX + barW, y + 8, 0xFF1A1A2A);
        int fill = (int) ((float) value / max * (barW - 2));
        if (fill > 0) gui.fill(barX + 1, y + 1, barX + 1 + fill, y + 7, color);
        gui.fill(barX, y, barX + barW, y + 1, 0xFF333355);
        gui.fill(barX, y + 7, barX + barW, y + 8, 0xFF333355);
    }
}
