package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.spacetrek.CoolerControllerBlock;
import com.spacetrek.CoolerControllerBlockEntity;
import com.spacetrek.ModFluids;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class CoolerRenderer implements BlockEntityRenderer<CoolerControllerBlockEntity> {

    private static final float[] HOT = {0.16f, 0.1f, 0.08f};
    private static final float[] COLD = {0.55f, 0.85f, 1.0f};
    private static final int ITEM_COUNT = 6;

    public CoolerRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(CoolerControllerBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (entity.getLevel() == null || !entity.isFormed()) return;

        float time = entity.getLevel().getGameTime() + partialTick;
        float f = entity.getProgressFraction(partialTick);
        boolean running = f > 0f;
        float spin = running ? time * 9f : 0f;

        TextureAtlasSprite fluidSprite = MachineRender.fluidSprite(ModFluids.COOLED_CRUDE_SOURCE);
        TextureAtlasSprite metal = MachineRender.blockSprite("block/cooler_controller/chrome");

        poseStack.pushPose();
        MachineRender.applyFacing(poseStack, entity.getBlockState().getValue(CoolerControllerBlock.FACING));

        poseStack.translate(0.5, 0.5, 0.0);
        poseStack.mulPose(Axis.ZP.rotationDegrees(spin));
        poseStack.translate(-0.5, -0.5, 0.0);

        VertexConsumer solid = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));
        PoseStack.Pose pose = poseStack.last();
        for (int i = 0; i < 4; i++) {
            poseStack.pushPose();
            poseStack.translate(0.5, 0.5, 0.0);
            poseStack.mulPose(Axis.ZP.rotationDegrees(i * 45f));
            poseStack.translate(-0.5, -0.5, 0.0);
            MachineRender.box(solid, poseStack.last(), metal, 3.2f, 7.6f, 3f, 12.8f, 8.4f, 12.5f,
                    1f, 1f, 1f, 1f, packedLight, packedOverlay);
            poseStack.popPose();
        }

        float t = running ? f : (entity.getCooled() > entity.getHot() ? 1f : 0f);
        float[] tint = new float[]{
                MachineRender.lerp(t, HOT[0], COLD[0]),
                MachineRender.lerp(t, HOT[1], COLD[1]),
                MachineRender.lerp(t, HOT[2], COLD[2])
        };

        if (running || entity.getHot() > 0 || entity.getCooled() > 0) {
            VertexConsumer fluid = bufferSource.getBuffer(RenderType.translucent());
            for (int i = 0; i < ITEM_COUNT; i++) {
                float a = (float) (i * Math.PI * 2.0 / ITEM_COUNT);
                float bob = running ? 0.4f * (float) Math.sin(time * 0.3f + i) : 0f;
                float cx = 8f + 2.8f * (float) Math.cos(a);
                float cy = 8f + 2.8f * (float) Math.sin(a) + bob;
                MachineRender.box(fluid, pose, fluidSprite, cx - 0.9f, cy - 0.9f, 5f, cx + 0.9f, cy + 0.9f, 7f,
                        tint[0], tint[1], tint[2], 0.95f, packedLight, packedOverlay);
            }
            MachineRender.box(fluid, pose, fluidSprite, 4.3f, 4.3f, 12f, 11.7f, 11.7f, 12.6f,
                    tint[0], tint[1], tint[2], 0.5f, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }
}
