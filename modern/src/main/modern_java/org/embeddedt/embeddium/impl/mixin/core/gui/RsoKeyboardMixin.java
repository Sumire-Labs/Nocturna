package org.embeddedt.embeddium.impl.mixin.core.gui;

import net.minecraft.client.KeyboardHandler;

import org.embeddedt.embeddium.impl.gui.rso.compat.TextInputBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardHandler.class)
public class RsoKeyboardMixin {
    @Inject(
            method = {"keyPress", "charTyped"},
            at = @At("HEAD"),
            cancellable = true)
    private void nocturna$ignoreDuplicateGlfwInput(CallbackInfo ci) {
        if (TextInputBridge.ownsKeyboard() && !TextInputBridge.isDispatching()) ci.cancel();
    }
}
