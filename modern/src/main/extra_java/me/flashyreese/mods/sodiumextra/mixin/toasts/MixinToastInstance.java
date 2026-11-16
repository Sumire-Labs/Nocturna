// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.toasts;

import me.flashyreese.mods.sodiumextra.client.util.ToastFilter;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public class MixinToastInstance {
    @Shadow @Final private net.minecraft.client.gui.components.toasts.Toast toast;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void sodiumExtra$skip(CallbackInfoReturnable<Boolean> cir) {
        if (!ToastFilter.isEnabled(toast)) cir.setReturnValue(true);
    }
}
