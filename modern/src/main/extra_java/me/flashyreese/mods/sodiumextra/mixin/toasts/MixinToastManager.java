// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.toasts;

import me.flashyreese.mods.sodiumextra.client.util.ToastFilter;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.gui.components.toasts.ToastComponent.class)
public class MixinToastManager {
    @Shadow @Final private java.util.Deque<net.minecraft.client.gui.components.toasts.Toast> queued;

    @Inject(method = "addToast", at = @At("HEAD"), cancellable = true)
    private void sodiumExtra$add(
            net.minecraft.client.gui.components.toasts.Toast toast, CallbackInfo ci) {
        if (!ToastFilter.isEnabled(toast)) ci.cancel();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void sodiumExtra$queue(CallbackInfo ci) {
        queued.removeIf(t -> !ToastFilter.isEnabled(t));
    }
}
