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
        if (name.equals("sodium:clouds/clouds.fsh"))
            return source.replace(
                    "fragColor = _linearFog(color, vertexDistance, FogColor, FogStart, FogEnd);",
                    "color.a *= vertexDistance <= 0.0 ? 1.0 : (vertexDistance >= FogEnd ? 0.0 : 1.0"
                        + " - vertexDistance / FogEnd);\n"
                        + "    fragColor = color;");
        if (name.equals("sodium:clouds/clouds.vsh"))
            return source.replace("vec4(Position.x, 0.0, Position.z, 1.0)", "vec4(Position, 1.0)");
        if (name.equals("sodium:blocks/block_layer_opaque.vsh")
                || name.equals("sodium:blocks/block_layer_opaque.fsh"))
            return me.flashyreese.mods.sodiumextra.client.fog.FogShaderTransformer
                    .injectRenderDistanceShape(source);
        return source;
    }
}
