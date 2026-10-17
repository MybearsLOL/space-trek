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

public final class PowerNetwork {

    private static final int MAX_NODES = 256;

    private PowerNetwork() {
    }

    public static int distribute(Level level, BlockPos source, int amount) {
        if (amount <= 0) return 0;

        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        List<PowerAcceptor> sinks = new ArrayList<>();

        BlockPos start = source.immutable();
        visited.add(start);
        queue.add(start);

        while (!queue.isEmpty() && visited.size() < MAX_NODES) {
            BlockPos current = queue.poll();
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!visited.add(next)) continue;
                if (!level.hasChunkAt(next)) continue;
                if (level.getBlockState(next).getBlock() instanceof PowerCableBlock) {
                    queue.add(next);
                    continue;
                }
                BlockEntity entity = level.getBlockEntity(next);
                if (entity instanceof PowerAcceptor sink) {
                    sinks.add(sink);
                }
            }
        }

        int remaining = amount;
        int pending = sinks.size();
        for (PowerAcceptor sink : sinks) {
            int share = remaining / pending;
            pending--;
            remaining -= sink.receivePower(share);
        }
        return amount - remaining;
    }
}
