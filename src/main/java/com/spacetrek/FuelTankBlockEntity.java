package com.spacetrek;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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

    public static final int MB_PER_BUCKET = 1000;
    public static final int BLOCK_CAPACITY_MB = 16 * MB_PER_BUCKET;
    private static final int MAX_GROUP_BLOCKS = 512;

    private FluidType fluidType = FluidType.EMPTY;
    private int amountMb = 0;
    private int groupTotalMb = 0;
    private int groupCapacityMb = BLOCK_CAPACITY_MB;

    public FuelTankBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FUEL_TANK, pos, state);
    }

    public FluidType getFluidType() { return fluidType; }
    public int getAmountMb() { return amountMb; }
    public int getGroupBuckets() { return groupTotalMb / MB_PER_BUCKET; }
    public int getGroupCapacityBuckets() { return groupCapacityMb / MB_PER_BUCKET; }
    public int getGroupBlocks() { return Math.max(1, groupCapacityMb / BLOCK_CAPACITY_MB); }

    public boolean canFill(FluidType type) {
        if (fluidType != FluidType.EMPTY && fluidType != type) return false;
        return groupTotalMb + MB_PER_BUCKET <= groupCapacityMb;
    }

    public boolean canDrain() {
        return fluidType != FluidType.EMPTY && groupTotalMb >= MB_PER_BUCKET;
    }

    private record GroupInfo(FluidType type, int total) {}

    public static List<FuelTankBlockEntity> collectGroup(Level level, BlockPos start) {
        List<FuelTankBlockEntity> result = new ArrayList<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos origin = start.immutable();
        seen.add(origin);
        queue.add(origin);

        while (!queue.isEmpty() && result.size() < MAX_GROUP_BLOCKS) {
            BlockPos current = queue.poll();
            if (!level.hasChunkAt(current)) continue;
            if (!level.getBlockState(current).is(ModBlocks.FUEL_TANK_BLOCK)) continue;
            if (!(level.getBlockEntity(current) instanceof FuelTankBlockEntity tank)) continue;
            result.add(tank);
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (seen.add(next)) queue.add(next);
            }
        }

        return result;
    }

    private static GroupInfo summarize(List<FuelTankBlockEntity> group) {
        FluidType type = FluidType.EMPTY;
        int total = 0;
        for (FuelTankBlockEntity tank : group) {
            if (tank.amountMb <= 0 || tank.fluidType == FluidType.EMPTY) continue;
            if (type == FluidType.EMPTY) type = tank.fluidType;
            if (tank.fluidType == type) total += tank.amountMb;
        }
        return new GroupInfo(type, total);
    }

    private static void applyGroup(List<FuelTankBlockEntity> group, FluidType type, int totalMb) {
        List<FuelTankBlockEntity> sorted = new ArrayList<>(group);
        sorted.sort(Comparator.comparingInt((FuelTankBlockEntity t) -> t.worldPosition.getY())
                .thenComparingInt(t -> t.worldPosition.getX())
                .thenComparingInt(t -> t.worldPosition.getZ()));

        int capacity = sorted.size() * BLOCK_CAPACITY_MB;
        int total = Math.max(0, Math.min(totalMb, capacity));
        FluidType groupType = total > 0 ? type : FluidType.EMPTY;
        int remaining = total;
        int index = 0;

        while (index < sorted.size()) {
            int layerY = sorted.get(index).worldPosition.getY();
            int end = index;
            while (end < sorted.size() && sorted.get(end).worldPosition.getY() == layerY) end++;

            int count = end - index;
            int take = Math.min(remaining, count * BLOCK_CAPACITY_MB);
            int base = take / count;
            int extra = take % count;

            for (int i = 0; i < count; i++) {
                sorted.get(index + i).setShare(groupType, base + (i < extra ? 1 : 0), total, capacity);
            }

            remaining -= take;
            index = end;
        }
    }

    private void setShare(FluidType type, int amount, int total, int capacity) {
        boolean changed = fluidType != type || amountMb != amount
                || groupTotalMb != total || groupCapacityMb != capacity;
        fluidType = type;
        amountMb = amount;
        groupTotalMb = total;
        groupCapacityMb = capacity;
        if (changed) {
            setChanged();
            if (level != null) {
                BlockState state = getBlockState();
                level.sendBlockUpdated(worldPosition, state, state, 3);
            }
        }
    }

    public static void rebalance(Level level, BlockPos pos) {
        List<FuelTankBlockEntity> group = collectGroup(level, pos);
        if (group.isEmpty()) return;
        GroupInfo info = summarize(group);
        applyGroup(group, info.type(), info.total());
    }

    public static boolean fillGroup(Level level, BlockPos pos, FluidType type) {
        List<FuelTankBlockEntity> group = collectGroup(level, pos);
        if (group.isEmpty()) return false;
        GroupInfo info = summarize(group);
        if (info.type() != FluidType.EMPTY && info.type() != type) return false;
        if (info.total() + MB_PER_BUCKET > group.size() * BLOCK_CAPACITY_MB) return false;
        applyGroup(group, type, info.total() + MB_PER_BUCKET);
        return true;
    }

    @Nullable
    public static ItemStack drainGroup(Level level, BlockPos pos) {
        List<FuelTankBlockEntity> group = collectGroup(level, pos);
        if (group.isEmpty()) return null;
        GroupInfo info = summarize(group);
        if (info.type() == FluidType.EMPTY || info.total() < MB_PER_BUCKET) return null;

        ItemStack result = switch (info.type()) {
            case WATER -> new ItemStack(Items.WATER_BUCKET);
            case ROCKET_FUEL -> new ItemStack(ModItems.ROCKET_FUEL_BUCKET);
            case OXYGEN -> new ItemStack(ModItems.OXYGEN_BUCKET);
            default -> null;
        };
        if (result == null) return null;

        applyGroup(group, info.type(), info.total() - MB_PER_BUCKET);
        return result;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("FluidType", fluidType.name());
        tag.putInt("AmountMb", amountMb);
        tag.putInt("GroupTotalMb", groupTotalMb);
        tag.putInt("GroupCapacityMb", groupCapacityMb);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        try {
            fluidType = FluidType.valueOf(tag.getString("FluidType"));
        } catch (Exception e) {
            fluidType = FluidType.EMPTY;
        }

        if (tag.contains("AmountMb")) {
            amountMb = tag.getInt("AmountMb");
            groupTotalMb = tag.getInt("GroupTotalMb");
            groupCapacityMb = tag.contains("GroupCapacityMb") ? tag.getInt("GroupCapacityMb") : BLOCK_CAPACITY_MB;
        } else {
            amountMb = tag.getInt("Buckets") * MB_PER_BUCKET;
            groupTotalMb = amountMb;
            groupCapacityMb = BLOCK_CAPACITY_MB;
        }
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
