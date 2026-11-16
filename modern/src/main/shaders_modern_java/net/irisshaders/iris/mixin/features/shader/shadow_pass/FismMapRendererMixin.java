// Ported from Iris (LGPL-3.0), 82e39b0252a9576190502001ccbadb038c6b5e78.
package net.irisshaders.iris.mixin.features.shader.shadow_pass;

import net.irisshaders.iris.apiimpl.IrisApiV0Impl;
import net.minecraft.client.gui.MapRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MapRenderer.class)
public abstract class FismMapRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fism$cancelMapRendering(CallbackInfo ci) {
        if (IrisApiV0Impl.INSTANCE.isRenderingShadowPass()) {
            ci.cancel();
        }
    }
}
