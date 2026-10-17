package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;

final class MachineRender {

    private MachineRender() {
    }

    static float clamp01(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }

    static TextureAtlasSprite blockSprite(String path) {
        return Minecraft.getInstance()
                .getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(ResourceLocation.fromNamespaceAndPath("space-trek", path));
    }

    static TextureAtlasSprite fluidSprite(Fluid fluid) {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
        TextureAtlasSprite[] sprites = handler != null
                ? handler.getFluidSprites(null, null, fluid.defaultFluidState())
                : null;
        if (sprites != null) return sprites[0];
        return Minecraft.getInstance()
                .getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS)
                .getSprite(ResourceLocation.withDefaultNamespace("block/white_concrete"));
    }

    static void applyFacing(PoseStack poseStack, Direction facing) {
        poseStack.translate(0.5, 0.0, 0.5);
        poseStack.mulPose(Axis.YP.rotationDegrees(-facing.toYRot() - 180f));
        poseStack.translate(-0.5, 0.0, -0.5);
    }

    static void box(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite,
                    float px1, float py1, float pz1, float px2, float py2, float pz2,
                    float r, float g, float b, float a, int light, int overlay) {
        float x1 = px1 / 16f, y1 = py1 / 16f, z1 = pz1 / 16f;
        float x2 = px2 / 16f, y2 = py2 / 16f, z2 = pz2 / 16f;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();

        quad(vc, pose, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0, 1, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);
        quad(vc, pose, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);
        quad(vc, pose, x2, y2, z1, x1, y2, z1, x1, y1, z1, x2, y1, z1, 0, 0, -1, u0, v0, u1, v1, r, g, b, a, light, overlay);
        quad(vc, pose, x1, y2, z2, x2, y2, z2, x2, y1, z2, x1, y1, z2, 0, 0, 1, u0, v0, u1, v1, r, g, b, a, light, overlay);
        quad(vc, pose, x1, y2, z1, x1, y2, z2, x1, y1, z2, x1, y1, z1, -1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);
        quad(vc, pose, x2, y2, z2, x2, y2, z1, x2, y1, z1, x2, y1, z2, 1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, overlay);
    }

    private static void quad(VertexConsumer vc, PoseStack.Pose pose,
                             float x1, float y1, float z1, float x2, float y2, float z2,
                             float x3, float y3, float z3, float x4, float y4, float z4,
                             float nx, float ny, float nz,
                             float u0, float v0, float u1, float v1,
                             float r, float g, float b, float a, int light, int overlay) {
        vertex(vc, pose, x1, y1, z1, u0, v0, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(vc, pose, x2, y2, z2, u1, v0, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(vc, pose, x3, y3, z3, u1, v1, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(vc, pose, x4, y4, z4, u0, v1, nx, ny, nz, r, g, b, a, light, overlay);
        vertex(vc, pose, x4, y4, z4, u0, v1, -nx, -ny, -nz, r, g, b, a, light, overlay);
        vertex(vc, pose, x3, y3, z3, u1, v1, -nx, -ny, -nz, r, g, b, a, light, overlay);
        vertex(vc, pose, x2, y2, z2, u1, v0, -nx, -ny, -nz, r, g, b, a, light, overlay);
        vertex(vc, pose, x1, y1, z1, u0, v0, -nx, -ny, -nz, r, g, b, a, light, overlay);
    }

    private static void vertex(VertexConsumer vc, PoseStack.Pose pose,
                               float x, float y, float z, float u, float v,
                               float nx, float ny, float nz,
                               float r, float g, float b, float a, int light, int overlay) {
        vc.addVertex(pose.pose(), x, y, z)
                .setColor(r, g, b, a)
                .setUv(u, v)
                .setOverlay(overlay)
                .setLight(light)
                .setNormal(pose, nx, ny, nz);
    }
}
