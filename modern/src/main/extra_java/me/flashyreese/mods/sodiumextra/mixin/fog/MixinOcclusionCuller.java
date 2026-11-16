package me.flashyreese.mods.sodiumextra.mixin.fog;

import me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper;

import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(
        value = org.embeddedt.embeddium.impl.render.chunk.occlusion.OcclusionCuller.class,
        remap = false)
public class MixinOcclusionCuller {
    @com.llamalad7.mixinextras.injector.ModifyExpressionValue(
            method = "process",
            at =
                    @At(
                            value = "INVOKE",
                            target =
                                    "Lorg/embeddedt/embeddium/impl/render/chunk/occlusion/VisibilityEncoding;getConnections(JI)I"))
    private int sodiumExtra$verticalTraversal(
            int connections,
            @com.llamalad7.mixinextras.sugar.Local(argsOnly = true) float limit,
            @com.llamalad7.mixinextras.sugar.Local(name = "chunkY") int y,
            @com.llamalad7.mixinextras.sugar.Local(name = "camY") int cameraY) {
        if (FogDistanceHelper.isExpandedCylindricalCullDistance(limit)) {
            if (y <= cameraY)
                connections |=
                        1
                                << org.embeddedt.embeddium.impl.render.chunk.occlusion
                                        .GraphDirection.DOWN;
            if (y >= cameraY)
                connections |=
                        1 << org.embeddedt.embeddium.impl.render.chunk.occlusion.GraphDirection.UP;
        }
        return connections;
    }

    @Inject(method = "isWithinRenderDistance", at = @At("HEAD"), cancellable = true)
    private static void sodiumExtra$distance(
            CameraTransform c,
            int x,
            int y,
            int z,
            float limit,
            CallbackInfoReturnable<Boolean> cir) {
        if (FogDistanceHelper.isExpandedCylindricalCullDistance(limit)) {
            int ox = (x << 4) - c.intX, oy = (y << 4) - c.intY, oz = (z << 4) - c.intZ;
            float dx = nearest(ox, ox + 16) - c.fracX,
                    dy = nearest(oy, oy + 16) - c.fracY,
                    dz = nearest(oz, oz + 16) - c.fracZ;
            cir.setReturnValue(
                    FogDistanceHelper.testExpandedCylindricalCullDistance(
                            dx * dx + dz * dz, dy, limit));
        }
    }

    @Unique
    private static int nearest(int min, int max) {
        return min > 0 ? min : Math.min(max, 0);
    }
}
