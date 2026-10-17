package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class CoolerPortBlockEntity extends BlockEntity implements PowerAcceptor, FluidSink {

    @Nullable
    private BlockPos controllerPos;

    public CoolerPortBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.COOLER_PORT, pos, state);
    }

    public void setController(BlockPos pos) {
        if (!pos.equals(controllerPos)) {
            controllerPos = pos.immutable();
            setChanged();
        }
    }

    @Nullable
    public CoolerControllerBlockEntity getController() {
        if (controllerPos == null || level == null || !level.hasChunkAt(controllerPos)) return null;
        if (level.getBlockEntity(controllerPos) instanceof CoolerControllerBlockEntity controller && controller.isFormed()) {
            return controller;
        }
        return null;
    }

    @Override
    public int receivePower(int amount) {
        CoolerControllerBlockEntity controller = getController();
        return controller == null ? 0 : controller.receivePower(amount);
    }

    @Override
    public boolean acceptBucket(FuelTankBlockEntity.FluidType type) {
        CoolerControllerBlockEntity controller = getController();
        return controller != null && controller.acceptBucket(type);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (controllerPos != null) {
            tag.putLong("Controller", controllerPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        controllerPos = tag.contains("Controller") ? BlockPos.of(tag.getLong("Controller")) : null;
    }
}
