package com.spacetrek.mixin;

import com.spacetrek.ModFluids;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.FluidState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FogRenderer.class)
public class MixinFogRenderer {

    @Inject(method = "setupFog", at = @At("HEAD"), cancellable = true)
    private static void onSetupFog(Camera camera, FogRenderer.FogMode fogMode,
                                   float viewDistance, boolean thickFog,
                                   float partialTick, CallbackInfo ci) {
        BlockPos pos = BlockPos.containing(camera.getPosition());
        FluidState fluidState = camera.getEntity().level().getFluidState(pos);

        if (fluidState.is(ModFluids.ROCKET_FUEL_SOURCE)
                || fluidState.is(ModFluids.ROCKET_FUEL_FLOWING)) {
            RenderSystem.setShaderFogStart(0.0f);
            RenderSystem.setShaderFogEnd(3.0f);
            RenderSystem.setShaderFogColor(1.0f, 0.85f, 0.0f, 1.0f);
            ci.cancel();
        }
    }
}