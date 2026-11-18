package bench.mixin;

import bench.RendererBenchmark;

import com.mojang.blaze3d.platform.Window;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Window.class)
public class FrameMixin {
    @Inject(method = "updateDisplay", at = @At("RETURN"))
    private void frame(CallbackInfo ci) {
        RendererBenchmark.frame();
    }
}
