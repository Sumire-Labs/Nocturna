package org.embeddedt.embeddium.impl.render.weather;

import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

/** Angelica's four-corner fan, with only desktop OpenGL attribute bindings adapted. */
final class WeatherQuadMesh {
    static final int VERTEX_COUNT = 4;
    private static final float[] CORNERS = {0, 0, 1, 0, 1, 1, 0, 1};
    private static int vao, vbo;

    static int vao() {
        if (vao != 0) return vao;
        vao = GL30C.glGenVertexArrays(); GL30C.glBindVertexArray(vao);
        vbo = GL15C.glGenBuffers(); GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo);
        try (var stack = MemoryStack.stackPush()) {
            GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, stack.floats(CORNERS), GL15C.GL_STATIC_DRAW);
        }
        GL20C.glEnableVertexAttribArray(0);
        GL20C.glVertexAttribPointer(0, 2, GL11C.GL_FLOAT, false, 8, 0L);
        WeatherInstancedAttribs.enableInstanceArrays();
        GL30C.glBindVertexArray(0); GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, 0);
        return vao;
    }

    static void delete() {
        if (vbo != 0) {GL15C.glDeleteBuffers(vbo);vbo = 0;}
        if (vao != 0) {GL30C.glDeleteVertexArrays(vao);vao = 0;}
    }
}
