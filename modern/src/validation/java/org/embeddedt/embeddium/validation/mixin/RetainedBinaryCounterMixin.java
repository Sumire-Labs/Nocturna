package org.embeddedt.embeddium.validation.mixin;

import org.embeddedt.embeddium.validation.RetainedProgramsValidation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.irisshaders.iris.cache.ProgramBinaryAccess")
public abstract class RetainedBinaryCounterMixin {
    @Inject(method = "load", at = @At("HEAD"), remap = false)
    private static void validation$binary(CallbackInfo ci) {
        RetainedProgramsValidation.binary();
    }
}
