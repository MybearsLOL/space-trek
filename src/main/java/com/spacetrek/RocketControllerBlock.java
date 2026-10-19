package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class RocketControllerBlock extends Block {

    public RocketControllerBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                               Player player, BlockHitResult hit) {
        if (level.isClientSide) return InteractionResult.SUCCESS;

        RocketAssembler.Result result = RocketAssembler.assemble((ServerLevel) level, pos);
        Component message = switch (result) {
            case OK -> Component.translatable("message.space-trek.rocket_assembled");
            case TOO_LARGE -> Component.translatable("message.space-trek.rocket_too_big");
        };
        player.displayClientMessage(message, true);
        return InteractionResult.CONSUME;
    }
}
