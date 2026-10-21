package com.spacetrek.mixin;

import com.spacetrek.ShipCollision;
import com.spacetrek.RocketEntity;
import com.spacetrek.Shipyard;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
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

    @Inject(method = "getViewVector", at = @At("RETURN"), cancellable = true, require = 0)
    private void spacetrek$shipView(float partialTick, CallbackInfoReturnable<Vec3> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player) || !(self.getVehicle() instanceof RocketEntity rocket)) return;
        Quaternionf q = rocket.getViewOrientation(partialTick);
        Vec3 v = cir.getReturnValue();
        Vector3f t = new Vector3f((float) v.x, (float) v.y, (float) v.z);
        q.transform(t);
        cir.setReturnValue(new Vec3(t.x, t.y, t.z));
    }

    @Inject(method = "distanceToSqr(DDD)D", at = @At("HEAD"), cancellable = true, require = 0)
    private void spacetrek$yardDistance(double x, double y, double z, CallbackInfoReturnable<Double> cir) {
        Entity self = (Entity) (Object) this;
        if (!(self instanceof Player) || !Shipyard.contains(x, z)) return;
        Vec3 world = Shipyard.toWorld(x, y, z);
        if (world == null) return;
        double dx = self.getX() - world.x;
        double dy = self.getY() - world.y;
        double dz = self.getZ() - world.z;
        cir.setReturnValue(dx * dx + dy * dy + dz * dz);
    }
}
