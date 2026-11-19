// SPDX-License-Identifier: LGPL-3.0-or-later
package net.irisshaders.iris.pathways.scaling.temporal;

import org.joml.Matrix4fc;

/** Camera parameters for the actual finite OpenGL perspective depth buffer, not the pack's nominal far uniform. */
public record TemporalCamera(float near, float far, float verticalFov) {
    public static TemporalCamera fromProjection(Matrix4fc projection) {
        if (projection == null || Math.abs(projection.m23() + 1) > 0.00001f || Math.abs(projection.m33()) > 0.00001f)
            throw new IllegalArgumentException("Temporal FSR requires a finite perspective projection");
        double near = projection.m32() / ((double) projection.m22() - 1);
        double far = projection.m32() / ((double) projection.m22() + 1);
        double fov = 2 * Math.atan(1.0 / Math.abs(projection.m11()));
        if (!Double.isFinite(near) || !Double.isFinite(far) || !Double.isFinite(fov)
                || !(near > 0 && far > near && fov > 0 && fov < Math.PI))
            throw new IllegalArgumentException("Temporal FSR requires ordinary finite OpenGL depth");
        return new TemporalCamera((float) near, (float) far, (float) fov);
    }
}
