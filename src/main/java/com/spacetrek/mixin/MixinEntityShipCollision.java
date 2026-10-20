package com.spacetrek.mixin;

import com.spacetrek.ShipCollision;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Entity.class)
public abstract class MixinEntityShipCollision {

    @Inject(method = "collide", at = @At("RETURN"), cancellable = true, require = 0)
    private void spacetrek$shipCollide(Vec3 movement, CallbackInfoReturnable<Vec3> cir) {
        Vec3 adjusted = ShipCollision.collide((Entity) (Object) this, movement, cir.getReturnValue());
        if (adjusted != null) {
            cir.setReturnValue(adjusted);
        }
    }
}
