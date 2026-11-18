package bench.mixin;

import bench.RendererBenchmark;

import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MouseHandler.class)
public class CameraLockMixin {
    @Inject(method = "turnPlayer", at = @At("HEAD"), cancellable = true)
    private void lock(CallbackInfo ci) {
        if (RendererBenchmark.cameraLocked()) ci.cancel();
    }
}
