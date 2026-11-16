package org.embeddedt.embeddium.impl.gl.shader.uniform;

import static org.taumc.celeritas.lwjgl.LWJGLServiceProvider.LWJGL;

import org.joml.Matrix4fc;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Upload buffer reuse from Angelica (LGPL-3.0), d876a203da9b46ee5b5048171b8e5e4deb9bb967. */
public class GlUniformMatrix4f extends GlUniform<Matrix4fc>  {
    // One native-order upload buffer per uniform, as in Angelica.
    private final FloatBuffer buffer = ByteBuffer.allocateDirect(16 * Float.BYTES)
            .order(ByteOrder.nativeOrder()).asFloatBuffer();

    public GlUniformMatrix4f(int index) {
        super(index);
    }

    @Override
    public void set(Matrix4fc value) {
        value.get(this.buffer);
        LWJGL.glUniformMatrix4fv(this.index, false, this.buffer);
    }
}
