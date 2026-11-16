package me.flashyreese.mods.sodiumextra.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(net.minecraft.client.gui.Gui.class)
public class MixinDebugCrosshair {
    @ModifyExpressionValue(
            method = "renderCrosshair",
            at = @At(value = "FIELD", target = "Lnet/minecraft/client/Options;renderDebug:Z"))
    private boolean nocturna$crosshair(boolean original) {
        return org.embeddedt.embeddium.impl.extra.compat.DebugOverlayManager.enabled(
                "3d_crosshair");
    }
}
