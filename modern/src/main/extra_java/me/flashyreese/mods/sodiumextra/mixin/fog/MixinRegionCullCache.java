package me.flashyreese.mods.sodiumextra.mixin.fog;

import static org.embeddedt.embeddium.impl.common.util.MathUtil.nearestToZero;
import static org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion.*;

import me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper;

import org.embeddedt.embeddium.impl.render.viewport.CameraTransform;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.embeddedt.embeddium.impl.render.viewport.frustum.Frustum;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Keep Celeritas' region shortcut conservative for Extra's two independent cylinder axes. */
@Mixin(
        targets = "org.embeddedt.embeddium.impl.render.chunk.occlusion.RegionCullCache",
        remap = false)
public class MixinRegionCullCache {
    @Shadow private CameraTransform camera;
    @Shadow private Viewport viewport;
    @Shadow private float maxDistance;

    @Inject(method = "compute", at = @At("HEAD"), cancellable = true)
    private void sodiumExtra$classify(int x, int y, int z, CallbackInfoReturnable<Integer> cir) {
        if (!FogDistanceHelper.isExpandedCylindricalCullDistance(maxDistance)) return;
        CameraTransform c = camera;
        int ax = x - c.intX, ay = y - c.intY, az = z - c.intZ;
        int bx = ax + REGION_BLOCK_WIDTH,
                by = ay + REGION_BLOCK_HEIGHT,
                bz = az + REGION_BLOCK_LENGTH;
        float lx = nearestToZero(ax, ax + 16) - c.fracX, hx = nearestToZero(bx - 16, bx) - c.fracX;
        float ly = nearestToZero(ay, ay + 16) - c.fracY, hy = nearestToZero(by - 16, by) - c.fracY;
        float lz = nearestToZero(az, az + 16) - c.fracZ, hz = nearestToZero(bz - 16, bz) - c.fracZ;
        float nx = Math.max(0, near(lx, hx) - 0.001F),
                ny = Math.max(0, near(ly, hy) - 0.001F),
                nz = Math.max(0, near(lz, hz) - 0.001F);
        if (!FogDistanceHelper.testExpandedCylindricalCullDistance(
                nx * nx + nz * nz, ny, maxDistance)) {
            cir.setReturnValue(4);
            return;
        }
        float fx = Math.max(Math.abs(lx), Math.abs(hx)) + 0.001F;
        float fy = Math.max(Math.abs(ly), Math.abs(hy)) + 0.001F;
        float fz = Math.max(Math.abs(lz), Math.abs(hz)) + 0.001F;
        boolean inside =
                FogDistanceHelper.testExpandedCylindricalCullDistance(
                        fx * fx + fz * fz, fy, maxDistance);
        float pad = 1.125F + 0.03125F;
        int result =
                viewport.intersectCameraRelativeBox(
                        ax - c.fracX - pad,
                        ay - c.fracY - pad,
                        az - c.fracZ - pad,
                        bx - c.fracX + pad,
                        by - c.fracY + pad,
                        bz - c.fracZ + pad);
        cir.setReturnValue(
                result == Frustum.OUTSIDE
                        ? 4
                        : result == Frustum.FULLY_INSIDE
                                ? (inside ? Frustum.FULLY_INSIDE : 3)
                                : (inside ? 2 : Frustum.PARTIALLY_INSIDE));
    }

    @Unique
    private static float near(float lo, float hi) {
        return lo > 0 ? lo : hi < 0 ? -hi : 0;
    }
}
