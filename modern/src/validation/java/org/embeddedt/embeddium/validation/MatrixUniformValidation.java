package org.embeddedt.embeddium.validation;

import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix3f;
import org.embeddedt.embeddium.impl.gl.shader.uniform.GlUniformMatrix4f;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL20C;
import org.lwjgl.system.MemoryStack;

/** Reads the real driver's uniform values back after repeated production uploads. Test mod only. */
final class MatrixUniformValidation {
    static void verify() {
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int vertex = shader(GL20C.GL_VERTEX_SHADER, """
                #version 150
                uniform mat3 test3;
                uniform mat4 test4;
                in vec3 position;
                void main() { gl_Position = test4 * vec4(test3 * position, 1.0); }
                """);
        int fragment = shader(GL20C.GL_FRAGMENT_SHADER, """
                #version 150
                out vec4 color;
                void main() { color = vec4(1.0); }
                """);
        int program = GL20C.glCreateProgram();
        try {
            GL20C.glAttachShader(program, vertex);
            GL20C.glAttachShader(program, fragment);
            GL20C.glLinkProgram(program);
            if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0) {
                throw new AssertionError(GL20C.glGetProgramInfoLog(program));
            }
            GL20C.glUseProgram(program);
            int index3 = GL20C.glGetUniformLocation(program, "test3");
            int index4 = GL20C.glGetUniformLocation(program, "test4");
            if (index3 < 0 || index4 < 0) throw new AssertionError("Matrix uniforms were optimized away");
            var uniform3 = new GlUniformMatrix3f(index3);
            var uniform4 = new GlUniformMatrix4f(index4);
            try (var stack = MemoryStack.stackPush()) {
                var readback = stack.mallocFloat(16);
                for (int iteration = 0; iteration < 32; iteration++) {
                    float[] values = new float[16];
                    for (int i = 0; i < values.length; i++) values[i] = iteration * 16 + i + .25f;
                    uniform3.set(new Matrix3f().set(values));
                    GL20C.glGetUniformfv(program, index3, readback);
                    for (int i = 0; i < 9; i++) check(values[i], readback.get(i));
                    uniform4.set(new Matrix4f().set(values));
                    GL20C.glGetUniformfv(program, index4, readback);
                    for (int i = 0; i < 16; i++) check(values[i], readback.get(i));
                }
            }
            if (GL11C.glGetError() != GL11C.GL_NO_ERROR) throw new AssertionError("GL error in matrix validation");
            System.out.println("PASS matrix uniform reuse: exact GPU readback for 32 updates of mat3 and mat4");
        } finally {
            GL20C.glUseProgram(previous);
            GL20C.glDeleteProgram(program);
            GL20C.glDeleteShader(vertex);
            GL20C.glDeleteShader(fragment);
        }
    }

    private static void check(float expected, float actual) {
        if (Float.floatToRawIntBits(expected) != Float.floatToRawIntBits(actual)) {
            throw new AssertionError("Matrix uniform differs: " + expected + " != " + actual);
        }
    }

    private static int shader(int type, String source) {
        int shader = GL20C.glCreateShader(type);
        GL20C.glShaderSource(shader, source);
        GL20C.glCompileShader(shader);
        if (GL20C.glGetShaderi(shader, GL20C.GL_COMPILE_STATUS) == 0) {
            String error = GL20C.glGetShaderInfoLog(shader);
            GL20C.glDeleteShader(shader);
            throw new AssertionError(error);
        }
        return shader;
    }
}
