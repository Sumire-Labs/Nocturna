package me.flashyreese.mods.sodiumextra.mixin.core;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.embeddedt.embeddium.impl.extra.compat.ExtraDebugOptionsScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/** Debug entries stay available when the optional steady-HUD mixin is disabled. */
@Mixin(value = net.minecraft.client.gui.components.DebugScreenOverlay.class, priority = 1100)
public class MixinDebugEntries {
    @ModifyReturnValue(method = "getGameInformation", at = @At("RETURN"))
    private List<String> sodiumExtra$entries(List<String> original) {
        return ExtraDebugOptionsScreen.appendDebugLines(original);
    }
}
