package bench.mixin;

import bench.ShaderAnimationClock;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.irisshaders.iris.uniforms.SystemTimeUniforms$Timer", remap = false)
public class ShaderAnimationClockMixin {
    @Shadow private float frameTimeCounter;

    @Inject(method = "beginFrame", at = @At("TAIL"))
    private void controlledTime(long frameStartTime, CallbackInfo callback) {
        if (ShaderAnimationClock.active()) frameTimeCounter = ShaderAnimationClock.seconds();
    }
}
