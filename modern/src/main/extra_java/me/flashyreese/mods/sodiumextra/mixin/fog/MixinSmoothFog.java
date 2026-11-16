package me.flashyreese.mods.sodiumextra.mixin.fog;

import org.embeddedt.embeddium.impl.extra.compat.ExtraFog;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformFloat;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(
        value =
                org.embeddedt.embeddium.impl.render.chunk.shader.ChunkShaderFogComponent.Smooth
                        .class,
        remap = false)
public class MixinSmoothFog {
    @Shadow @Final private GlUniformFloat uFogStart, uFogEnd;

    @Inject(method = "setup", at = @At("TAIL"))
    private void sodiumExtra$shape(CallbackInfo ci) {
        if (ExtraFog.hasTerrain) {
            uFogStart.set(ExtraFog.terrain.renderDistanceStart);
            uFogEnd.set(ExtraFog.terrain.renderDistanceEnd);
        }
    }
}
