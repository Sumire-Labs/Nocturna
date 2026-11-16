package org.embeddedt.embeddium.impl.gl.shader.uniform;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

import org.joml.Matrix3f;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Upload buffer reuse from Angelica (LGPL-3.0), d876a203da9b46ee5b5048171b8e5e4deb9bb967. */
public class GlUniformMatrix3f extends GlUniform<Matrix3f> {
    // Equivalent to Angelica's BufferUtils.createFloatBuffer(9), without a backend-specific dependency.
    private final FloatBuffer buffer = ByteBuffer.allocateDirect(9 * Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();

    public GlUniformMatrix3f(int index) {
        super(index);
    }

    @Override
    public void set(Matrix3f value) {
        value.get(this.buffer);
        LWJGL.glUniformMatrix3fv(this.index, false, this.buffer);
    }
}
