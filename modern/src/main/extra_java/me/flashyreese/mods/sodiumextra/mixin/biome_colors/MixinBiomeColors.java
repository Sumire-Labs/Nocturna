// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.biome_colors;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.renderer.BiomeColors.class)
public class MixinBiomeColors {
    @Inject(method = "getAverageGrassColor", at = @At("RETURN"), cancellable = true)
    private static void sodiumExtra$Grass(CallbackInfoReturnable<Integer> cir) {
        if (!SodiumExtraClientMod.options().detailSettings.biomeColors) cir.setReturnValue(9551193);
    }

    @Inject(method = "getAverageWaterColor", at = @At("RETURN"), cancellable = true)
    private static void sodiumExtra$Water(CallbackInfoReturnable<Integer> cir) {
        if (!SodiumExtraClientMod.options().detailSettings.biomeColors) cir.setReturnValue(4159204);
    }

    @Inject(method = "getAverageFoliageColor", at = @At("RETURN"), cancellable = true)
    private static void sodiumExtra$Foliage(CallbackInfoReturnable<Integer> cir) {
        if (!SodiumExtraClientMod.options().detailSettings.biomeColors) cir.setReturnValue(5877296);
    }
}
