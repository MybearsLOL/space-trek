package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

public class FuelTankBlock extends Block implements EntityBlock {

    public FuelTankBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new FuelTankBlockEntity(pos, state);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level,
                                               BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof FuelTankBlockEntity tank)) return InteractionResult.PASS;

        ItemStack held = player.getMainHandItem();

        // Fill tank with water bucket
        if (held.getItem() == Items.WATER_BUCKET) {
            if (tank.fill(FuelTankBlockEntity.FluidType.WATER)) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.BUCKET));
                tank.sync(level, pos, state);
                return InteractionResult.SUCCESS;
            }
        }

        // Fill tank with rocket fuel bucket
        if (held.getItem() == ModItems.ROCKET_FUEL_BUCKET) {
            if (tank.fill(FuelTankBlockEntity.FluidType.ROCKET_FUEL)) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.BUCKET));
                tank.sync(level, pos, state);
                return InteractionResult.SUCCESS;
            }
        }

        // Fill tank with oxygen bucket
        if (held.getItem() == ModItems.OXYGEN_BUCKET) {
            if (tank.fill(FuelTankBlockEntity.FluidType.OXYGEN)) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND,
                        new ItemStack(Items.BUCKET));
                tank.sync(level, pos, state);
                return InteractionResult.SUCCESS;
            }
        }

        // Drain tank with empty bucket
        if (held.getItem() == Items.BUCKET) {
            ItemStack result = tank.drain();
            if (result != null) {
                player.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, result);
                tank.sync(level, pos, state);
                return InteractionResult.SUCCESS;
            }
        }

        return InteractionResult.PASS;
    }
}