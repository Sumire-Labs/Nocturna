// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.cloud;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.LevelRenderer.class)
public class MixinLevelRenderer {
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
            method = "renderClouds",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/renderer/DimensionSpecialEffects;getCloudHeight()F"))
    private float sodiumExtra$height(float original) {
        var e = SodiumExtraClientMod.options().extraSettings;
        return e.cloudHeightOverride ? e.cloudHeight : original;
    }
}
