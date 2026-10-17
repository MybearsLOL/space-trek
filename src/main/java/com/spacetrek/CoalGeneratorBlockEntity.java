package com.spacetrek;

import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CoalGeneratorBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory<BlockPos>, PowerNode {

    public static final int DATA_COUNT = 3;
    public static final int MAX_ENERGY = 30000;
    public static final int POWER_PER_TICK = 40;
    public static final int OUTPUT_PER_TICK = 200;
    private static final int COAL_BURN_TICKS = 1600;
    private static final int LAVA_BURN_TICKS = 20000;

    private final SimpleContainer fuelSlot = new SimpleContainer(1);

    private int burnTime = 0;
    private int burnTotal = 0;
    private int energy = 0;

    public final ContainerData dataAccess = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case 0 -> burnTime;
                case 1 -> burnTotal;
                case 2 -> energy;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case 0 -> burnTime = value;
                case 1 -> burnTotal = value;
                case 2 -> energy = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public CoalGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COAL_GENERATOR, pos, state);
        fuelSlot.addListener(container -> setChanged());
    }

    public static int getFuelTime(ItemStack stack) {
        if (stack.is(Items.COAL) || stack.is(Items.CHARCOAL)) return COAL_BURN_TICKS;
        if (stack.is(Items.LAVA_BUCKET)) return LAVA_BURN_TICKS;
        return 0;
    }

    public static boolean isFuel(ItemStack stack) {
        return getFuelTime(stack) > 0;
    }

    public SimpleContainer getFuelContainer() {
        return fuelSlot;
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean dirty = false;
        boolean hasRoom = energy + POWER_PER_TICK <= MAX_ENERGY;

        if (burnTime > 0 && hasRoom) {
            burnTime--;
            energy += POWER_PER_TICK;
            dirty = true;
        }

        if (burnTime <= 0 && hasRoom) {
            ItemStack stack = fuelSlot.getItem(0);
            int time = getFuelTime(stack);
            if (time > 0) {
                burnTotal = time;
                burnTime = time;
                if (stack.is(Items.LAVA_BUCKET)) {
                    fuelSlot.setItem(0, new ItemStack(Items.BUCKET));
                } else {
                    fuelSlot.removeItem(0, 1);
                }
                dirty = true;
            }
        }

        if (energy > 0) {
            int sent = PowerNetwork.distribute(level, pos, Math.min(energy, OUTPUT_PER_TICK));
            if (sent > 0) {
                energy -= sent;
                dirty = true;
            }
        }

        boolean lit = burnTime > 0;
        if (state.getValue(CoalGeneratorBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(CoalGeneratorBlock.LIT, lit), 3);
        }

        if (dirty) setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Fuel", fuelSlot.getItem(0).saveOptional(registries));
        tag.putInt("BurnTime", burnTime);
        tag.putInt("BurnTotal", burnTotal);
        tag.putInt("Energy", energy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        fuelSlot.setItem(0, ItemStack.parseOptional(registries, tag.getCompound("Fuel")));
        burnTime = tag.getInt("BurnTime");
        burnTotal = tag.getInt("BurnTotal");
        energy = tag.getInt("Energy");
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.space-trek.coal_generator");
    }

    @Nullable
    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new CoalGeneratorMenu(containerId, inventory, worldPosition, fuelSlot, dataAccess,
                ContainerLevelAccess.create(level, worldPosition));
    }

    @Override
    public BlockPos getScreenOpeningData(ServerPlayer player) {
        return worldPosition;
    }
}
