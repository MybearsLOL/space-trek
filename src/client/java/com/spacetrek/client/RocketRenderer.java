package com.spacetrek.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.spacetrek.RocketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

public class RocketRenderer extends EntityRenderer<RocketEntity> {

    public RocketRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public boolean shouldRender(RocketEntity entity, Frustum frustum, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public ResourceLocation getTextureLocation(RocketEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public void render(RocketEntity entity, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight) {
        Quaternionf rotation = entity.getRenderOrientation(partialTick);
        float half = entity.getBbHeight() / 2.0f;

        poseStack.pushPose();
        poseStack.translate(0.0, half, 0.0);
        poseStack.mulPose(rotation);
        poseStack.translate(0.0, -half, 0.0);

        double ox = entity.getOriginX();
        double oy = entity.getOriginY();
        double oz = entity.getOriginZ();

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.crosshairPickEntity == entity && minecraft.getCameraEntity() != null) {
            Entity viewer = minecraft.getCameraEntity();
            Vec3 eye = viewer.getEyePosition(partialTick);
            Vec3 end = eye.add(viewer.getViewVector(partialTick).scale(6.0));
            RocketEntity.PartHit hit = entity.raycastParts(eye, end, entity.getPosition(partialTick), rotation);
            if (hit != null) {
                AABB box = new AABB(hit.part().x() - ox, hit.part().y() - oy, hit.part().z() - oz,
                        hit.part().x() - ox + 1.0, hit.part().y() - oy + 1.0, hit.part().z() - oz + 1.0).inflate(0.002);
                LevelRenderer.renderLineBox(poseStack, bufferSource.getBuffer(RenderType.lines()), box, 0.0f, 0.0f, 0.0f, 0.45f);
            }
        }

        for (RocketEntity.Part part : entity.getParts()) {
            poseStack.pushPose();
            poseStack.translate(part.x() - ox, part.y() - oy, part.z() - oz);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(
                    part.state(), poseStack, bufferSource, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }

        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }
}
