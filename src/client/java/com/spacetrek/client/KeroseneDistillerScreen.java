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

    private static final int ARROW_X = 52;
    private static final int ARROW_Y = 44;
    private static final int ARROW_WIDTH = 24;
    private static final int ARROW_HEIGHT = 8;

    private static final int BAR_X = 86;
    private static final int BAR_Y = 36;
    private static final int BAR_WIDTH = 78;
    private static final int BAR_HEIGHT = 18;

    private static final int BUCKET_COUNT = KeroseneDistillerBlockEntity.MAX_KEROSENE / KeroseneDistillerBlockEntity.BUCKET_AMOUNT;

    public KeroseneDistillerScreen(KeroseneDistillerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        int barLeft = leftPos + BAR_X;
        int barTop = topPos + BAR_Y;
        if (mouseX >= barLeft && mouseX < barLeft + BAR_WIDTH && mouseY >= barTop && mouseY < barTop + BAR_HEIGHT) {
            graphics.renderTooltip(font, Component.translatable("gui.space-trek.kerosene_distiller.kerosene",
                    bucketAmount(), BUCKET_COUNT), mouseX, mouseY);
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

        renderArrow(graphics, x + ARROW_X, y + ARROW_Y);
        renderKeroseneBar(graphics, x + BAR_X, y + BAR_Y);
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

    private void renderKeroseneBar(GuiGraphics graphics, int bx, int by) {
        graphics.fill(bx - 1, by - 1, bx + BAR_WIDTH + 1, by + BAR_HEIGHT + 1, 0xFF373737);
        graphics.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, 0xFF1B1B1B);

        int filled = (int) (BAR_WIDTH * (menu.getKerosene() / (float) KeroseneDistillerBlockEntity.MAX_KEROSENE));
        filled = Math.max(0, Math.min(BAR_WIDTH, filled));
        if (filled > 0) {
            graphics.fill(bx, by, bx + filled, by + BAR_HEIGHT, 0xFFC42A2A);
            graphics.fill(bx, by, bx + filled, by + 4, 0xFFF06A5E);
        }

        for (int i = 1; i < BUCKET_COUNT; i++) {
            int tx = bx + BAR_WIDTH * i / BUCKET_COUNT;
            graphics.fill(tx, by, tx + 1, by + BAR_HEIGHT, 0x66000000);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        Component amount = Component.translatable("gui.space-trek.kerosene_distiller.amount", bucketAmount(), BUCKET_COUNT);
        graphics.drawString(font, amount, BAR_X, BAR_Y + BAR_HEIGHT + 5, 0x404040, false);
    }

    private String bucketAmount() {
        return String.format(Locale.ROOT, "%.1f", menu.getKerosene() / (float) KeroseneDistillerBlockEntity.BUCKET_AMOUNT);
    }
}
