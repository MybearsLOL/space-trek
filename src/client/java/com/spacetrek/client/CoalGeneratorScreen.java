package com.spacetrek.client;

import com.spacetrek.CoalGeneratorBlockEntity;
import com.spacetrek.CoalGeneratorMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class CoalGeneratorScreen extends AbstractContainerScreen<CoalGeneratorMenu> {

    private static final int FLAME_X = 57;
    private static final int FLAME_Y = 36;

    private static final int BAR_X = 116;
    private static final int BAR_Y = 17;
    private static final int BAR_WIDTH = 14;
    private static final int BAR_HEIGHT = 56;

    public CoalGeneratorScreen(CoalGeneratorMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        int left = leftPos + BAR_X;
        int top = topPos + BAR_Y;
        if (mouseX >= left && mouseX < left + BAR_WIDTH && mouseY >= top && mouseY < top + BAR_HEIGHT) {
            graphics.renderTooltip(font, Component.translatable("gui.space-trek.energy",
                    menu.getEnergy(), CoalGeneratorBlockEntity.MAX_ENERGY), mouseX, mouseY);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos;
        int y = topPos;

        graphics.fill(x, y, x + imageWidth, y + imageHeight, 0xFF555555);
        graphics.fill(x, y, x + imageWidth - 2, y + imageHeight - 2, 0xFFFFFFFF);
        graphics.fill(x + 2, y + 2, x + imageWidth - 2, y + imageHeight - 2, 0xFFC6C6C6);

        for (Slot slot : menu.slots) {
            int sx = x + slot.x;
            int sy = y + slot.y;
            graphics.fill(sx - 1, sy - 1, sx + 17, sy + 17, 0xFFFFFFFF);
            graphics.fill(sx - 1, sy - 1, sx + 16, sy + 16, 0xFF373737);
            graphics.fill(sx, sy, sx + 16, sy + 16, 0xFF8B8B8B);
        }

        renderFlame(graphics, x + FLAME_X, y + FLAME_Y);
        renderEnergyBar(graphics, x + BAR_X, y + BAR_Y);
    }

    private void renderFlame(GuiGraphics graphics, int fx, int fy) {
        graphics.fill(fx - 1, fy - 1, fx + 15, fy + 15, 0xFF373737);
        graphics.fill(fx, fy, fx + 14, fy + 14, 0xFF2B2B2B);

        int total = menu.getBurnTotal();
        int burn = menu.getBurnTime();
        if (total <= 0 || burn <= 0) return;

        int height = Math.max(1, Math.min(14, (int) (14 * (burn / (float) total))));
        graphics.fill(fx, fy + 14 - height, fx + 14, fy + 14, 0xFFFF7A00);
        graphics.fill(fx + 3, fy + 14 - height, fx + 11, fy + 14, 0xFFFFC23D);
    }

    private void renderEnergyBar(GuiGraphics graphics, int bx, int by) {
        graphics.fill(bx - 1, by - 1, bx + BAR_WIDTH + 1, by + BAR_HEIGHT + 1, 0xFF373737);
        graphics.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, 0xFF1B1B1B);

        int filled = (int) (BAR_HEIGHT * (menu.getEnergy() / (float) CoalGeneratorBlockEntity.MAX_ENERGY));
        filled = Math.max(0, Math.min(BAR_HEIGHT, filled));
        if (filled > 0) {
            graphics.fill(bx, by + BAR_HEIGHT - filled, bx + BAR_WIDTH, by + BAR_HEIGHT, 0xFF3DD16B);
            graphics.fill(bx, by + BAR_HEIGHT - filled, bx + 3, by + BAR_HEIGHT, 0xFF8CF0AA);
        }
    }
}
