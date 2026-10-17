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

public class FuelSynthesizerBlockEntity extends BlockEntity implements PowerAcceptor, FluidSink {

    public static final int CAPACITY = 8000;
    public static final int MAX_ENERGY = 12000;
    public static final int POWER_PER_TICK = 30;
    public static final int BUCKET_AMOUNT = 1000;
    public static final int PROCESS_TICKS = 200;
    public static final int KEROSENE_COST = 1000;
    public static final int OXYGEN_COST = 1000;
    public static final int FUEL_YIELD = 2000;

    private int kerosene = 0;
    private int oxygen = 0;
    private int rocketFuel = 0;
    private int progress = 0;
    private int energy = 0;
    private int syncedEnergy = 0;

    public FuelSynthesizerBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_SYNTHESIZER, pos, state);
    }

    public int getKerosene() { return kerosene; }
    public int getOxygen() { return oxygen; }
    public int getRocketFuel() { return rocketFuel; }
    public int getProgress() { return progress; }
    public int getEnergy() { return energy; }

    public float getProgressFraction(float partialTick) {
        if (progress <= 0) return 0f;
        float smooth = progress + (getBlockState().getValue(FuelSynthesizerBlock.LIT) ? partialTick : 0f);
        return Math.min(1f, smooth / PROCESS_TICKS);
    }

    @Override
    public int receivePower(int amount) {
        int accepted = Math.min(amount, MAX_ENERGY - energy);
        if (accepted <= 0) return 0;
        energy += accepted;
        setChanged();
        return accepted;
    }

    @Override
    public boolean acceptBucket(FuelTankBlockEntity.FluidType type) {
        return switch (type) {
            case KEROSENE -> addKerosene();
            case OXYGEN -> addOxygen();
            default -> false;
        };
    }

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
        boolean working = false;
        boolean needsSync = false;

        if (progress > 0) {
            if (energy >= POWER_PER_TICK) {
                energy -= POWER_PER_TICK;
                progress++;
                working = true;
                if (progress >= PROCESS_TICKS) {
                    progress = 0;
                    rocketFuel = Math.min(CAPACITY, rocketFuel + FUEL_YIELD);
                    needsSync = true;
                } else if (progress % 20 == 0) {
                    needsSync = true;
                }
            }
        } else if (kerosene >= KEROSENE_COST && oxygen >= OXYGEN_COST
                && rocketFuel + FUEL_YIELD <= CAPACITY && energy >= POWER_PER_TICK) {
            kerosene -= KEROSENE_COST;
            oxygen -= OXYGEN_COST;
            energy -= POWER_PER_TICK;
            progress = 1;
            working = true;
            needsSync = true;
        }

        if (rocketFuel >= BUCKET_AMOUNT && level.getGameTime() % 20 == 0
                && FluidNetwork.pushBucket(level, pos, FuelTankBlockEntity.FluidType.ROCKET_FUEL)) {
            rocketFuel -= BUCKET_AMOUNT;
            needsSync = true;
        }

        if (state.getValue(FuelSynthesizerBlock.LIT) != working) {
            level.setBlock(pos, state.setValue(FuelSynthesizerBlock.LIT, working), 3);
        }

        if (!needsSync && level.getGameTime() % 20 == 0 && syncedEnergy != energy) {
            needsSync = true;
        }

        if (needsSync) {
            syncedEnergy = energy;
            sync();
        } else if (working) {
            setChanged();
        }
    }

    public void clientTick(Level level, BlockPos pos, BlockState state) {
        if (state.getValue(FuelSynthesizerBlock.LIT) && progress > 0 && progress < PROCESS_TICKS) {
            progress++;
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Kerosene", kerosene);
        tag.putInt("Oxygen", oxygen);
        tag.putInt("RocketFuel", rocketFuel);
        tag.putInt("Progress", progress);
        tag.putInt("Energy", energy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        kerosene = tag.getInt("Kerosene");
        oxygen = tag.getInt("Oxygen");
        rocketFuel = tag.getInt("RocketFuel");
        progress = tag.getInt("Progress");
        energy = tag.getInt("Energy");
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
