package me.flashyreese.mods.sodiumextra.mixin.fog;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import com.mojang.blaze3d.shaders.Program;

import org.embeddedt.embeddium.impl.extra.compat.VanillaFogBridge;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(Program.class)
public class MixinProgram {
    @WrapMethod(method = "compileShader")
    private static Program nocturna$stage(
            Program.Type type,
            String name,
            java.io.InputStream data,
            String source,
            GlslPreprocessor processor,
            Operation<Program> original) {
        var previous = VanillaFogBridge.STAGE.get();
        VanillaFogBridge.STAGE.set(type);
        try {
            return original.call(type, name, data, source, processor);
        } finally {
            if (previous == null) VanillaFogBridge.STAGE.remove();
            else VanillaFogBridge.STAGE.set(previous);
        }
    }
}
