package com.spacetrek.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.spacetrek.RocketEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntityRenderer.class)
public abstract class MixinLivingEntityRenderer {

    @Inject(method = "render", at = @At("HEAD"), require = 0)
    private void spacetrek$rotateWithShip(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                          MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (!(entity.getVehicle() instanceof RocketEntity rocket)) return;
        poseStack.pushPose();
        poseStack.mulPose(rocket.getRenderOrientation(partialTicks));
        float bodyRot = Mth.rotLerp(partialTicks, entity.yBodyRotO, entity.yBodyRot);
        poseStack.mulPose(Axis.YP.rotationDegrees(bodyRot - rocket.getSeatYaw(entity)));
    }

    @Inject(method = "render", at = @At("RETURN"), require = 0)
    private void spacetrek$restoreAfterShip(LivingEntity entity, float entityYaw, float partialTicks, PoseStack poseStack,
                                            MultiBufferSource buffer, int packedLight, CallbackInfo ci) {
        if (entity.getVehicle() instanceof RocketEntity) {
            poseStack.popPose();
        }
    }
}
