package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FuelTankBlockEntity extends BlockEntity {

    public enum FluidType { EMPTY, WATER, ROCKET_FUEL, OXYGEN }

    public static final int MAX_BUCKETS = 16;

    private FluidType fluidType = FluidType.EMPTY;
    private int buckets = 0;

    public FuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_TANK, pos, state);
    }

    public FluidType getFluidType() { return fluidType; }
    public int getBuckets() { return buckets; }
    public int getMaxBuckets() { return MAX_BUCKETS; }

    public boolean fill(FluidType type) {
        if (buckets >= MAX_BUCKETS) return false;
        if (fluidType != FluidType.EMPTY && fluidType != type) return false;
        fluidType = type;
        buckets++;
        setChanged();
        return true;
    }

    @Nullable
    public ItemStack drain() {
        if (buckets <= 0 || fluidType == FluidType.EMPTY) return null;
        buckets--;
        ItemStack result = switch (fluidType) {
            case WATER -> new ItemStack(Items.WATER_BUCKET);
            case ROCKET_FUEL -> new ItemStack(ModItems.ROCKET_FUEL_BUCKET);
            case OXYGEN -> new ItemStack(ModItems.OXYGEN_BUCKET);
            default -> null;
        };
        if (buckets == 0) fluidType = FluidType.EMPTY;
        setChanged();
        return result;
    }

    public void sync(Level level, BlockPos pos, BlockState state) {
        level.sendBlockUpdated(pos, state, state, 3);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("FluidType", fluidType.name());
        tag.putInt("Buckets", buckets);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            fluidType = FluidType.valueOf(tag.getString("FluidType"));
        } catch (Exception e) {
            fluidType = FluidType.EMPTY;
        }
        buckets = tag.getInt("Buckets");
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
}