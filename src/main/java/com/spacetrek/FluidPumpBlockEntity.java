package com.spacetrek;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

public class FluidPumpBlockEntity extends BlockEntity {

    public static final int PUMP_INTERVAL = 20;
    public static final int ACTIVE_HOLD = 30;
    private static final float EXPLOSION_POWER = 3.5f;
    private static final int SEARCH_RADIUS = 10;
    private static final int SEARCH_LIMIT = 400;

    private long lastPush = -1000L;

    private List<FluidNetwork.PipeNode> clientPipes = List.of();
    private long clientPipesTime = -1000L;

    public FluidPumpBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.FLUID_PUMP, pos, state);
    }

    public static boolean touchesLava(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (level.getFluidState(pos.relative(direction)).is(FluidTags.LAVA)) return true;
        }
        return level.getFluidState(pos).is(FluidTags.LAVA);
    }

    @Nullable
    public static FuelTankBlockEntity.FluidType typeOf(FluidState state) {
        if (state.isEmpty()) return null;
        Fluid fluid = state.getType();
        if (state.is(FluidTags.WATER)) return FuelTankBlockEntity.FluidType.WATER;
        if (matches(fluid, ModFluids.CRUDE_OIL_SOURCE, ModFluids.CRUDE_OIL_FLOWING)) return FuelTankBlockEntity.FluidType.CRUDE_OIL;
        if (matches(fluid, ModFluids.COOLED_CRUDE_SOURCE, ModFluids.COOLED_CRUDE_FLOWING)) return FuelTankBlockEntity.FluidType.COOLED_CRUDE;
        if (matches(fluid, ModFluids.KEROSENE_SOURCE, ModFluids.KEROSENE_FLOWING)) return FuelTankBlockEntity.FluidType.KEROSENE;
        if (matches(fluid, ModFluids.OXYGEN_SOURCE, ModFluids.OXYGEN_FLOWING)) return FuelTankBlockEntity.FluidType.OXYGEN;
        if (matches(fluid, ModFluids.ROCKET_FUEL_SOURCE, ModFluids.ROCKET_FUEL_FLOWING)) return FuelTankBlockEntity.FluidType.ROCKET_FUEL;
        return null;
    }

    @Nullable
    public static FuelTankBlockEntity.FluidType detectFluid(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            FuelTankBlockEntity.FluidType type = typeOf(level.getFluidState(pos.relative(direction)));
            if (type != null) return type;
        }
        return null;
    }

    @Nullable
    private static BlockPos findSource(Level level, BlockPos pump, FuelTankBlockEntity.FluidType type) {
        Set<BlockPos> visited = new HashSet<>();
        Deque<BlockPos> queue = new ArrayDeque<>();
        for (Direction direction : Direction.values()) {
            BlockPos start = pump.relative(direction);
            if (typeOf(level.getFluidState(start)) == type && visited.add(start)) queue.add(start);
        }

        BlockPos best = null;
        while (!queue.isEmpty() && visited.size() < SEARCH_LIMIT) {
            BlockPos current = queue.poll();
            FluidState fluid = level.getFluidState(current);
            if (fluid.isSource() && level.getBlockState(current).getBlock() instanceof LiquidBlock) {
                if (best == null || current.getY() > best.getY()
                        || (current.getY() == best.getY() && current.distSqr(pump) < best.distSqr(pump))) {
                    best = current;
                }
            }
            for (Direction direction : Direction.values()) {
                BlockPos next = current.relative(direction);
                if (!level.hasChunkAt(next) || next.distManhattan(pump) > SEARCH_RADIUS) continue;
                if (typeOf(level.getFluidState(next)) == type && visited.add(next)) queue.add(next);
            }
        }
        return best;
    }

    private static boolean matches(Fluid actual, Fluid source, Fluid flowing) {
        return actual == source || actual == flowing;
    }

    public List<FluidNetwork.PipeNode> getClientPipes(Level level) {
        if (level.getGameTime() - clientPipesTime >= 10L) {
            clientPipes = FluidNetwork.collectPipes(level, worldPosition);
            clientPipesTime = level.getGameTime();
        }
        return clientPipes;
    }

    public void serverTick(Level level, BlockPos pos, BlockState state) {
        long time = level.getGameTime();

        if (time % 4 == 0 && touchesLava(level, pos)) {
            level.removeBlock(pos, false);
            level.explode(null, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                    EXPLOSION_POWER, Level.ExplosionInteraction.BLOCK);
            return;
        }

        if (time % PUMP_INTERVAL == 0) {
            FuelTankBlockEntity.FluidType type = detectFluid(level, pos);
            if (type != null) {
                BlockPos source = type == FuelTankBlockEntity.FluidType.WATER ? null : findSource(level, pos, type);
                boolean canPump = type == FuelTankBlockEntity.FluidType.WATER || source != null;
                if (canPump && FluidNetwork.pushBucket(level, pos, type)) {
                    lastPush = time;
                    if (source != null) {
                        level.setBlock(source, Blocks.AIR.defaultBlockState(), 3);
                    }
                }
            }
        }

        boolean active = time - lastPush < ACTIVE_HOLD;
        if (state.getValue(FluidPumpBlock.ACTIVE) != active) {
            level.setBlock(pos, state.setValue(FluidPumpBlock.ACTIVE, active), 3);
        }
    }
}
