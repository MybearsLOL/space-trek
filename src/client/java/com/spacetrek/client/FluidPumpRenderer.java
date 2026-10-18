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
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class FluidPumpRenderer implements BlockEntityRenderer<FluidPumpBlockEntity> {

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

        for (FluidNetwork.PipeNode node : entity.getClientPipes(level)) {
            BlockState pipe = level.getBlockState(node.pos());
            if (!(pipe.getBlock() instanceof FluidPipeBlock)) continue;

            float wave = 0.5f + 0.5f * (float) Math.sin(time * 0.32f - node.distance() * 0.9f);
            float e = 0.15f + 1.15f * wave;
            float t = 0.1f + e * 0.7f;
            float ox = (node.pos().getX() - origin.getX()) * 16f;
            float oy = (node.pos().getY() - origin.getY()) * 16f;
            float oz = (node.pos().getZ() - origin.getZ()) * 16f;
            int light = LevelRenderer.getLightColor(level, node.pos());
            float a = 0.55f + 0.3f * wave;

            MachineRender.box(vc, pose, fluid, ox + 5f - e, oy + 5f - e, oz + 5f - e, ox + 11f + e, oy + 11f + e, oz + 11f + e,
                    color[0], color[1], color[2], a, light, packedOverlay);

            if (pipe.getValue(FluidPipeBlock.NORTH)) {
                MachineRender.box(vc, pose, fluid, ox + 6f - t, oy + 6f - t, oz, ox + 10f + t, oy + 10f + t, oz + 5f,
                        color[0], color[1], color[2], a, light, packedOverlay);
            }
            if (pipe.getValue(FluidPipeBlock.SOUTH)) {
                MachineRender.box(vc, pose, fluid, ox + 6f - t, oy + 6f - t, oz + 11f, ox + 10f + t, oy + 10f + t, oz + 16f,
                        color[0], color[1], color[2], a, light, packedOverlay);
            }
            if (pipe.getValue(FluidPipeBlock.WEST)) {
                MachineRender.box(vc, pose, fluid, ox, oy + 6f - t, oz + 6f - t, ox + 5f, oy + 10f + t, oz + 10f + t,
                        color[0], color[1], color[2], a, light, packedOverlay);
            }
            if (pipe.getValue(FluidPipeBlock.EAST)) {
                MachineRender.box(vc, pose, fluid, ox + 11f, oy + 6f - t, oz + 6f - t, ox + 16f, oy + 10f + t, oz + 10f + t,
                        color[0], color[1], color[2], a, light, packedOverlay);
            }
            if (pipe.getValue(FluidPipeBlock.UP)) {
                MachineRender.box(vc, pose, fluid, ox + 6f - t, oy + 11f, oz + 6f - t, ox + 10f + t, oy + 16f, oz + 10f + t,
                        color[0], color[1], color[2], a, light, packedOverlay);
            }
            if (pipe.getValue(FluidPipeBlock.DOWN)) {
                MachineRender.box(vc, pose, fluid, ox + 6f - t, oy, oz + 6f - t, ox + 10f + t, oy + 5f, oz + 10f + t,
                        color[0], color[1], color[2], a, light, packedOverlay);
            }
        }
    }
}
