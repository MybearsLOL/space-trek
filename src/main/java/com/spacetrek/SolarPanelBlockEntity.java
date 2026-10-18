package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class SolarPanelBlockEntity extends BlockEntity implements PowerNode {

    public enum Status { OK, NIGHT, BLOCKED, ANGLE }

    public static final int PEAK_OUTPUT = 20;
    public static final int MAX_ENERGY = 4000;
    public static final int TRANSFER_PER_TICK = 200;

    private int energy = 0;
    private int output = 0;
    private Status status = Status.NIGHT;

    public SolarPanelBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOLAR_PANEL, pos, state);
    }

    public int getOutput() {
        return output;
    }

    public Status getStatus() {
        return status;
    }

    public void refreshOutput(Level level, BlockPos pos, BlockState state) {
        output = 0;

        if (!level.dimensionType().hasSkyLight()) {
            status = Status.BLOCKED;
            return;
        }
        if (!level.canSeeSky(pos.above())) {
            status = Status.BLOCKED;
            return;
        }

        float angle = level.getTimeOfDay(1.0f) * Mth.TWO_PI;
        float elevation = Mth.cos(angle);
        float sunX = -Mth.sin(angle);
        if (elevation <= 0.02f) {
            status = Status.NIGHT;
            return;
        }

        Direction facing = state.getValue(SolarPanelBlock.FACING);
        float alignment = 0.7071f * (facing.getStepX() * sunX + elevation);
        float horizon = Mth.clamp(elevation * 4f, 0f, 1f);
        float efficiency = Mth.clamp(alignment, 0f, 1f) * horizon;

        if (level.isRainingAt(pos.above())) {
            efficiency *= 0.3f;
        }

        output = Math.round(PEAK_OUTPUT * efficiency);
        status = output > 0 ? Status.OK : Status.ANGLE;
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        if (level.getGameTime() % 10 == 0) {
            refreshOutput(level, pos, state);
        }

        if (output > 0 && energy < MAX_ENERGY) {
            energy = Math.min(MAX_ENERGY, energy + output);
            setChanged();
        }

        if (energy > 0) {
            int sent = PowerNetwork.distribute(level, pos, Math.min(energy, TRANSFER_PER_TICK));
            if (sent > 0) {
                energy -= sent;
                setChanged();
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Energy", energy);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        energy = tag.getInt("Energy");
    }
}
