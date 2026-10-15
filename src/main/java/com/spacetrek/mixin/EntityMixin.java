package com.spacetrek.mixin;

import com.spacetrek.ModDimensions;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Minecraft deletes every non-living entity (dropped items, XP orbs, arrows...) that falls
 * below the bottom of the world. In the space dimension there is no "bottom", so this switches
 * that check off there. Players and mobs are already protected by ModDimensions.
 *
 * require = 0 means that if one of the method names does not exist in this Minecraft version,
 * the game still starts (the item would then just be deleted below the world as before).
 */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @Inject(method = {"checkBelowWorld", "onBelowWorld"}, at = @At("HEAD"), cancellable = true, require = 0)
    private void spacetrek$noVoidInSpace(CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        if (self.level().dimension() == ModDimensions.SPACE_LEVEL) {
            ci.cancel();
        }
    }
}