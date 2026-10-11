package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.spacetrek.FuelTankBlockEntity;
import com.spacetrek.ModFluids;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.material.Fluids;

public class FuelTankBlockEntityRenderer implements BlockEntityRenderer<FuelTankBlockEntity> {

    public FuelTankBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(FuelTankBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        int buckets = entity.getBuckets();
        int maxBuckets = entity.getMaxBuckets();
        FuelTankBlockEntity.FluidType fluidType = entity.getFluidType();

        if (buckets <= 0 || fluidType == FuelTankBlockEntity.FluidType.EMPTY) return;

        TextureAtlasSprite sprite;
        float r, g, b;

        switch (fluidType) {
            case WATER -> {
                FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(Fluids.WATER);
                TextureAtlasSprite[] sprites = handler != null
                        ? handler.getFluidSprites(null, null, Fluids.WATER.defaultFluidState())
                        : null;
                sprite = sprites != null ? sprites[0] : getWhiteSprite();
                r = 0.24f; g = 0.46f; b = 1.0f;
            }
            case ROCKET_FUEL -> {
                FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(ModFluids.ROCKET_FUEL_SOURCE);
                TextureAtlasSprite[] sprites = handler != null
                        ? handler.getFluidSprites(null, null, ModFluids.ROCKET_FUEL_SOURCE.defaultFluidState())
                        : null;
                sprite = sprites != null ? sprites[0] : getWhiteSprite();
                r = 1.0f; g = 0.85f; b = 0.0f;
            }
            case OXYGEN -> {
                FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(ModFluids.OXYGEN_SOURCE);
                TextureAtlasSprite[] sprites = handler != null
                        ? handler.getFluidSprites(null, null, ModFluids.OXYGEN_SOURCE.defaultFluidState())
                        : null;
                sprite = sprites != null ? sprites[0] : getWhiteSprite();
                r = 0.67f; g = 0.87f; b = 1.0f;
            }
            default -> {
                sprite = getWhiteSprite();
                r = 1.0f; g = 1.0f; b = 1.0f;
            }
        }

        float a = 0.9f;
        float margin = 0.02f;
        float fillHeight = (float) buckets / maxBuckets;

        float x1 = margin, x2 = 1f - margin;
        float y1 = margin, y2 = y1 + (1f - margin * 2f) * fillHeight;
        float z1 = margin, z2 = 1f - margin;

        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        VertexConsumer vc = bufferSource.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();

        // Bottom
        face(vc, pose, x1,y1,z1, x2,y1,z1, x2,y1,z2, x1,y1,z2, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        face(vc, pose, x1,y1,z2, x2,y1,z2, x2,y1,z1, x1,y1,z1, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        // Top
        face(vc, pose, x1,y2,z2, x2,y2,z2, x2,y2,z1, x1,y2,z1, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        face(vc, pose, x1,y2,z1, x2,y2,z1, x2,y2,z2, x1,y2,z2, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        // North (z1)
        face(vc, pose, x2,y2,z1, x1,y2,z1, x1,y1,z1, x2,y1,z1, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        face(vc, pose, x2,y1,z1, x1,y1,z1, x1,y2,z1, x2,y2,z1, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        // South (z2)
        face(vc, pose, x1,y2,z2, x2,y2,z2, x2,y1,z2, x1,y1,z2, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        face(vc, pose, x1,y1,z2, x2,y1,z2, x2,y2,z2, x1,y2,z2, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        // West (x1)
        face(vc, pose, x1,y2,z1, x1,y2,z2, x1,y1,z2, x1,y1,z1, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        face(vc, pose, x1,y1,z1, x1,y1,z2, x1,y2,z2, x1,y2,z1, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        // East (x2)
        face(vc, pose, x2,y2,z2, x2,y2,z1, x2,y1,z1, x2,y1,z2, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
        face(vc, pose, x2,y1,z2, x2,y1,z1, x2,y2,z1, x2,y2,z2, u0,v0,u1,v1, r,g,b,a, packedLight, packedOverlay);
    }

    private TextureAtlasSprite getWhiteSprite() {
        return Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
    }

    private void face(VertexConsumer vc, PoseStack.Pose pose,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a,
                      int light, int overlay) {
        vc.addVertex(pose.pose(), x1, y1, z1).setColor(r,g,b,a).setUv(u0,v0).setOverlay(overlay).setLight(light).setNormal(pose, 0, 1, 0);
        vc.addVertex(pose.pose(), x2, y2, z2).setColor(r,g,b,a).setUv(u1,v0).setOverlay(overlay).setLight(light).setNormal(pose, 0, 1, 0);
        vc.addVertex(pose.pose(), x3, y3, z3).setColor(r,g,b,a).setUv(u1,v1).setOverlay(overlay).setLight(light).setNormal(pose, 0, 1, 0);
        vc.addVertex(pose.pose(), x4, y4, z4).setColor(r,g,b,a).setUv(u0,v1).setOverlay(overlay).setLight(light).setNormal(pose, 0, 1, 0);
    }
}