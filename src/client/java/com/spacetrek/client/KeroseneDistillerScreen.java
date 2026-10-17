package com.spacetrek.client;

import com.spacetrek.KeroseneDistillerBlockEntity;
import com.spacetrek.KeroseneDistillerMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class KeroseneDistillerScreen extends AbstractContainerScreen<KeroseneDistillerMenu> {

    private static final int BAR_TOP = 17;
    private static final int BAR_HEIGHT = 56;
    private static final int BAR_WIDTH = 14;
    private static final int ENERGY_X = 10;
    private static final int CRUDE_X = 34;
    private static final int KEROSENE_X = 152;

    private static final int ARROW_X = 76;
    private static final int ARROW_Y = 52;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 8;

    public KeroseneDistillerScreen(KeroseneDistillerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        if (hovering(mouseX, mouseY, ENERGY_X)) {
            graphics.renderTooltip(font, Component.translatable("gui.space-trek.energy",
                    menu.getEnergy(), KeroseneDistillerBlockEntity.MAX_ENERGY), mouseX, mouseY);
        } else if (hovering(mouseX, mouseY, CRUDE_X)) {
            graphics.renderTooltip(font, Component.translatable("gui.space-trek.kerosene_distiller.crude",
                    buckets(menu.getCrude()), buckets(KeroseneDistillerBlockEntity.MAX_FLUID)), mouseX, mouseY);
        } else if (hovering(mouseX, mouseY, KEROSENE_X)) {
            graphics.renderTooltip(font, Component.translatable("gui.space-trek.kerosene_distiller.kerosene",
                    buckets(menu.getKerosene()), buckets(KeroseneDistillerBlockEntity.MAX_FLUID)), mouseX, mouseY);
        }
    }

    private boolean hovering(int mouseX, int mouseY, int barX) {
        int left = leftPos + barX;
        int top = topPos + BAR_TOP;
        return mouseX >= left && mouseX < left + BAR_WIDTH && mouseY >= top && mouseY < top + BAR_HEIGHT;
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

        renderBar(graphics, x + ENERGY_X, y + BAR_TOP, menu.getEnergy(), KeroseneDistillerBlockEntity.MAX_ENERGY, 0xFF3DD16B, 0xFF8CF0AA);
        renderBar(graphics, x + CRUDE_X, y + BAR_TOP, menu.getCrude(), KeroseneDistillerBlockEntity.MAX_FLUID, 0xFF2A2A30, 0xFF6A6A76);
        renderBar(graphics, x + KEROSENE_X, y + BAR_TOP, menu.getKerosene(), KeroseneDistillerBlockEntity.MAX_FLUID, 0xFFC42A2A, 0xFFF06A5E);
        renderArrow(graphics, x + ARROW_X, y + ARROW_Y);
    }

    private void renderBar(GuiGraphics graphics, int bx, int by, int value, int max, int color, int shine) {
        graphics.fill(bx - 1, by - 1, bx + BAR_WIDTH + 1, by + BAR_HEIGHT + 1, 0xFF373737);
        graphics.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, 0xFF1B1B1B);

        int filled = (int) (BAR_HEIGHT * (value / (float) max));
        filled = Math.max(0, Math.min(BAR_HEIGHT, filled));
        if (filled > 0) {
            graphics.fill(bx, by + BAR_HEIGHT - filled, bx + BAR_WIDTH, by + BAR_HEIGHT, color);
            graphics.fill(bx, by + BAR_HEIGHT - filled, bx + 3, by + BAR_HEIGHT, shine);
        }

        for (int i = 1; i < 8; i++) {
            int ty = by + BAR_HEIGHT * i / 8;
            graphics.fill(bx + BAR_WIDTH - 4, ty, bx + BAR_WIDTH, ty + 1, 0x66000000);
        }
    }

    private void renderArrow(GuiGraphics graphics, int ax, int ay) {
        graphics.fill(ax - 1, ay - 1, ax + ARROW_WIDTH + 1, ay + ARROW_HEIGHT + 1, 0xFF373737);
        graphics.fill(ax, ay, ax + ARROW_WIDTH, ay + ARROW_HEIGHT, 0xFF2B2B2B);

        int total = menu.getProgressTotal();
        int progress = menu.getProgress();
        if (total <= 0 || progress <= 0) return;

        int filled = Math.max(1, Math.min(ARROW_WIDTH, (int) (ARROW_WIDTH * (progress / (float) total))));
        graphics.fill(ax, ay, ax + filled, ay + ARROW_HEIGHT, 0xFFFF7A00);
        graphics.fill(ax, ay, ax + filled, ay + 2, 0xFFFFC23D);
    }

    private static String buckets(int amount) {
        return String.format(Locale.ROOT, "%.1f", amount / 1000f);
    }
}
