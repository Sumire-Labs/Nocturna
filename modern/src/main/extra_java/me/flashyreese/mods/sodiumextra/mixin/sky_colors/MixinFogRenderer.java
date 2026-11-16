// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.sky_colors;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.FogRenderer.class)
public class MixinFogRenderer {
    @Shadow public static float fogRed, fogGreen, fogBlue;

    @Inject(
            method = "setupColor",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraft/client/multiplayer/ClientLevel;getRainLevel(F)F"))
    private static void sodiumExtra$color(CallbackInfo ci) {
        if (!SodiumExtraClientMod.options().detailSettings.skyColors) {
            fogRed = ((7907327 >> 16) & 255) / 255.0F;
            fogGreen = ((7907327 >> 8) & 255) / 255.0F;
            fogBlue = (7907327 & 255) / 255.0F;
        }
    }
}
