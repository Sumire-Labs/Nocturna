package org.embeddedt.embeddium.impl.extra.compat;

import org.lwjgl.glfw.*;
import org.lwjgl.opengl.*;

import java.util.OptionalInt;

public final class ExtraPlatform {
    private static final SwapIntervalProbe PROBE = new SwapIntervalProbe();
    private static long knownContext;
    private static int knownInterval;
    private static final SwapIntervalProbe.Backend BACKEND =
            new SwapIntervalProbe.Backend() {
                public long context() {
                    return GLFW.glfwGetCurrentContext();
                }

                public OptionalInt current() {
                    long context = context();
                    if (context == 0) return OptionalInt.empty();
                    var platform = net.minecraft.Util.getPlatform();
                    if (platform == net.minecraft.Util.OS.WINDOWS
                            && GLFW.glfwExtensionSupported("WGL_EXT_swap_control"))
                        return OptionalInt.of(WGLEXTSwapControl.wglGetSwapIntervalEXT());
                    if (platform == net.minecraft.Util.OS.LINUX
                            && GLFW.glfwGetWindowAttrib(context, GLFW.GLFW_CONTEXT_CREATION_API)
                                    == GLFW.GLFW_NATIVE_CONTEXT_API
                            && GLFW.glfwExtensionSupported("GLX_EXT_swap_control")) {
                        return OptionalInt.of(
                                GLX13.glXQueryDrawable(
                                        GLFWNativeX11.glfwGetX11Display(),
                                        GLFWNativeGLX.glfwGetGLXWindow(context),
                                        GLXEXTSwapControl.GLX_SWAP_INTERVAL_EXT));
                    }
                    return context == knownContext
                            ? OptionalInt.of(knownInterval)
                            : OptionalInt.empty();
                }

                public boolean set(int interval) {
                    GLFWErrorCallback previous = GLFW.glfwSetErrorCallback(null);
                    try {
                        GLFW.glfwGetError((org.lwjgl.PointerBuffer) null);
                        GLFW.glfwSwapInterval(interval);
                        boolean success =
                                GLFW.glfwGetError((org.lwjgl.PointerBuffer) null)
                                        == GLFW.GLFW_NO_ERROR;
                        if (success) recordSwapInterval(interval);
                        return success;
                    } finally {
                        GLFW.glfwSetErrorCallback(previous);
                    }
                }
            };

    public static boolean isAdaptiveSyncSupported() {
        return PROBE.supported(BACKEND);
    }

    public static void recordSwapInterval(int interval) {
        knownContext = GLFW.glfwGetCurrentContext();
        knownInterval = interval;
    }

    public static void updateVsync() {
        var m = net.minecraft.client.Minecraft.getInstance();
        m.getWindow().updateVsync(m.options.enableVsync().get());
    }
}
