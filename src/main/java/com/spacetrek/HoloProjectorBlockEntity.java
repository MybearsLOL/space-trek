package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public class HoloProjectorBlockEntity extends BlockEntity {

    public static final int DISTANCE = 2;

    public HoloProjectorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.HOLO_PROJECTOR, pos, state);
    }
}
