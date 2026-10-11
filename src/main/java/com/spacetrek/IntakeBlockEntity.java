package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class IntakeBlockEntity extends BlockEntity {

    public static final int MAX_BUCKETS = 8;
    // Generates 1 bucket of oxygen every 200 ticks (10 seconds)
    private static final int GENERATE_INTERVAL = 200;

    private int buckets = 0;
    private int tickCounter = 0;

    public IntakeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INTAKE, pos, state);
    }

    public int getBuckets() { return buckets; }
    public int getMaxBuckets() { return MAX_BUCKETS; }

    public void tick(Level level, BlockPos pos, BlockState state) {
        tickCounter++;
        if (tickCounter >= GENERATE_INTERVAL) {
            tickCounter = 0;
            if (buckets < MAX_BUCKETS) {
                buckets++;
                setChanged();
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    @Nullable
    public ItemStack drain() {
        if (buckets <= 0) return null;
        buckets--;
        setChanged();
        return new ItemStack(ModItems.OXYGEN_BUCKET);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Buckets", buckets);
        tag.putInt("TickCounter", tickCounter);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buckets = tag.getInt("Buckets");
        tickCounter = tag.getInt("TickCounter");
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