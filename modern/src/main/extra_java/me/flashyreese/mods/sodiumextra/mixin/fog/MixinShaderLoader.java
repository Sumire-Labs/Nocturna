package me.flashyreese.mods.sodiumextra.mixin.fog;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = org.embeddedt.embeddium.impl.render.shader.ShaderLoader.class, remap = false)
public class MixinShaderLoader {
    @com.llamalad7.mixinextras.injector.ModifyReturnValue(
            method = "getShaderSource",
            at = @At("RETURN"))
    private static String sodiumExtra$shader(String source, String name) {
        if (name.equals("sodium:blocks/block_layer_opaque.vsh")
                || name.equals("sodium:blocks/block_layer_opaque.fsh"))
            return me.flashyreese.mods.sodiumextra.client.fog.FogShaderTransformer
                    .injectRenderDistanceShape(source);
        return source;
    }
}
