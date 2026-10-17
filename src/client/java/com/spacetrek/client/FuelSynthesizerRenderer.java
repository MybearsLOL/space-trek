package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.spacetrek.FuelSynthesizerBlock;
import com.spacetrek.FuelSynthesizerBlockEntity;
import com.spacetrek.ModFluids;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;

public class FuelSynthesizerRenderer implements BlockEntityRenderer<FuelSynthesizerBlockEntity> {

    private static final float[] OXYGEN = {0.67f, 0.87f, 1.0f};
    private static final float[] KEROSENE = {0.85f, 0.12f, 0.1f};
    private static final float[] GOLD = {1.0f, 0.85f, 0.0f};

    private static final float CHAMBER_Y0 = 8.6f;
    private static final float CHAMBER_HEIGHT = 5.8f;
    private static final float TANK_Y0 = 3.6f;
    private static final float TANK_HEIGHT = 7.8f;
    private static final float OUTPUT_Y0 = 3.6f;
    private static final float OUTPUT_HEIGHT = 3.8f;
    private static final float ALPHA = 0.9f;

    public FuelSynthesizerRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(FuelSynthesizerBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        if (entity.getLevel() == null) return;

        float time = entity.getLevel().getGameTime() + partialTick;
        float f = entity.getProgressFraction(partialTick);
        boolean running = f > 0f;

        float tA = MachineRender.clamp01(f / 0.25f);
        float tB = MachineRender.clamp01((f - 0.25f) / 0.25f);
        float tC = MachineRender.clamp01((f - 0.5f) / 0.38f);
        float tD = MachineRender.clamp01((f - 0.88f) / 0.12f);

        float oxygenShown = entity.getOxygen();
        float keroseneShown = entity.getKerosene();
        float fuelShown = entity.getRocketFuel();
        if (running) {
            oxygenShown += FuelSynthesizerBlockEntity.OXYGEN_COST * (1f - tA);
            keroseneShown += FuelSynthesizerBlockEntity.KEROSENE_COST * (1f - tB);
            fuelShown += FuelSynthesizerBlockEntity.FUEL_YIELD * tD;
        }

        TextureAtlasSprite sprite = MachineRender.fluidSprite(ModFluids.OXYGEN_SOURCE);
        float cap = FuelSynthesizerBlockEntity.CAPACITY;

        poseStack.pushPose();
        MachineRender.applyFacing(poseStack, entity.getBlockState().getValue(FuelSynthesizerBlock.FACING));

        VertexConsumer fluid = bufferSource.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();

        level(fluid, pose, sprite, 11.5f, TANK_Y0, 4.5f, 14.5f, 11.5f, TANK_HEIGHT, oxygenOrKerosene(keroseneShown / cap), KEROSENE, packedLight, packedOverlay);
        level(fluid, pose, sprite, 1.5f, TANK_Y0, 4.5f, 4.5f, 11.5f, TANK_HEIGHT, oxygenOrKerosene(oxygenShown / cap), OXYGEN, packedLight, packedOverlay);
        level(fluid, pose, sprite, 6.5f, OUTPUT_Y0, 13.5f, 9.5f, 14.5f, OUTPUT_HEIGHT, fuelShown / cap, GOLD, packedLight, packedOverlay);

        if (running) {
            float scale = 1f - tD;
            float blueHeight = CHAMBER_HEIGHT * 0.5f * tA * scale;
            float redHeight = CHAMBER_HEIGHT * 0.5f * tB * scale;
            float[] blue = mix(OXYGEN, GOLD, tC);
            float[] red = mix(KEROSENE, GOLD, tC);
            float wobble = tC > 0f && tD < 1f ? 0.18f * (float) Math.sin(time * 0.9f) * tC : 0f;

            slab(fluid, pose, sprite, 5.6f, CHAMBER_Y0, 4.6f, 10.4f, CHAMBER_Y0 + blueHeight, 11.4f, blue, packedLight, packedOverlay);
            slab(fluid, pose, sprite, 5.6f, CHAMBER_Y0 + blueHeight, 4.6f, 10.4f, CHAMBER_Y0 + blueHeight + redHeight + wobble, 11.4f, red, packedLight, packedOverlay);

            float spin = f < 0.25f ? 0f : (f < 0.5f ? 6f : (f < 0.88f ? 38f : 10f));
            drawStirrer(bufferSource, poseStack, time * spin, packedLight, packedOverlay);
        }

        poseStack.popPose();
    }

    private static float oxygenOrKerosene(float fraction) {
        return MachineRender.clamp01(fraction);
    }

    private static float[] mix(float[] from, float[] to, float t) {
        return new float[]{
                MachineRender.lerp(t, from[0], to[0]),
                MachineRender.lerp(t, from[1], to[1]),
                MachineRender.lerp(t, from[2], to[2])
        };
    }

    private static void level(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite,
                              float x1, float y0, float z1, float x2, float z2, float height,
                              float fraction, float[] color, int light, int overlay) {
        float h = height * MachineRender.clamp01(fraction);
        if (h < 0.05f) return;
        MachineRender.box(vc, pose, sprite, x1, y0, z1, x2, y0 + h, z2, color[0], color[1], color[2], ALPHA, light, overlay);
    }

    private static void slab(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float[] color, int light, int overlay) {
        if (y2 - y1 < 0.05f) return;
        MachineRender.box(vc, pose, sprite, x1, y1, z1, x2, y2, z2, color[0], color[1], color[2], ALPHA, light, overlay);
    }

    private static void drawStirrer(MultiBufferSource bufferSource, PoseStack poseStack, float angle,
                                    int light, int overlay) {
        TextureAtlasSprite metal = MachineRender.blockSprite("block/fuel_synthesizer/dark");
        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));

        poseStack.pushPose();
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.translate(-0.5, 0.0, -0.5);
        PoseStack.Pose pose = poseStack.last();

        MachineRender.box(vc, pose, metal, 7.6f, CHAMBER_Y0, 7.6f, 8.4f, 14.4f, 8.4f, 1f, 1f, 1f, 1f, light, overlay);
        MachineRender.box(vc, pose, metal, 5.8f, 9.6f, 7.5f, 10.2f, 10.4f, 8.5f, 1f, 1f, 1f, 1f, light, overlay);
        MachineRender.box(vc, pose, metal, 7.5f, 10.8f, 5.8f, 8.5f, 11.6f, 10.2f, 1f, 1f, 1f, 1f, light, overlay);
        MachineRender.box(vc, pose, metal, 5.8f, 12.0f, 7.5f, 10.2f, 12.8f, 8.5f, 1f, 1f, 1f, 1f, light, overlay);

        poseStack.popPose();
    }
}
