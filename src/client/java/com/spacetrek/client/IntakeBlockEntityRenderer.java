package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.spacetrek.IntakeBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.level.Level;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public class IntakeBlockEntityRenderer implements BlockEntityRenderer<IntakeBlockEntity> {

    // Fan pivot in block space (0-1), matching Blockbench coords / 16
    private static final float PIVOT_X = 6f / 16f;
    private static final float PIVOT_Y = 3f / 16f;
    private static final float PIVOT_Z = 4f / 16f;

    // Blade dimensions relative to pivot center
    private static final float BLADE_HALF_LENGTH = 3f / 16f;
    private static final float BLADE_HALF_THICKNESS = 0.5f / 16f;
    private static final float BLADE_DEPTH = 1f / 16f;

    // Fan spin speed in degrees per tick
    private static final float SPIN_SPEED = 6f;

    // Gray color for the fan blades
    private static final float R = 0.6f, G = 0.6f, B = 0.6f, A = 1.0f;

    public IntakeBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(IntakeBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        Level level = entity.getLevel();
        if (level == null) return;

        // Calculate spin angle from game time + partial tick
        float angle = ((level.getGameTime() % 60) + partialTick) * SPIN_SPEED;

        poseStack.pushPose();

        // Translate to pivot point
        poseStack.translate(PIVOT_X, PIVOT_Y, PIVOT_Z);

        // Rotate around Z axis
        poseStack.mulPose(new org.joml.Quaternionf().rotationZ((float) Math.toRadians(angle)));

        VertexConsumer vc = bufferSource.getBuffer(RenderType.solid());
        Matrix4f matrix = poseStack.last().pose();

        // Draw 4 blades: top, bottom, left, right
        // Top blade
        drawBox(vc, matrix, poseStack,
                -BLADE_HALF_THICKNESS, 0,               -BLADE_DEPTH / 2,
                BLADE_HALF_THICKNESS, BLADE_HALF_LENGTH, BLADE_DEPTH / 2,
                packedLight, packedOverlay);

        // Bottom blade
        drawBox(vc, matrix, poseStack,
                -BLADE_HALF_THICKNESS, -BLADE_HALF_LENGTH, -BLADE_DEPTH / 2,
                BLADE_HALF_THICKNESS,  0,                  BLADE_DEPTH / 2,
                packedLight, packedOverlay);

        // Right blade
        drawBox(vc, matrix, poseStack,
                0,                BLADE_HALF_THICKNESS,  -BLADE_DEPTH / 2,
                BLADE_HALF_LENGTH, -BLADE_HALF_THICKNESS,  BLADE_DEPTH / 2,
                packedLight, packedOverlay);

        // Left blade
        drawBox(vc, matrix, poseStack,
                -BLADE_HALF_LENGTH, -BLADE_HALF_THICKNESS, -BLADE_DEPTH / 2,
                0,                  BLADE_HALF_THICKNESS,  BLADE_DEPTH / 2,
                packedLight, packedOverlay);

        // Small center hub
        drawBox(vc, matrix, poseStack,
                -BLADE_HALF_THICKNESS, -BLADE_HALF_THICKNESS, -BLADE_DEPTH / 2,
                BLADE_HALF_THICKNESS,  BLADE_HALF_THICKNESS,  BLADE_DEPTH / 2,
                packedLight, packedOverlay);

        poseStack.popPose();
    }

    private void drawBox(VertexConsumer vc, Matrix4f matrix, PoseStack poseStack,
                         float x1, float y1, float z1,
                         float x2, float y2, float z2,
                         int light, int overlay) {

        PoseStack.Pose pose = poseStack.last();

        // South face (z2)
        quad(vc, pose, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, light, overlay, new Vector3f(0,0,1));
        // North face (z1)
        quad(vc, pose, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, light, overlay, new Vector3f(0,0,-1));
        // Top face (y2)
        quad(vc, pose, x1,y2,z1, x2,y2,z1, x2,y2,z2, x1,y2,z2, light, overlay, new Vector3f(0,1,0));
        // Bottom face (y1)
        quad(vc, pose, x1,y1,z2, x2,y1,z2, x2,y1,z1, x1,y1,z1, light, overlay, new Vector3f(0,-1,0));
        // East face (x2)
        quad(vc, pose, x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, light, overlay, new Vector3f(1,0,0));
        // West face (x1)
        quad(vc, pose, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, light, overlay, new Vector3f(-1,0,0));
    }

    private void quad(VertexConsumer vc, PoseStack.Pose pose,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      int light, int overlay, Vector3f normal) {
        vc.addVertex(pose.pose(), x1, y1, z1).setColor(R,G,B,A).setUv(0,0).setOverlay(overlay).setLight(light).setNormal(pose, normal.x, normal.y, normal.z);
        vc.addVertex(pose.pose(), x2, y2, z2).setColor(R,G,B,A).setUv(1,0).setOverlay(overlay).setLight(light).setNormal(pose, normal.x, normal.y, normal.z);
        vc.addVertex(pose.pose(), x3, y3, z3).setColor(R,G,B,A).setUv(1,1).setOverlay(overlay).setLight(light).setNormal(pose, normal.x, normal.y, normal.z);
        vc.addVertex(pose.pose(), x4, y4, z4).setColor(R,G,B,A).setUv(0,1).setOverlay(overlay).setLight(light).setNormal(pose, normal.x, normal.y, normal.z);
    }
}