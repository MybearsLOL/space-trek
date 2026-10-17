package com.spacetrek;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class FluidNetwork {

    private static final int MAX_NODES = 256;

    private FluidNetwork() {
    }

    public static boolean pushBucket(Level level, BlockPos source, FuelTankBlockEntity.FluidType type) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<FluidSink> machines = new ArrayList<>();
        List<FluidSink> tanks = new ArrayList<>();

        BlockPos start = source.immutable();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty() && visited.size() < MAX_NODES) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!visited.add(next)) continue;
                if (!level.hasChunkAt(next)) continue;
                if (level.getBlockState(next).getBlock() instanceof FluidPipeBlock) {
                    queue.add(next);
                    continue;
                }
                BlockEntity entity = level.getBlockEntity(next);
                if (entity instanceof FuelTankBlockEntity) {
                    tanks.add((FluidSink) entity);
                } else if (entity instanceof FluidSink sink) {
                    machines.add(sink);
                }
            }
        }

        for (FluidSink sink : machines) {
            if (sink.acceptBucket(type)) return true;
        }
        for (FluidSink sink : tanks) {
            if (sink.acceptBucket(type)) return true;
        }
        return false;
    }
}
