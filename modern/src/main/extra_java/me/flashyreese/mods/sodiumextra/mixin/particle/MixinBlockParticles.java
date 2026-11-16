// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.particle;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.particle.ParticleEngine.class)
public class MixinBlockParticles {
    @Inject(method = "destroy", at = @At("HEAD"), cancellable = true)
    private void sodiumExtra$destroy(CallbackInfo ci) {
        var p = SodiumExtraClientMod.options().particleSettings;
        if (!p.particles || !p.blockBreak) ci.cancel();
    }

    @Inject(
            method = {"crack", "addBlockHitEffects"},
            at = @At("HEAD"),
            cancellable = true)
    private void sodiumExtra$crack(CallbackInfo ci) {
        var p = SodiumExtraClientMod.options().particleSettings;
        if (!p.particles || !p.blockBreaking) ci.cancel();
    }
}
