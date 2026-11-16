package net.irisshaders.iris.cache;

import org.lwjgl.opengl.ARBGetProgramBinary;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11C;
import org.lwjgl.opengl.GL41C;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Desktop OpenGL binding for Angelica's backend-neutral program-binary operations. */
final class ProgramBinaryAccess {
    static boolean supported() {
        var caps = GL.getCapabilities();
        return (caps.OpenGL41 || caps.GL_ARB_get_program_binary)
                && GL11C.glGetInteger(GL41C.GL_NUM_PROGRAM_BINARY_FORMATS) > 0;
    }

    static void parameter(int program, int name, int value) {
        if (GL.getCapabilities().OpenGL41) GL41C.glProgramParameteri(program, name, value);
        else ARBGetProgramBinary.glProgramParameteri(program, name, value);
    }

    static void load(int program, int format, ByteBuffer data) {
        if (GL.getCapabilities().OpenGL41) GL41C.glProgramBinary(program, format, data);
        else ARBGetProgramBinary.glProgramBinary(program, format, data);
    }

    static void read(int program, IntBuffer written, IntBuffer format, ByteBuffer data) {
        if (GL.getCapabilities().OpenGL41) GL41C.glGetProgramBinary(program, written, format, data);
        else ARBGetProgramBinary.glGetProgramBinary(program, written, format, data);
    }
}
