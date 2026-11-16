package me.flashyreese.mods.sodiumextra.mixin.fog;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.ShaderInstance.class)
public class MixinShaderInstance {
    @Shadow @Final private int programId;
    @Unique private int[] nocturna$fog;

    @Inject(method = "apply", at = @At("TAIL"))
    private void nocturna$uniforms(CallbackInfo ci) {
        if (nocturna$fog == null)
            nocturna$fog =
                    new int[] {
                        org.lwjgl.opengl.GL20C.glGetUniformLocation(
                                programId, "nocturna_FogEnabled"),
                        org.lwjgl.opengl.GL20C.glGetUniformLocation(
                                programId, "nocturna_FogRanges"),
                        org.lwjgl.opengl.GL20C.glGetUniformLocation(
                                programId, "nocturna_InverseView")
                    };
        org.embeddedt.embeddium.impl.extra.compat.VanillaFogBridge.upload(
                nocturna$fog[0], nocturna$fog[1], nocturna$fog[2]);
    }
}
