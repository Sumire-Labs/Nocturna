package net.irisshaders.iris.mixin.texture.pbr;

import net.irisshaders.iris.texture.pbr.PBRResourceIndex;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MultiPackResourceManager.class)
public abstract class MixinMultiPackResourceManager {
    @Inject(method = "close", at = @At("HEAD"))
    private void iris$releasePbrIndex(CallbackInfo ci) {
        PBRResourceIndex.release((MultiPackResourceManager) (Object) this);
    }
}
