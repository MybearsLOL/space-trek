package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public class FuelSynthesizerBlockEntity extends BlockEntity {

    public static final int CAPACITY = 8000;
    public static final int BUCKET_AMOUNT = 1000;
    public static final int PROCESS_TICKS = 200;
    public static final int KEROSENE_COST = 1000;
    public static final int OXYGEN_COST = 1000;
    public static final int FUEL_YIELD = 2000;

    private int kerosene = 0;
    private int oxygen = 0;
    private int rocketFuel = 0;
    private int progress = 0;

    public FuelSynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_SYNTHESIZER, pos, state);
    }

    public int getKerosene() { return kerosene; }
    public int getOxygen() { return oxygen; }
    public int getRocketFuel() { return rocketFuel; }
    public int getProgress() { return progress; }

    public boolean addKerosene() {
        if (kerosene + BUCKET_AMOUNT > CAPACITY) return false;
        kerosene += BUCKET_AMOUNT;
        sync();
        return true;
    }

    public boolean addOxygen() {
        if (oxygen + BUCKET_AMOUNT > CAPACITY) return false;
        oxygen += BUCKET_AMOUNT;
        sync();
        return true;
    }

    public boolean takeRocketFuel() {
        if (rocketFuel < BUCKET_AMOUNT) return false;
        rocketFuel -= BUCKET_AMOUNT;
        sync();
        return true;
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
        }
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        boolean changed = false;

        if (progress > 0) {
            progress++;
            if (progress >= PROCESS_TICKS) {
                progress = 0;
                rocketFuel = Math.min(CAPACITY, rocketFuel + FUEL_YIELD);
            }
            changed = true;
        } else if (kerosene >= KEROSENE_COST && oxygen >= OXYGEN_COST && rocketFuel + FUEL_YIELD <= CAPACITY) {
            kerosene -= KEROSENE_COST;
            oxygen -= OXYGEN_COST;
            progress = 1;
            changed = true;
        }

        boolean lit = progress > 0;
        if (state.getValue(FuelSynthesizerBlock.LIT) != lit) {
            level.setBlock(pos, state.setValue(FuelSynthesizerBlock.LIT, lit), 3);
        }

        // Sync about once a second while working, so the HUD stays fresh
        if (changed && (progress == 0 || progress % 20 == 0 || progress == 1)) {
            sync();
        } else if (changed) {
            setChanged();
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Kerosene", kerosene);
        tag.putInt("Oxygen", oxygen);
        tag.putInt("RocketFuel", rocketFuel);
        tag.putInt("Progress", progress);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        kerosene = tag.getInt("Kerosene");
        oxygen = tag.getInt("Oxygen");
        rocketFuel = tag.getInt("RocketFuel");
        progress = tag.getInt("Progress");
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
