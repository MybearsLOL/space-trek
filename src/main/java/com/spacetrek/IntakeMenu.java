package com.spacetrek;

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

public class IntakeMenu extends AbstractContainerMenu {

    private static final int FUEL_SLOT_X = 44;
    private static final int FUEL_SLOT_Y = 52;
    private static final int PLAYER_INVENTORY_START = 1;
    private static final int HOTBAR_START = 28;
    private static final int SLOT_END = 37;

    private final Container fuel;
    private final ContainerData data;
    private final ContainerLevelAccess access;

    public IntakeMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(1),
                new SimpleContainerData(IntakeBlockEntity.DATA_COUNT), ContainerLevelAccess.NULL);
    }

    public IntakeMenu(int containerId, Inventory inventory, Container fuel, ContainerData data,
                      ContainerLevelAccess access) {
        super(ModMenuTypes.INTAKE, containerId);
        checkContainerSize(fuel, 1);
        checkContainerDataCount(data, IntakeBlockEntity.DATA_COUNT);
        this.fuel = fuel;
        this.data = data;
        this.access = access;

        addSlot(new Slot(fuel, 0, FUEL_SLOT_X, FUEL_SLOT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return IntakeBlockEntity.isFuel(stack);
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

    public int getBurnTime() {
        return data.get(0);
    }

    public int getBurnTotal() {
        return data.get(1);
    }

    public int getOxygen() {
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
            } else if (IntakeBlockEntity.isFuel(stack)) {
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
        return stillValid(access, player, ModBlocks.INTAKE_BLOCK);
    }
}
