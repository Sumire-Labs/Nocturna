package me.flashyreese.mods.sodiumextra.mixin.fog;

import org.embeddedt.embeddium.impl.extra.compat.ExtraFog;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(
        targets = "org.embeddedt.embeddium.impl.render.immediate.CloudShader$CloudShaderInterface",
        remap = false)
public class MixinCloudFog {
    @Shadow @Final private GlUniformFloat uniformFogStart, uniformFogEnd;

    @Inject(method = "setupState", at = @At("TAIL"))
    private void sodiumExtra$cloud(CallbackInfo ci) {
        if (ExtraFog.hasTerrain) {
            if (uniformFogStart != null) uniformFogStart.set(0.0F);
            uniformFogEnd.set(ExtraFog.terrain.cloudEnd);
        }
    }
}
