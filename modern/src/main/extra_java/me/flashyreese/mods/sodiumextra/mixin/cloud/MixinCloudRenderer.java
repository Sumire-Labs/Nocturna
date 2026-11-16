// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.cloud;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = org.embeddedt.embeddium.impl.render.immediate.CloudRenderer.class, remap = false)
public class MixinCloudRenderer {
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
            method = "render",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F",
                            remap = true))
    private float sodiumExtra$height(float original) {
        var e = SodiumExtraClientMod.options().extraSettings;
        return e.cloudHeightOverride ? e.cloudHeight : original;
    }
}
