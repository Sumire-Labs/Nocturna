package org.embeddedt.embeddium.impl.mixin.core.gui;

import com.mojang.blaze3d.systems.RenderSystem;

import org.embeddedt.embeddium.impl.gui.rso.compat.TextInputBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(RenderSystem.class)
public class RsoEventPumpMixin {
    @Inject(method = "pollEvents", at = @At("HEAD"))
    private static void nocturna$pumpSdlFirst(CallbackInfo ci) {
        TextInputBridge.prepareEvents();
    }

    @Inject(method = "pollEvents", at = @At("RETURN"))
    private static void nocturna$dispatchAfterGlfwStateUpdate(CallbackInfo ci) {
        TextInputBridge.pumpEvents();
    }
}
