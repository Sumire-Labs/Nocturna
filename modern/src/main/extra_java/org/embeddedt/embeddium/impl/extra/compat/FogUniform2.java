package org.embeddedt.embeddium.impl.extra.compat;

import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniform;

public final class FogUniform2 extends GlUniform<float[]> {
    public FogUniform2(int index) {
        super(index);
    }

    @Override
    public void set(float[] value) {
        org.lwjgl.opengl.GL20C.glUniform2f(this.index, value[0], value[1]);
    }
}
