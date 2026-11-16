package org.embeddedt.embeddium.impl.extra.compat;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

public final class ExtraPlatform {
    private static long probedContext, knownContext;
    private static boolean adaptiveSupported;
    private static int knownInterval;

    public static boolean isAdaptiveSyncSupported() {
        long context = GLFW.glfwGetCurrentContext();
        if (context == 0) return false;
        if (context == probedContext) return adaptiveSupported;
        probedContext = context;
        adaptiveSupported = false;
        if (!(GLFW.glfwExtensionSupported("WGL_EXT_swap_control_tear")
                || GLFW.glfwExtensionSupported("GLX_EXT_swap_control_tear")
                || GLFW.glfwExtensionSupported("EGL_EXT_swap_control_tear"))) return false;
        int previous = currentInterval(context);
        GLFW.glfwGetError((org.lwjgl.PointerBuffer) null);
        try {
            GLFW.glfwSwapInterval(-1);
            adaptiveSupported =
                    GLFW.glfwGetError((org.lwjgl.PointerBuffer) null) == GLFW.GLFW_NO_ERROR;
            if (adaptiveSupported && windowsSwapQuery())
                adaptiveSupported =
                        org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT() == -1;
        } finally {
            GLFW.glfwSwapInterval(previous);
            GLFW.glfwGetError((org.lwjgl.PointerBuffer) null);
        }
        return adaptiveSupported;
    }

    private static boolean windowsSwapQuery() {
        return net.minecraft.Util.getPlatform() == net.minecraft.Util.OS.WINDOWS
                && GLFW.glfwExtensionSupported("WGL_EXT_swap_control");
    }

    private static int currentInterval(long context) {
        return windowsSwapQuery()
                ? org.lwjgl.opengl.WGLEXTSwapControl.wglGetSwapIntervalEXT()
                : knownContext == context ? knownInterval : 0;
    }

    public static void recordSwapInterval(int interval) {
        knownContext = GLFW.glfwGetCurrentContext();
        knownInterval = interval;
    }

    public static void updateVsync() {
        Minecraft m = Minecraft.getInstance();
        m.getWindow().updateVsync(m.options.enableVsync().get());
    }
}
