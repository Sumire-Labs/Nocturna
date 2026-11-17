package org.embeddedt.embeddium.validation.mixin;

import org.embeddedt.embeddium.impl.gl.shader.GlProgram;
import org.embeddedt.embeddium.validation.RetainedProgramsValidation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GlProgram.Builder.class)
public abstract class RetainedTerrainLinkCounterMixin {
    @Inject(method = "linkDeferred", at = @At("HEAD"), remap = false)
    private void validation$link(CallbackInfoReturnable<GlProgram.Deferred> cir) {
        RetainedProgramsValidation.link();
    }
}
