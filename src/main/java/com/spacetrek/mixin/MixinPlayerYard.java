package com.spacetrek.mixin;

import com.spacetrek.Shipyard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Player.class)
public abstract class MixinPlayerYard {

    @Inject(method = "canInteractWithBlock", at = @At("HEAD"), cancellable = true, require = 0)
    private void spacetrek$yardReach(BlockPos pos, double extra, CallbackInfoReturnable<Boolean> cir) {
        if (!Shipyard.contains(pos.getX(), pos.getZ())) return;
        Vec3 world = Shipyard.toWorld(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
        if (world == null) return;
        Player self = (Player) (Object) this;
        double range = self.blockInteractionRange() + extra;
        cir.setReturnValue(self.getEyePosition().distanceToSqr(world) < range * range);
    }
}
