// Sodium Extra 0.9.6-beta.4 (LGPL-3.0), upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.mixin.particle;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.client.particle.FireworkParticles;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(FireworkParticles.Starter.class)
public class MixinFireworkParticle {
    @Unique
    private final ResourceLocation fireworkIdentifier =
            new ResourceLocation("minecraft", "firework");

    @Inject(method = "createParticle", at = @At(value = "HEAD"), cancellable = true)
    public void addExplosionParticle(
            double x,
            double y,
            double z,
            double velocityX,
            double velocityY,
            double velocityZ,
            int[] colors,
            int[] targetColors,
            boolean trail,
            boolean flicker,
            CallbackInfo ci) {
        if (!SodiumExtraClientMod.options()
                .particleSettings
                .isParticleEnabled(this.fireworkIdentifier)) {
            ci.cancel();
        }
    }
}
