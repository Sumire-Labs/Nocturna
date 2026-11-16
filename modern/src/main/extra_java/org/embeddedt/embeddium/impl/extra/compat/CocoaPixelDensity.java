package org.embeddedt.embeddium.impl.extra.compat;

/**
 * Apply the same NSOpenGLView property used by GLFW's Retina creation hint to a reused Forge
 * window.
 */
public final class CocoaPixelDensity {
    public static void disable(long window) {
        if (net.minecraft.Util.getPlatform() != net.minecraft.Util.OS.OSX) return;
        long nativeWindow = org.lwjgl.glfw.GLFWNativeCocoa.glfwGetCocoaWindow(window);
        long send =
                org.lwjgl.system.macosx.ObjCRuntime.getLibrary().getFunctionAddress("objc_msgSend");
        long view = org.lwjgl.system.JNI.invokePPP(nativeWindow, selector("contentView"), send);
        org.lwjgl.system.JNI.invokePPV(
                view, selector("setWantsBestResolutionOpenGLSurface:"), false, send);
        long context = org.lwjgl.glfw.GLFWNativeNSGL.glfwGetNSGLContext(window);
        if (context != 0) org.lwjgl.system.JNI.invokePPV(context, selector("update"), send);
        if (org.lwjgl.system.JNI.invokePPZ(
                view, selector("wantsBestResolutionOpenGLSurface"), send))
            throw new IllegalStateException("Cocoa retained a high-density OpenGL surface");
    }

    private static long selector(String value) {
        return org.lwjgl.system.macosx.ObjCRuntime.sel_registerName(value);
    }
}
