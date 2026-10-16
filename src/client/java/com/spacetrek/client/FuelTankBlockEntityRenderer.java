package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.spacetrek.FuelTankBlock;
import com.spacetrek.FuelTankBlockEntity;
import com.spacetrek.ModFluids;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandler;
import net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderHandlerRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;

public class FuelTankBlockEntityRenderer implements BlockEntityRenderer<FuelTankBlockEntity> {

    private static final float MARGIN = 0.1f;
    private static final float FULL = 0.999f;

    public FuelTankBlockEntityRenderer(BlockEntityRendererProvider.Context ctx) {}

    @Override
    public void render(FuelTankBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

        int amount = entity.getAmountMb();
        FuelTankBlockEntity.FluidType fluidType = entity.getFluidType();

        if (amount <= 0 || fluidType == FuelTankBlockEntity.FluidType.EMPTY) return;

        TextureAtlasSprite sprite;
        float r, g, b;

        switch (fluidType) {
            case WATER -> {
                sprite = fluidSprite(Fluids.WATER.defaultFluidState().getType());
                r = 0.24f; g = 0.46f; b = 1.0f;
            }
            case ROCKET_FUEL -> {
                sprite = fluidSprite(ModFluids.ROCKET_FUEL_SOURCE);
                r = 1.0f; g = 0.85f; b = 0.0f;
            }
            case OXYGEN -> {
                sprite = fluidSprite(ModFluids.OXYGEN_SOURCE);
                r = 0.67f; g = 0.87f; b = 1.0f;
            }
            default -> {
                sprite = getWhiteSprite();
                r = 1.0f; g = 1.0f; b = 1.0f;
            }
        }

        BlockState state = entity.getBlockState();
        boolean north = state.getValue(FuelTankBlock.NORTH);
        boolean east = state.getValue(FuelTankBlock.EAST);
        boolean south = state.getValue(FuelTankBlock.SOUTH);
        boolean west = state.getValue(FuelTankBlock.WEST);
        boolean up = state.getValue(FuelTankBlock.UP);
        boolean down = state.getValue(FuelTankBlock.DOWN);

        float fill = amount / (float) FuelTankBlockEntity.BLOCK_CAPACITY_MB;
        boolean full = fill >= FULL;

        boolean aboveHasFluid = false;
        Level level = entity.getLevel();
        if (up && level != null && level.getBlockEntity(entity.getBlockPos().above()) instanceof FuelTankBlockEntity above) {
            aboveHasFluid = above.getAmountMb() > 0;
        }

        float x1 = west ? 0f : MARGIN;
        float x2 = east ? 1f : 1f - MARGIN;
        float z1 = north ? 0f : MARGIN;
        float z2 = south ? 1f : 1f - MARGIN;
        float y1 = down ? 0f : MARGIN;
        float yMax = up ? 1f : 1f - MARGIN;
        float y2 = full && up ? 1f : y1 + (yMax - y1) * Math.min(1f, fill);

        float a = 0.9f;
        float u0 = sprite.getU0(), u1 = sprite.getU1();
        float v0 = sprite.getV0(), v1 = sprite.getV1();

        VertexConsumer vc = bufferSource.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();

        if (!down) {
            faceBoth(vc, pose, x1, y1, z1, x2, y1, z1, x2, y1, z2, x1, y1, z2, 0, -1, 0,
                    u0, v0, u1, v1, r, g, b, a, packedLight, packedOverlay);
        }
        if (!(full && up && aboveHasFluid)) {
            faceBoth(vc, pose, x1, y2, z2, x2, y2, z2, x2, y2, z1, x1, y2, z1, 0, 1, 0,
                    u0, v0, u1, v1, r, g, b, a, packedLight, packedOverlay);
        }
        if (!north) {
            faceBoth(vc, pose, x2, y2, z1, x1, y2, z1, x1, y1, z1, x2, y1, z1, 0, 0, -1,
                    u0, v0, u1, v1, r, g, b, a, packedLight, packedOverlay);
        }
        if (!south) {
            faceBoth(vc, pose, x1, y2, z2, x2, y2, z2, x2, y1, z2, x1, y1, z2, 0, 0, 1,
                    u0, v0, u1, v1, r, g, b, a, packedLight, packedOverlay);
        }
        if (!west) {
            faceBoth(vc, pose, x1, y2, z1, x1, y2, z2, x1, y1, z2, x1, y1, z1, -1, 0, 0,
                    u0, v0, u1, v1, r, g, b, a, packedLight, packedOverlay);
        }
        if (!east) {
            faceBoth(vc, pose, x2, y2, z2, x2, y2, z1, x2, y1, z1, x2, y1, z2, 1, 0, 0,
                    u0, v0, u1, v1, r, g, b, a, packedLight, packedOverlay);
        }
    }

    private TextureAtlasSprite fluidSprite(net.minecraft.world.level.material.Fluid fluid) {
        FluidRenderHandler handler = FluidRenderHandlerRegistry.INSTANCE.get(fluid);
        TextureAtlasSprite[] sprites = handler != null
                ? handler.getFluidSprites(null, null, fluid.defaultFluidState())
                : null;
        return sprites != null ? sprites[0] : getWhiteSprite();
    }

    private TextureAtlasSprite getWhiteSprite() {
        return Minecraft.getInstance()
                .getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
                .apply(ResourceLocation.withDefaultNamespace("block/white_concrete"));
    }

    private void faceBoth(VertexConsumer vc, PoseStack.Pose pose,
                          float x1, float y1, float z1,
                          float x2, float y2, float z2,
                          float x3, float y3, float z3,
                          float x4, float y4, float z4,
                          float nx, float ny, float nz,
                          float u0, float v0, float u1, float v1,
                          float r, float g, float b, float a,
                          int light, int overlay) {
        face(vc, pose, x1, y1, z1, x2, y2, z2, x3, y3, z3, x4, y4, z4, nx, ny, nz,
                u0, v0, u1, v1, r, g, b, a, light, overlay);
        face(vc, pose, x4, y4, z4, x3, y3, z3, x2, y2, z2, x1, y1, z1, -nx, -ny, -nz,
                u0, v0, u1, v1, r, g, b, a, light, overlay);
    }

    private void face(VertexConsumer vc, PoseStack.Pose pose,
                      float x1, float y1, float z1,
                      float x2, float y2, float z2,
                      float x3, float y3, float z3,
                      float x4, float y4, float z4,
                      float nx, float ny, float nz,
                      float u0, float v0, float u1, float v1,
                      float r, float g, float b, float a,
                      int light, int overlay) {
        vc.addVertex(pose.pose(), x1, y1, z1).setColor(r, g, b, a).setUv(u0, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose.pose(), x2, y2, z2).setColor(r, g, b, a).setUv(u1, v0).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose.pose(), x3, y3, z3).setColor(r, g, b, a).setUv(u1, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
        vc.addVertex(pose.pose(), x4, y4, z4).setColor(r, g, b, a).setUv(u0, v1).setOverlay(overlay).setLight(light).setNormal(pose, nx, ny, nz);
    }
}
