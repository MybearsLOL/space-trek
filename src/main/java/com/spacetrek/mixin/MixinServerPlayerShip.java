package com.spacetrek.mixin;

import com.spacetrek.ShipCollision;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MixinServerPlayerShip {

    @Shadow
    public ServerPlayer player;

    @Shadow
    private boolean clientIsFloating;

    @ModifyConstant(method = "handleMovePlayer", constant = @Constant(doubleValue = 0.0625), require = 0)
    private double spacetrek$lenientMoveCheck(double original) {
        return ShipCollision.isNearShip(player) ? 4096.0 : original;
    }

    @Inject(method = "tick", at = @At("HEAD"), require = 0)
    private void spacetrek$noFloatKick(CallbackInfo ci) {
        if (clientIsFloating && ShipCollision.isNearShip(player)) {
            clientIsFloating = false;
        }
    }
}
