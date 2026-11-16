package me.flashyreese.mods.sodiumextra.mixin.fog;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.renderer.ShaderInstance$1")
public class MixinFogImport {
    @ModifyReturnValue(method = "applyImport", at = @At("RETURN"))
    private String nocturna$import(String source, boolean inline, String name) {
        return name.endsWith("fog.glsl")
                ? org.embeddedt.embeddium.impl.extra.compat.VanillaFogBridge.transform(source)
                : source;
    }
}
