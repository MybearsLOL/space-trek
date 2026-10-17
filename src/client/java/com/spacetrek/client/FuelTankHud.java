package com.spacetrek.client;

import com.spacetrek.FuelTankBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class FuelTankHud {

    public static void render(GuiGraphics gui, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;

        HitResult hit = mc.hitResult;
        if (hit == null || hit.getType() != HitResult.Type.BLOCK) return;

        BlockPos pos = ((BlockHitResult) hit).getBlockPos();
        Level level = mc.level;
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FuelTankBlockEntity tank)) return;

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();

        int panelW = 160;
        int panelH = 56;
        int panelX = (screenW - panelW) / 2;
        int panelY = screenH - panelH - 50;

        gui.fill(panelX, panelY, panelX + panelW, panelY + panelH, 0xCC0A0A12);
        gui.fill(panelX, panelY, panelX + panelW, panelY + 1, 0xFF4A90D9);

        gui.drawCenteredString(
                mc.font,
                Component.literal(tank.getGroupBlocks() > 1
                        ? "FUEL TANK (" + tank.getGroupBlocks() + " blocks)"
                        : "FUEL TANK"),
                panelX + panelW / 2,
                panelY + 6,
                0xFF4A90D9
        );

        FuelTankBlockEntity.FluidType type = tank.getFluidType();
        String typeLabel = switch (type) {
            case ROCKET_FUEL -> "Rocket Fuel";
            case WATER -> "Water";
            case OXYGEN -> "Oxygen (O2)";
            case KEROSENE -> "Kerosene";
            case CRUDE_OIL -> "Crude Oil";
            case COOLED_CRUDE -> "Cooled Crude Oil";
            case EMPTY -> "Empty";
        };
        int typeColor = switch (type) {
            case ROCKET_FUEL -> 0xFFFFD700;
            case WATER -> 0xFF55AAFF;
            case OXYGEN -> 0xFFAADDFF;
            case KEROSENE -> 0xFFE04848;
            case CRUDE_OIL -> 0xFF8A8A92;
            case COOLED_CRUDE -> 0xFFB5835A;
            case EMPTY -> 0xFF888888;
        };

        gui.drawCenteredString(
                mc.font,
                Component.literal(typeLabel),
                panelX + panelW / 2,
                panelY + 18,
                typeColor
        );

        int barX = panelX + 10;
        int barY = panelY + 32;
        int barW = panelW - 20;
        int barH = 8;
        gui.fill(barX, barY, barX + barW, barY + barH, 0xFF1A1A2A);

        int buckets = tank.getGroupBuckets();
        int maxBuckets = tank.getGroupCapacityBuckets();
        int fillW = (int) ((float) buckets / maxBuckets * barW);
        if (fillW > 0) {
            gui.fill(barX, barY, barX + fillW, barY + barH, typeColor);
        }

        gui.fill(barX, barY, barX + barW, barY + 1, 0xFF333355);
        gui.fill(barX, barY + barH - 1, barX + barW, barY + barH, 0xFF333355);
        gui.fill(barX, barY, barX + 1, barY + barH, 0xFF333355);
        gui.fill(barX + barW - 1, barY, barX + barW, barY + barH, 0xFF333355);

        gui.drawCenteredString(
                mc.font,
                Component.literal(buckets + " / " + maxBuckets + " buckets"),
                panelX + panelW / 2,
                panelY + 44,
                0xFFCCCCCC
        );
    }
}