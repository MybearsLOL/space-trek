package com.spacetrek.client;

import com.spacetrek.IntakeBlockEntity;
import com.spacetrek.IntakeMenu;
import java.util.Locale;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;

public class IntakeScreen extends AbstractContainerScreen<IntakeMenu> {

    private static final int BAR_X = 78;
    private static final int BAR_Y = 36;
    private static final int BAR_WIDTH = 82;
    private static final int BAR_HEIGHT = 18;

    private static final int FLAME_X = 47;
    private static final int FLAME_Y = 34;
    private static final int FLAME_WIDTH = 12;
    private static final int FLAME_HEIGHT = 14;

    private static final int BUCKET_COUNT = IntakeBlockEntity.MAX_OXYGEN / IntakeBlockEntity.BUCKET_AMOUNT;

    public IntakeScreen(IntakeMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);

        int barLeft = leftPos + BAR_X;
        int barTop = topPos + BAR_Y;
        if (mouseX >= barLeft && mouseX < barLeft + BAR_WIDTH && mouseY >= barTop && mouseY < barTop + BAR_HEIGHT) {
            graphics.renderTooltip(font, oxygenText(), mouseX, mouseY);
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
        renderOxygenBar(graphics, x + BAR_X, y + BAR_Y);
    }

    private void renderFlame(GuiGraphics graphics, int fx, int fy) {
        graphics.fill(fx - 1, fy - 1, fx + FLAME_WIDTH + 1, fy + FLAME_HEIGHT + 1, 0xFF373737);
        graphics.fill(fx, fy, fx + FLAME_WIDTH, fy + FLAME_HEIGHT, 0xFF2B2B2B);

        int total = menu.getBurnTotal();
        if (total <= 0 || menu.getBurnTime() <= 0) return;

        float fraction = Math.min(1f, menu.getBurnTime() / (float) total);
        int height = Math.max(1, (int) Math.ceil(FLAME_HEIGHT * fraction));
        int top = fy + FLAME_HEIGHT - height;
        graphics.fill(fx, top, fx + FLAME_WIDTH, fy + FLAME_HEIGHT, 0xFFFF7A00);
        graphics.fill(fx + 3, top, fx + FLAME_WIDTH - 3, fy + FLAME_HEIGHT, 0xFFFFC23D);
    }

    private void renderOxygenBar(GuiGraphics graphics, int bx, int by) {
        graphics.fill(bx - 1, by - 1, bx + BAR_WIDTH + 1, by + BAR_HEIGHT + 1, 0xFF373737);
        graphics.fill(bx, by, bx + BAR_WIDTH, by + BAR_HEIGHT, 0xFF1B1B1B);

        int filled = (int) (BAR_WIDTH * (menu.getOxygen() / (float) IntakeBlockEntity.MAX_OXYGEN));
        filled = Math.max(0, Math.min(BAR_WIDTH, filled));
        if (filled > 0) {
            graphics.fill(bx, by, bx + filled, by + BAR_HEIGHT, 0xFF3FA9E8);
            graphics.fill(bx, by, bx + filled, by + 4, 0xFF8FD8FF);
        }

        for (int i = 1; i < BUCKET_COUNT; i++) {
            int tx = bx + BAR_WIDTH * i / BUCKET_COUNT;
            graphics.fill(tx, by, tx + 1, by + BAR_HEIGHT, 0x66000000);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderLabels(graphics, mouseX, mouseY);
        graphics.drawString(font, oxygenText(), BAR_X, BAR_Y + BAR_HEIGHT + 5, 0x404040, false);
    }

    private Component oxygenText() {
        String amount = String.format(Locale.ROOT, "%.1f", menu.getOxygen() / (float) IntakeBlockEntity.BUCKET_AMOUNT);
        return Component.translatable("gui.space-trek.intake.oxygen", amount, BUCKET_COUNT);
    }
}
