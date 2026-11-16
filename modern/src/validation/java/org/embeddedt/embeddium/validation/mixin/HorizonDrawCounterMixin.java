package org.embeddedt.embeddium.validation.mixin;

import net.irisshaders.iris.pathways.HorizonRenderer;
import org.embeddedt.embeddium.validation.HorizonValidation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HorizonRenderer.class)
public abstract class HorizonDrawCounterMixin {
    @Inject(method = "renderHorizon", at = @At("TAIL"), remap = false)
    private void validation$recordHorizonDraw(CallbackInfo ci) {
        HorizonValidation.recordWorldDraw();
    }
}
