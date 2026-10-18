package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.spacetrek.FluidNetwork;
import com.spacetrek.FluidPipeBlock;
import com.spacetrek.FluidPumpBlock;
import com.spacetrek.FluidPumpBlockEntity;
import com.spacetrek.FuelTankBlockEntity;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class FluidPumpRenderer implements BlockEntityRenderer<FluidPumpBlockEntity> {

    private static final int SLUGS = 4;
    private static final float SPEED = 0.9f;
    private static final float SLUG_LENGTH = 2.6f;
    private static final float HALF = 2.4f;

    public FluidPumpRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public boolean shouldRenderOffScreen(FluidPumpBlockEntity entity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    private static float[] colorFor(FuelTankBlockEntity.FluidType type) {
        return switch (type) {
            case WATER -> new float[]{0.24f, 0.46f, 1.0f};
            case CRUDE_OIL -> new float[]{0.14f, 0.11f, 0.1f};
            case COOLED_CRUDE -> new float[]{0.36f, 0.22f, 0.13f};
            case KEROSENE -> new float[]{0.85f, 0.12f, 0.1f};
            case OXYGEN -> new float[]{0.67f, 0.87f, 1.0f};
            case ROCKET_FUEL -> new float[]{1.0f, 0.85f, 0.0f};
            default -> new float[]{1f, 1f, 1f};
        };
    }

    @Override
    public void render(FluidPumpBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null) return;

        BlockState state = entity.getBlockState();
        if (!state.getValue(FluidPumpBlock.ACTIVE)) return;

        float time = level.getGameTime() + partialTick;
        BlockPos origin = entity.getBlockPos();

        TextureAtlasSprite metal = MachineRender.blockSprite("block/fluid_pump/steel");
        VertexConsumer solid = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(time * 22f));
        poseStack.translate(-0.5, 0.0, -0.5);
        PoseStack.Pose spin = poseStack.last();
        MachineRender.box(solid, spin, metal, 3f, 12f, 7.2f, 13f, 13f, 8.8f, 1f, 1f, 1f, 1f, packedLight, packedOverlay);
        MachineRender.box(solid, spin, metal, 7.2f, 12f, 3f, 8.8f, 13f, 13f, 1f, 1f, 1f, 1f, packedLight, packedOverlay);
        MachineRender.box(solid, spin, metal, 7f, 11f, 7f, 9f, 13.6f, 9f, 1f, 1f, 1f, 1f, packedLight, packedOverlay);
        poseStack.popPose();

        FuelTankBlockEntity.FluidType type = FluidPumpBlockEntity.detectFluid(level, origin);
        if (type == null) return;
        float[] color = colorFor(type);

        TextureAtlasSprite fluid = MachineRender.fluidSprite(Fluids.WATER);
        VertexConsumer vc = bufferSource.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();

        List<FluidNetwork.PipeNode> nodes = entity.getClientPipes(level);
        Map<BlockPos, Integer> distances = new HashMap<>();
        for (FluidNetwork.PipeNode node : nodes) {
            distances.put(node.pos(), node.distance());
        }

        for (FluidNetwork.PipeNode node : nodes) {
            BlockState pipe = level.getBlockState(node.pos());
            if (!(pipe.getBlock() instanceof FluidPipeBlock)) continue;

            float ox = (node.pos().getX() - origin.getX()) * 16f;
            float oy = (node.pos().getY() - origin.getY()) * 16f;
            float oz = (node.pos().getZ() - origin.getZ()) * 16f;
            int light = LevelRenderer.getLightColor(level, node.pos());

            for (Direction dir : Direction.values()) {
                if (!connected(pipe, dir)) continue;
                Integer next = distances.get(node.pos().relative(dir));
                if (next == null || next <= node.distance()) continue;

                for (int k = 0; k < SLUGS; k++) {
                    float s = (time * SPEED + k * (16f / SLUGS)) % 16f;
                    float c0 = 8f + s;
                    float c1 = c0 + SLUG_LENGTH;
                    float a0 = 8f - HALF;
                    float a1 = 8f + HALF;
                    float x0 = a0, x1 = a1, y0 = a0, y1 = a1, z0 = a0, z1 = a1;
                    switch (dir) {
                        case NORTH -> { z0 = 16f - c1; z1 = 16f - c0; }
                        case SOUTH -> { z0 = c0; z1 = c1; }
                        case WEST -> { x0 = 16f - c1; x1 = 16f - c0; }
                        case EAST -> { x0 = c0; x1 = c1; }
                        case UP -> { y0 = c0; y1 = c1; }
                        default -> { y0 = 16f - c1; y1 = 16f - c0; }
                    }
                    MachineRender.box(vc, pose, fluid, ox + x0, oy + y0, oz + z0, ox + x1, oy + y1, oz + z1,
                            color[0], color[1], color[2], 0.85f, light, packedOverlay);
                }
            }
        }
    }

    private static boolean connected(BlockState pipe, Direction dir) {
        return switch (dir) {
            case NORTH -> pipe.getValue(FluidPipeBlock.NORTH);
            case SOUTH -> pipe.getValue(FluidPipeBlock.SOUTH);
            case WEST -> pipe.getValue(FluidPipeBlock.WEST);
            case EAST -> pipe.getValue(FluidPipeBlock.EAST);
            case UP -> pipe.getValue(FluidPipeBlock.UP);
            case DOWN -> pipe.getValue(FluidPipeBlock.DOWN);
        };
    }
}
