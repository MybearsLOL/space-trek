package com.spacetrek.mixin;

import com.spacetrek.ModFluids;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public class MixinLivingEntity {

    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self.isEyeInFluid(net.minecraft.tags.FluidTags.WATER)
                && self.level().getFluidState(self.blockPosition()).is(ModFluids.ROCKET_FUEL_SOURCE)) {
            self.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, 20, 2, true, false));
        }
    }
}