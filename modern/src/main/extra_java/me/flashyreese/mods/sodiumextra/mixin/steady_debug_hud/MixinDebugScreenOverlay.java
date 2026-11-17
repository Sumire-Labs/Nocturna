package me.flashyreese.mods.sodiumextra.mixin.steady_debug_hud;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;

/**
 * Forge draws HUD lists directly, without calling DebugScreenOverlay.render. Cache after other mods
 * append their lines.
 */
@Mixin(value = net.minecraftforge.client.gui.overlay.ForgeGui.class, remap = false)
public class MixinDebugScreenOverlay {
    @Unique private long sodiumExtra$nextTime;
    @Unique private boolean sodiumExtra$rebuild;
    @Unique private long nocturna$revision = -1;
    @Unique private List<String> sodiumExtra$left = List.of(), sodiumExtra$right = List.of();

    @Inject(method = "renderHUDText", at = @At("HEAD"))
    private void sodiumExtra$refresh(CallbackInfo ci) {
        var settings = SodiumExtraClientMod.options().extraSettings;
        long now = net.minecraft.Util.getMillis();
        long revision =
                org.embeddedt.embeddium.impl.extra.compat.DebugOverlayManager.cacheRevision();
        sodiumExtra$rebuild =
                !settings.steadyDebugHud
                        || now > sodiumExtra$nextTime
                        || revision != nocturna$revision;
        nocturna$revision = revision;
        if (sodiumExtra$rebuild)
            sodiumExtra$nextTime = now + settings.steadyDebugHudRefreshInterval * 50L;
    }

    @WrapOperation(
            method = "renderHUDText",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraftforge/client/gui/overlay/ForgeGui$ForgeDebugScreenOverlay;getLeft()Ljava/util/List;"))
    private List<String> sodiumExtra$left(
            @Coerce Object overlay, Operation<List<String>> original) {
        var lines = original.call(overlay);
        if (sodiumExtra$rebuild || sodiumExtra$left.isEmpty())
            sodiumExtra$left = new ArrayList<>(lines);
        return new ArrayList<>(sodiumExtra$left);
    }

    @WrapOperation(
            method = "renderHUDText",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lnet/minecraftforge/client/gui/overlay/ForgeGui$ForgeDebugScreenOverlay;getRight()Ljava/util/List;"))
    private List<String> sodiumExtra$right(
            @Coerce Object overlay, Operation<List<String>> original) {
        var lines = original.call(overlay);
        if (sodiumExtra$rebuild || sodiumExtra$right.isEmpty())
            sodiumExtra$right = new ArrayList<>(lines);
        return new ArrayList<>(sodiumExtra$right);
    }
}
