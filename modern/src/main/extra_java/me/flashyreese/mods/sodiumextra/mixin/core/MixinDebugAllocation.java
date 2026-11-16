package me.flashyreese.mods.sodiumextra.mixin.core;

import org.embeddedt.embeddium.impl.extra.compat.DebugOverlayManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Reuse the host's GC-aware 500 ms calculator, which has the same contract as the reference client.
 */
@Mixin(targets = "net.minecraft.client.gui.components.DebugScreenOverlay$AllocationRateCalculator")
public abstract class MixinDebugAllocation implements DebugOverlayManager.AllocationRate {
    @Shadow
    protected abstract long bytesAllocatedPerSecond(long heapUsage);

    public long nocturna$rate(long heapUsage) {
        return bytesAllocatedPerSecond(heapUsage);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nocturna$register(CallbackInfo ci) {
        DebugOverlayManager.allocationRate = this;
    }
}
