package me.flashyreese.mods.sodiumextra.mixin.fog;

import me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper;

import org.embeddedt.embeddium.impl.extra.compat.ExtraFog;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(value = org.embeddedt.embeddium.impl.render.chunk.RenderSectionManager.class, remap = false)
public abstract class MixinRenderSectionManager {
    @Shadow
    private float getRenderDistance() {
        throw new AssertionError();
    }

    @com.llamalad7.mixinextras.injector.ModifyReturnValue(
            method = {"getEffectiveRenderDistance", "getSearchDistance"},
            at = @At("RETURN"))
    private float sodiumExtra$expand(float original) {
        if (!ExtraFog.hasTerrain) return original;
        float start = ExtraFog.terrain.renderDistanceStart,
                end = ExtraFog.terrain.renderDistanceEnd;
        // Upstream's encoded end lies beyond the terrain view range. Preserve that full-range
        // cull for radial/planar shapes before applying the cylindrical two-axis expansion.
        if (FogDistanceHelper.decodeRenderDistanceEnd(start, end) != end)
            original = getRenderDistance();
        return FogDistanceHelper.expandCylindricalCullDistance(
                original, start, end, getRenderDistance());
    }
}
