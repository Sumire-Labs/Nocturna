// Sodium Extra 0.9.6-beta.4 (LGPL-3.0), upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.mixin.instant_sneak;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public class MixinCamera {

    @Shadow private float eyeHeight;

    @Shadow private Entity entity;

    @Inject(at = @At("HEAD"), method = "tick")
    public void noLerp(CallbackInfo ci) {
        if (SodiumExtraClientMod.options().extraSettings.instantSneak && this.entity != null) {
            this.eyeHeight = this.entity.getEyeHeight();
        }
    }
}
