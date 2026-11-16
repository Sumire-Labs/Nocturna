package org.embeddedt.embeddium.validation.mixin;

import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.validation.ShaderCachePackValidation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = GlShader.class, remap = false)
public class ShaderCompileCounterMixin {
    @Inject(method = "compile", at = @At("HEAD"))
    private static void count(ShaderType type, String name, String source, CallbackInfoReturnable<Integer> ci) {
        ShaderCachePackValidation.recordCompilation(name);
    }
}
