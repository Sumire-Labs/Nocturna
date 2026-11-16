// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.particle;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = net.minecraft.client.renderer.LevelRenderer.class, priority = 1400)
public class MixinLevelRenderer {
    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void sodiumExtra$weather(CallbackInfo ci) {
        if (!SodiumExtraClientMod.options().detailSettings.rainSnow) ci.cancel();
    }

    @com.llamalad7.mixinextras.injector.v2.WrapWithCondition(
            method = "tickRain",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/multiplayer/ClientLevel;addParticle(Lnet/minecraft/core/particles/ParticleOptions;DDDDDD)V"))
    private boolean sodiumExtra$splash(
            net.minecraft.client.multiplayer.ClientLevel level,
            net.minecraft.core.particles.ParticleOptions p,
            double x,
            double y,
            double z,
            double dx,
            double dy,
            double dz) {
        var s = SodiumExtraClientMod.options().particleSettings;
        return s.particles && s.rainSplash;
    }
}
