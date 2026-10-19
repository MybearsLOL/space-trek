package com.spacetrek;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class RocketAssembler {

    public enum Result { OK, NO_ENGINE, TOO_LARGE }

    public static final int MAX_BLOCKS = 512;
    public static final TagKey<Block> ROCKET_BLOCKS =
            TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath("space-trek", "rocket_blocks"));

    private RocketAssembler() {
    }

    private static boolean isPart(ServerLevel level, BlockPos pos, BlockState state) {
        if (state.isAir()) return false;
        if (state.getDestroySpeed(level, pos) < 0) return false;
        return state.is(ModBlocks.ROCKET_CONTROLLER_BLOCK) || state.is(ROCKET_BLOCKS);
    }

    public static Result assemble(ServerLevel level, BlockPos controller) {
        Set<BlockPos> found = new LinkedHashSet<>();
        Set<BlockPos> seen = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        BlockPos start = controller.immutable();
        seen.add(start);
        queue.add(start);

        while (!queue.isEmpty()) {
            BlockPos current = queue.poll();
            if (!level.hasChunkAt(current)) continue;
            BlockState state = level.getBlockState(current);
            if (!isPart(level, current, state)) continue;
            found.add(current);
            if (found.size() > MAX_BLOCKS) return Result.TOO_LARGE;
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (seen.add(next)) queue.add(next);
            }
        }

        int engines = 0;
        for (BlockPos pos : found) {
            if (level.getBlockState(pos).is(ModBlocks.ROCKET_ENGINE_BLOCK)) engines++;
        }
        if (engines == 0) return Result.NO_ENGINE;

        List<FuelTankBlockEntity> tanks = new ArrayList<>();
        for (BlockPos pos : found) {
            if (level.getBlockEntity(pos) instanceof FuelTankBlockEntity tank) tanks.add(tank);
        }
        int fuel = FuelTankBlockEntity.drainRocketFuel(tanks);

        List<RocketEntity.Part> parts = new ArrayList<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;

        for (BlockPos pos : found) {
            BlockState state = level.getBlockState(pos);
            BlockEntity be = level.getBlockEntity(pos);
            CompoundTag nbt = be != null ? be.saveWithFullMetadata(level.registryAccess()) : null;
            int rx = pos.getX() - controller.getX();
            int ry = pos.getY() - controller.getY();
            int rz = pos.getZ() - controller.getZ();
            parts.add(new RocketEntity.Part(rx, ry, rz, state, nbt));
            minX = Math.min(minX, rx);
            maxX = Math.max(maxX, rx);
            minY = Math.min(minY, ry);
            minZ = Math.min(minZ, rz);
            maxZ = Math.max(maxZ, rz);
        }

        for (BlockPos pos : found) {
            level.removeBlockEntity(pos);
        }
        for (BlockPos pos : found) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        }

        double x = controller.getX() + (minX + maxX + 1) / 2.0;
        double y = controller.getY() + minY;
        double z = controller.getZ() + (minZ + maxZ + 1) / 2.0;

        RocketEntity rocket = new RocketEntity(ModEntities.ROCKET, level);
        rocket.setParts(parts, fuel);
        rocket.moveTo(x, y, z, 0.0f, -90.0f);
        level.addFreshEntity(rocket);
        return Result.OK;
    }
}
