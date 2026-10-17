package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FuelTankBlock extends Block implements EntityBlock {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    public FuelTankBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    public static BooleanProperty property(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelTankBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        FuelTankBlockEntity.FluidType found = FuelTankBlockEntity.FluidType.EMPTY;

        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            boolean connected = level.getBlockState(neighborPos).is(this);
            state = state.setValue(property(direction), connected);

            if (connected && level.getBlockEntity(neighborPos) instanceof FuelTankBlockEntity tank) {
                FuelTankBlockEntity.FluidType type = tank.getFluidType();
                if (type != FuelTankBlockEntity.FluidType.EMPTY) {
                    if (found == FuelTankBlockEntity.FluidType.EMPTY) {
                        found = type;
                    } else if (found != type) {
                        return null;
                    }
                }
            }
        }

        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                     LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        return state.setValue(property(direction), neighborState.is(this));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        FuelTankBlockEntity.rebalance(level, pos);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && !level.isClientSide) {
            for (Direction direction : Direction.values()) {
                BlockPos neighborPos = pos.relative(direction);
                if (level.getBlockState(neighborPos).is(this)) {
                    level.scheduleTick(neighborPos, this, 1);
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                              Player player, InteractionHand hand, BlockHitResult hit) {
        FuelTankBlockEntity.FluidType fillType = fluidFor(stack);
        boolean drain = stack.is(Items.BUCKET);
        if (fillType == null && !drain) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FuelTankBlockEntity tank)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;

        if (fillType != null) {
            if (!tank.canFill(fillType)) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
            if (!level.isClientSide && FuelTankBlockEntity.fillGroup(level, pos, fillType)) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, new ItemStack(Items.BUCKET)));
                level.playSound(null, pos, SoundEvents.BUCKET_EMPTY, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }

        if (!tank.canDrain()) return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        if (!level.isClientSide) {
            ItemStack result = FuelTankBlockEntity.drainGroup(level, pos);
            if (result != null) {
                player.setItemInHand(hand, ItemUtils.createFilledResult(stack, player, result));
                level.playSound(null, pos, SoundEvents.BUCKET_FILL, SoundSource.BLOCKS, 1.0f, 1.0f);
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Nullable
    private static FuelTankBlockEntity.FluidType fluidFor(ItemStack stack) {
        if (stack.is(Items.WATER_BUCKET)) return FuelTankBlockEntity.FluidType.WATER;
        if (stack.is(ModItems.ROCKET_FUEL_BUCKET)) return FuelTankBlockEntity.FluidType.ROCKET_FUEL;
        if (stack.is(ModItems.OXYGEN_BUCKET)) return FuelTankBlockEntity.FluidType.OXYGEN;
        if (stack.is(ModItems.KEROSENE_BUCKET)) return FuelTankBlockEntity.FluidType.KEROSENE;
        if (stack.is(ModItems.CRUDE_OIL_BUCKET)) return FuelTankBlockEntity.FluidType.CRUDE_OIL;
        return null;
    }
}
