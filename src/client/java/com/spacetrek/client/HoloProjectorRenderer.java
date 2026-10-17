package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.spacetrek.CoolerStructure;
import com.spacetrek.HoloProjectorBlock;
import com.spacetrek.HoloProjectorBlockEntity;
import com.spacetrek.ModBlocks;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

public class HoloProjectorRenderer implements BlockEntityRenderer<HoloProjectorBlockEntity> {

    private static final int FULL_BRIGHT = 0xF000F0;
    private static final float[] CONTROLLER = {0.3f, 0.9f, 1.0f};
    private static final float[] CASING = {0.45f, 0.75f, 1.0f};
    private static final float[] PORT = {1.0f, 0.65f, 0.2f};
    private static final float[] BLOCKED = {1.0f, 0.2f, 0.2f};
    private static final float EDGE = 0.4f;

    public HoloProjectorRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public boolean shouldRenderOffScreen(HoloProjectorBlockEntity entity) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

    @Override
    public void render(HoloProjectorBlockEntity entity, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        Level level = entity.getLevel();
        if (level == null) return;

        BlockPos origin = entity.getBlockPos();
        Direction facing = entity.getBlockState().getValue(HoloProjectorBlock.FACING);
        BlockPos controllerPos = origin.relative(facing, HoloProjectorBlockEntity.DISTANCE);
        List<CoolerStructure.Cell> cells = CoolerStructure.cells(controllerPos, facing.getOpposite());

        float time = level.getGameTime() + partialTick;
        TextureAtlasSprite sprite = MachineRender.blockSprite("block/cooler_casing");
        VertexConsumer vc = bufferSource.getBuffer(RenderType.translucent());
        PoseStack.Pose pose = poseStack.last();

        int index = 0;
        for (CoolerStructure.Cell cell : cells) {
            index++;
            BlockState existing = level.getBlockState(cell.pos());
            if (existing.is(cell.block())) continue;

            boolean blocked = !existing.isAir() && !existing.canBeReplaced();
            float[] color = blocked ? BLOCKED
                    : (cell.controller() ? CONTROLLER : (cell.block() == ModBlocks.COOLER_PORT_BLOCK ? PORT : CASING));

            float pulse = 0.5f + 0.5f * (float) Math.sin(time * 0.12f + index * 0.7f);
            float fill = blocked ? 0.35f + 0.15f * pulse : 0.18f + 0.14f * pulse;

            float x = (cell.pos().getX() - origin.getX()) * 16f;
            float y = (cell.pos().getY() - origin.getY()) * 16f;
            float z = (cell.pos().getZ() - origin.getZ()) * 16f;
            float inset = blocked ? -0.4f : 0.6f;

            MachineRender.box(vc, pose, sprite, x + inset, y + inset, z + inset, x + 16f - inset, y + 16f - inset, z + 16f - inset,
                    color[0], color[1], color[2], fill, FULL_BRIGHT, packedOverlay);

            edges(vc, pose, sprite, x, y, z, color, 0.55f + 0.35f * pulse, packedOverlay);

            if (cell.controller()) {
                Direction front = facing.getOpposite();
                float fx = front.getStepX();
                float fz = front.getStepZ();
                float cx = x + 8f + fx * 8f;
                float cz = z + 8f + fz * 8f;
                float hx = fx != 0f ? 0.6f : 4f;
                float hz = fz != 0f ? 0.6f : 4f;
                MachineRender.box(vc, pose, sprite, cx - hx, y + 4f, cz - hz, cx + hx, y + 12f, cz + hz,
                        1f, 1f, 1f, 0.55f + 0.3f * pulse, FULL_BRIGHT, packedOverlay);
            }
        }
    }

    private static void edges(VertexConsumer vc, PoseStack.Pose pose, TextureAtlasSprite sprite,
                              float x, float y, float z, float[] c, float a, int overlay) {
        float t = EDGE;
        for (int ix = 0; ix < 2; ix++) {
            for (int iy = 0; iy < 2; iy++) {
                float ex = ix == 0 ? x : x + 16f - t;
                float ey = iy == 0 ? y : y + 16f - t;
                MachineRender.box(vc, pose, sprite, ex, ey, z, ex + t, ey + t, z + 16f, c[0], c[1], c[2], a, FULL_BRIGHT, overlay);
            }
        }
        for (int ix = 0; ix < 2; ix++) {
            for (int iz = 0; iz < 2; iz++) {
                float ex = ix == 0 ? x : x + 16f - t;
                float ez = iz == 0 ? z : z + 16f - t;
                MachineRender.box(vc, pose, sprite, ex, y, ez, ex + t, y + 16f, ez + t, c[0], c[1], c[2], a, FULL_BRIGHT, overlay);
            }
        }
        for (int iy = 0; iy < 2; iy++) {
            for (int iz = 0; iz < 2; iz++) {
                float ey = iy == 0 ? y : y + 16f - t;
                float ez = iz == 0 ? z : z + 16f - t;
                MachineRender.box(vc, pose, sprite, x, ey, ez, x + 16f, ey + t, ez + t, c[0], c[1], c[2], a, FULL_BRIGHT, overlay);
            }
        }
    }
}
