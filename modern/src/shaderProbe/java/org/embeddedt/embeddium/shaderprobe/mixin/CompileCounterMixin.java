package org.embeddedt.embeddium.shaderprobe.mixin;
import org.embeddedt.embeddium.impl.gl.shader.*;
import org.embeddedt.embeddium.shaderprobe.ShaderPackSmoke;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(value=GlShader.class,remap=false)
public class CompileCounterMixin {
    @Inject(method="compile",at=@At("HEAD"))
    private static void count(ShaderType type,String name,String source,CallbackInfoReturnable<Integer> ci){ShaderPackSmoke.compiles.incrementAndGet();}
}
