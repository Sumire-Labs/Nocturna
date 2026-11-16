package org.embeddedt.embeddium.validation.mixin;

import net.irisshaders.iris.shadows.ShadowRenderer;
import org.embeddedt.embeddium.validation.FismValidation;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ShadowRenderer.class)
public abstract class FismShadowPassMixin {
    @Inject(method = "renderShadows", remap = false, at = @At(value = "FIELD",
        target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;ACTIVE:Z", opcode = Opcodes.PUTSTATIC,
        ordinal = 0, shift = At.Shift.AFTER))
    private void validation$fismInShadowPass(CallbackInfo ci) {
        FismValidation.realShadowPass();
    }
}
