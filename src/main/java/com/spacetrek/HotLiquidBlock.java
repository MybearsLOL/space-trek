package com.spacetrek;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FlowingFluid;

public class HotLiquidBlock extends LiquidBlock {

    private final float damage;

    public HotLiquidBlock(FlowingFluid fluid, BlockBehaviour.Properties properties, float damage) {
        super(fluid, properties);
        this.damage = damage;
    }

    @Override
    protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!level.isClientSide && entity instanceof LivingEntity living && !living.fireImmune()) {
            living.hurt(level.damageSources().hotFloor(), damage);
        }
        super.entityInside(state, level, pos, entity);
    }
}
