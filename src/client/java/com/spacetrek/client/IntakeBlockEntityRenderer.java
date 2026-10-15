package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.spacetrek.IntakeBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

public class IntakeBlockEntityRenderer implements BlockEntityRenderer<IntakeBlockEntity> {

    private static final ResourceLocation FAN_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("space-trek", "block/intake");

    private static final float CENTER_X = 8f / 16f;
    private static final float CENTER_Y = 9f / 16f;
    private static final float CENTER_Z = 8f / 16f;

    private static final int BLADE_COUNT = 4;
    private static final float HUB_HALF_SIZE = 1f / 16f;
    private static final float HUB_HALF_DEPTH = 0.75f / 16f;
    private static final float BLADE_INNER = 0.75f / 16f;
    private static final float BLADE_OUTER = 4.5f / 16f;
    private static final float BLADE_HALF_WIDTH = 1.25f / 16f;
    private static final float BLADE_HALF_DEPTH = 0.25f / 16f;
    private static final float BLADE_PITCH_DEGREES = 25f;

    private static final float SPIN_DEGREES_PER_TICK = 6f;
    private static final int SPIN_PERIOD_TICKS = 60;

    public IntakeBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(IntakeBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null) return;

        float angle = ((level.getGameTime() % SPIN_PERIOD_TICKS) + partialTick) * SPIN_DEGREES_PER_TICK;

        TextureAtlasSprite sprite = Minecraft.getInstance()
                .getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(FAN_TEXTURE);

        float du = sprite.getU1() - sprite.getU0();
        float dv = sprite.getV1() - sprite.getV0();
        float u0 = sprite.getU0() + du * 0.05f;
        float u1 = sprite.getU0() + du * 0.45f;
        float v0 = sprite.getV0() + dv * 0.05f;
        float v1 = sprite.getV0() + dv * 0.45f;

        VertexConsumer vc = bufferSource.getBuffer(RenderType.entityCutoutNoCull(TextureAtlas.LOCATION_BLOCKS));

        poseStack.pushPose();
        poseStack.translate(CENTER_X, CENTER_Y, CENTER_Z);
        poseStack.mulPose(Axis.ZP.rotationDegrees(angle));

        drawBox(vc, poseStack.last(), u0, v0, u1, v1,
                -HUB_HALF_SIZE, -HUB_HALF_SIZE, -HUB_HALF_DEPTH,
                HUB_HALF_SIZE, HUB_HALF_SIZE, HUB_HALF_DEPTH,
                packedLight, packedOverlay);

        for (int i = 0; i < BLADE_COUNT; i++) {
            poseStack.pushPose();
            poseStack.mulPose(Axis.ZP.rotationDegrees(i * (360f / BLADE_COUNT)));
            poseStack.mulPose(Axis.YP.rotationDegrees(BLADE_PITCH_DEGREES));
            drawBox(vc, poseStack.last(), u0, v0, u1, v1,
                    -BLADE_HALF_WIDTH, BLADE_INNER, -BLADE_HALF_DEPTH,
                    BLADE_HALF_WIDTH, BLADE_OUTER, BLADE_HALF_DEPTH,
                    packedLight, packedOverlay);
            poseStack.popPose();
        }

        poseStack.popPose();
    }

    private static void drawBox(VertexConsumer vc, PoseStack.Pose pose,
                                float u0, float v0, float u1, float v1,
                                float x1, float y1, float z1,
                                float x2, float y2, float z2,
                                int light, int overlay) {
        quad(vc, pose, u0, v0, u1, v1, light, overlay, 0, 0, 1,
                x1, y1, z2, x2, y1, z2, x2, y2, z2, x1, y2, z2);
        quad(vc, pose, u0, v0, u1, v1, light, overlay, 0, 0, -1,
                x2, y1, z1, x1, y1, z1, x1, y2, z1, x2, y2, z1);
        quad(vc, pose, u0, v0, u1, v1, light, overlay, 0, 1, 0,
                x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1);
        quad(vc, pose, u0, v0, u1, v1, light, overlay, 0, -1, 0,
                x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2);
        quad(vc, pose, u0, v0, u1, v1, light, overlay, 1, 0, 0,
                x2, y1, z2, x2, y1, z1, x2, y2, z1, x2, y2, z2);
        quad(vc, pose, u0, v0, u1, v1, light, overlay, -1, 0, 0,
                x1, y1, z1, x1, y1, z2, x1, y2, z2, x1, y2, z1);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             float u0, float v0, float u1, float v1,
                             int light, int overlay,
                             float nx, float ny, float nz,
                             float ax, float ay, float az,
                             float bx, float by, float bz,
                             float cx, float cy, float cz,
                             float dx, float dy, float dz) {
        vertex(vc, pose, ax, ay, az, u0, v1, light, overlay, nx, ny, nz);
        vertex(vc, pose, bx, by, bz, u1, v1, light, overlay, nx, ny, nz);
        vertex(vc, pose, cx, cy, cz, u1, v0, light, overlay, nx, ny, nz);
        vertex(vc, pose, dx, dy, dz, u0, v0, light, overlay, nx, ny, nz);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               int light, int overlay,
                               float nx, float ny, float nz) {
        vc.addVertex(pose, x, y, z)
                .setColor(1f, 1f, 1f, 1f)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
