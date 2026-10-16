package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public class KeroseneDistillerMenu extends AbstractContainerMenu {

    public static final int FEED_SLOT_X = 26;
    public static final int FEED_SLOT_Y = 40;

    private static final int PLAYER_INVENTORY_START = 1;
    private static final int HOTBAR_START = 28;
    private static final int SLOT_END = 37;

    private final BlockPos pos;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public KeroseneDistillerMenu(int containerId, Inventory inventory, BlockPos pos) {
        this(containerId, inventory, pos, new SimpleContainer(1),
                new SimpleContainerData(KeroseneDistillerBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL);
    }

    public KeroseneDistillerMenu(int containerId, Inventory inventory, BlockPos pos, Container feedstock,
                                 ContainerData data, ContainerLevelAccess access) {
        super(ModMenuTypes.KEROSENE_DISTILLER, containerId);
        checkContainerSize(feedstock, 1);
        checkContainerDataCount(data, KeroseneDistillerBlockEntity.DATA_COUNT);
        this.pos = pos;
        this.data = data;
        this.access = access;

        addSlot(new Slot(feedstock, 0, FEED_SLOT_X, FEED_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return KeroseneDistillerBlockEntity.isFeedstock(stack);
            }
        });

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142));
        }

        addDataSlots(data);
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getProgress() {
        return data.get(0);
    }

    public int getProgressTotal() {
        return data.get(1);
    }

    public int getKerosene() {
        return data.get(2);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        ItemStack result = ItemStack.EMPTY;
        Slot slot = slots.get(index);

        if (slot != null && slot.hasItem()) {
            ItemStack stack = slot.getItem();
            result = stack.copy();

            if (index == 0) {
                if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, SLOT_END, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (KeroseneDistillerBlockEntity.isFeedstock(stack)) {
                if (!moveItemStackTo(stack, 0, 1, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (index < HOTBAR_START) {
                if (!moveItemStackTo(stack, HOTBAR_START, SLOT_END, false)) {
                    return ItemStack.EMPTY;
                }
            } else if (!moveItemStackTo(stack, PLAYER_INVENTORY_START, HOTBAR_START, false)) {
                return ItemStack.EMPTY;
            }

            if (stack.isEmpty()) {
                slot.setByPlayer(ItemStack.EMPTY);
            } else {
                slot.setChanged();
            }

            if (stack.getCount() == result.getCount()) {
                return ItemStack.EMPTY;
            }

            slot.onTake(player, stack);
        }

        return result;
    }

    @Override
    public boolean stillValid(Player player) {
        return stillValid(access, player, ModBlocks.KEROSENE_DISTILLER_BLOCK);
    }
}
