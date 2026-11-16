package me.flashyreese.mods.sodiumextra.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.renderer.entity.EntityRenderDispatcher.class)
public class MixinDebugHitboxes {
    @ModifyExpressionValue(
            method = "render",
            at =
                    @At(
                            value = "FIELD",
                            target =
                                    "Lnet/minecraft/client/renderer/entity/EntityRenderDispatcher;renderHitBoxes:Z"))
    private boolean nocturna$render(boolean original) {
        return org.embeddedt.embeddium.impl.extra.compat.DebugOverlayManager.enabled(
                "entity_hitboxes");
    }

    @ModifyReturnValue(method = "shouldRenderHitBoxes", at = @At("RETURN"))
    private boolean nocturna$hitboxes(boolean original) {
        return org.embeddedt.embeddium.impl.extra.compat.DebugOverlayManager.enabled(
                "entity_hitboxes");
    }
}
