package org.embeddedt.embeddium.impl.mixin.core.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

import org.embeddedt.embeddium.impl.gui.rso.compat.TextInputBridge;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class RsoModifierMixin {
    @Inject(method = "hasShiftDown", at = @At("HEAD"), cancellable = true)
    private static void nocturna$shift(CallbackInfoReturnable<Boolean> ci) {
        if (TextInputBridge.ownsKeyboard())
            ci.setReturnValue((TextInputBridge.modifiers() & 1) != 0);
    }

    @Inject(method = "hasControlDown", at = @At("HEAD"), cancellable = true)
    private static void nocturna$control(CallbackInfoReturnable<Boolean> ci) {
        if (TextInputBridge.ownsKeyboard())
            ci.setReturnValue((TextInputBridge.modifiers() & (Minecraft.ON_OSX ? 8 : 2)) != 0);
    }

    @Inject(method = "hasAltDown", at = @At("HEAD"), cancellable = true)
    private static void nocturna$alt(CallbackInfoReturnable<Boolean> ci) {
        if (TextInputBridge.ownsKeyboard())
            ci.setReturnValue((TextInputBridge.modifiers() & 4) != 0);
    }
}
