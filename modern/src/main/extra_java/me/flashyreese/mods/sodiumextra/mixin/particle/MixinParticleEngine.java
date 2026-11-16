// Sodium Extra 0.9.6-beta.4 (LGPL-3.0), upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.mixin.particle;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ParticleEngine.class)
public class MixinParticleEngine {
    @Inject(method = "createParticle", at = @At(value = "HEAD"), cancellable = true)
    public void addParticle(
            ParticleOptions particleOptions,
            double d,
            double e,
            double f,
            double g,
            double h,
            double i,
            CallbackInfoReturnable<Particle> cir) {
        ResourceLocation particleTypeId =
                BuiltInRegistries.PARTICLE_TYPE.getKey(particleOptions.getType());
        if (!SodiumExtraClientMod.options().particleSettings.isParticleEnabled(particleTypeId)) {
            cir.setReturnValue(null);
        }
    }
}
