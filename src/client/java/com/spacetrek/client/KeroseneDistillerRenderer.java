package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.spacetrek.KeroseneDistillerBlock;
import com.spacetrek.KeroseneDistillerBlockEntity;
import com.spacetrek.ModFluids;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class KeroseneDistillerRenderer implements BlockEntityRenderer<KeroseneDistillerBlockEntity> {

    private static final float[] CRUDE = {0.12f, 0.1f, 0.1f};
    private static final float[] KEROSENE = {0.85f, 0.12f, 0.1f};
    private static final float[] VAPOR = {0.9f, 0.8f, 0.7f};

    private static final float TANK_Y0 = 3.6f;
    private static final float TANK_HEIGHT = 7.8f;
    private static final float COLUMN_Y0 = 9.6f;
    private static final float COLUMN_HEIGHT = 4.8f;

    public KeroseneDistillerRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(KeroseneDistillerBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (entity.getLevel() == null) return;

        float time = entity.getLevel().getGameTime() + partialTick;
        float f = entity.getProgressFraction(partialTick);
        boolean running = f > 0f;
        float cap = KeroseneDistillerBlockEntity.MAX_FLUID;

        float crudeShown = entity.getCrude();
        float keroseneShown = entity.getKerosene();
        if (running) {
            crudeShown += KeroseneDistillerBlockEntity.CRUDE_PER_BATCH * (1f - MachineRender.clamp01(f / 0.1f));
            keroseneShown += KeroseneDistillerBlockEntity.KEROSENE_PER_BATCH * MachineRender.clamp01((f - 0.9f) / 0.1f);
        }

        TextureAtlasSprite sprite = MachineRender.fluidSprite(ModFluids.KEROSENE_SOURCE);

        poseStack.pushPose();
        MachineRender.applyFacing(poseStack, entity.getBlockState().getValue(KeroseneDistillerBlock.FACING));

        VertexConsumer fluid = bufferSource.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();

        tank(fluid, pose, sprite, 11.5f, MachineRender.clamp01(crudeShown / cap), CRUDE, packedLight, packedOverlay);
        tank(fluid, pose, sprite, 1.5f, MachineRender.clamp01(keroseneShown / cap), KEROSENE, packedLight, packedOverlay);

        if (running) {
            float boil = 1.1f + 0.2f * (float) Math.sin(time * 0.7f);
            MachineRender.box(fluid, pose, sprite, 6.6f, COLUMN_Y0, 5.6f, 9.4f, COLUMN_Y0 + boil, 10.4f,
                    CRUDE[0], CRUDE[1], CRUDE[2], 0.95f, packedLight, packedOverlay);

            float condense = MachineRender.clamp01((f - 0.3f) / 0.7f);
            float vaporLow = COLUMN_Y0 + boil;
            float vaporTop = COLUMN_Y0 + COLUMN_HEIGHT - 0.1f;
            float pulse = 0.18f + 0.1f * (float) Math.sin(time * 0.35f);
            if (vaporTop - vaporLow > 0.1f) {
                MachineRender.box(fluid, pose, sprite, 6.6f, vaporLow, 5.6f, 9.4f, vaporTop, 10.4f,
                        VAPOR[0], VAPOR[1], VAPOR[2], pulse, packedLight, packedOverlay);
            }
            float drip = 2.4f * condense;
            if (drip > 0.1f) {
                MachineRender.box(fluid, pose, sprite, 7.4f, vaporLow, 7.4f, 8.6f, vaporLow + drip, 8.6f,
                        KEROSENE[0], KEROSENE[1], KEROSENE[2], 0.85f, packedLight, packedOverlay);
            }

            drawCog(bufferSource, poseStack, time * 14f, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private static void tank(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite,
                             float x1, float fraction, float[] color, int light, int overlay) {
        float h = TANK_HEIGHT * fraction;
        if (h < 0.05f) return;
        MachineRender.box(vc, pose, sprite, x1, TANK_Y0, 4.5f, x1 + 3f, TANK_Y0 + h, 11.5f,
                color[0], color[1], color[2], 0.92f, light, overlay);
    }

    private static void drawCog(MultiBufferSource bufferSource, PoseStack poseStack, float angle,
                                int light, int overlay) {
        TextureAtlasSprite metal = MachineRender.blockSprite("block/kerosene_distiller/steel");
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 12.0 / 16.0);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-0.5, 0.0, -12.0 / 16.0);
        PoseStack.Pose pose = poseStack.last();

        MachineRender.box(vc, pose, metal, 7.2f, 9.0f, 11.2f, 8.8f, 10.4f, 12.8f, 1f, 1f, 1f, 1f, light, overlay);
        MachineRender.box(vc, pose, metal, 6.0f, 9.3f, 11.6f, 10.0f, 10.1f, 12.4f, 1f, 1f, 1f, 1f, light, overlay);
        MachineRender.box(vc, pose, metal, 7.6f, 9.3f, 10.6f, 8.4f, 10.1f, 13.4f, 1f, 1f, 1f, 1f, light, overlay);

        poseStack.popPose();
    }
}
