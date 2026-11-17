package org.embeddedt.embeddium.validation.mixin;

import net.irisshaders.iris.cache.ProgramBinaryCache;
import net.irisshaders.iris.gl.program.RetainedPrograms;
import net.irisshaders.iris.gl.shader.ProgramCreator;
import org.embeddedt.embeddium.validation.RetainedProgramsValidation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ProgramCreator.class)
public abstract class RetainedLinkCounterMixin {
    @Inject(method = "load(Ljava/lang/String;Lnet/irisshaders/iris/cache/ProgramBinaryCache$Key;" +
        "Lnet/irisshaders/iris/gl/program/RetainedPrograms$Ownership;)" +
        "Lnet/irisshaders/iris/gl/shader/ProgramCreator$Submitted;", at = @At("RETURN"), remap = false)
    private static void validation$loaded(String name, ProgramBinaryCache.Key key, RetainedPrograms.Ownership owner,
                                         CallbackInfoReturnable<ProgramCreator.Submitted> cir) {
        if (cir.getReturnValue() != null) System.out.println("VALIDATION binary restored: " + name);
    }
    @Inject(method = "submit(Ljava/lang/String;Ljava/util/List;Ljava/util/Map;" +
        "Lnet/irisshaders/iris/cache/ProgramBinaryCache$Key;" +
        "Lnet/irisshaders/iris/gl/program/RetainedPrograms$Ownership;)" +
        "Lnet/irisshaders/iris/gl/shader/ProgramCreator$Submitted;", at = @At("HEAD"), remap = false)
    private static void validation$link(CallbackInfoReturnable<ProgramCreator.Submitted> cir) {
        RetainedProgramsValidation.link();
    }

    @Inject(method = "reuse", at = @At("RETURN"), remap = false)
    private static void validation$reuse(String name, RetainedPrograms.Ownership owner, ProgramBinaryCache.Key key,
                                        CallbackInfoReturnable<ProgramCreator.Submitted> cir) {
        if (cir.getReturnValue() != null) RetainedProgramsValidation.hit(name, owner);
    }
}
