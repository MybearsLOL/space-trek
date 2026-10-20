package com.spacetrek.mixin;

import com.spacetrek.RocketEntity;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class MixinCamera {

    @Shadow
    protected abstract void setPosition(double x, double y, double z);

    @Shadow
    public abstract Vec3 getPosition();

    @Shadow
    public abstract Quaternionf rotation();

    @Inject(method = "setup", at = @At("TAIL"), require = 0)
    private void spacetrek$followShip(BlockGetter area, Entity entity, boolean detached, boolean thirdPersonReverse,
                                      float partialTick, CallbackInfo ci) {
        if (detached || !(entity.getVehicle() instanceof RocketEntity rocket)) return;
        Quaternionf ship = rocket.getViewOrientation(partialTick);
        Quaternionf look = rotation();
        look.set(new Quaternionf(ship).mul(look));

        float eye = entity.getEyeHeight();
        Vector3f offset = new Vector3f(0.0f, eye, 0.0f);
        ship.transform(offset);
        Vec3 position = getPosition();
        setPosition(position.x + offset.x, position.y + offset.y - eye, position.z + offset.z);
    }
}
