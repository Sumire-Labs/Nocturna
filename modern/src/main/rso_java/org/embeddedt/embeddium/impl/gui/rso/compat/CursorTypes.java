package org.embeddedt.embeddium.impl.gui.rso.compat;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

public final class CursorTypes {
    public static final int POINTING_HAND = GLFW.GLFW_HAND_CURSOR,
            RESIZE_EW = GLFW.GLFW_HRESIZE_CURSOR;
    private static final java.util.Map<Integer, Long> cursors = new java.util.HashMap<>();

    public static void reset() {
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), 0);
    }

    public static void release() {
        reset();
        cursors.values().forEach(GLFW::glfwDestroyCursor);
        cursors.clear();
    }

    public static void request(int type) {
        long cursor = cursors.computeIfAbsent(type, GLFW::glfwCreateStandardCursor);
        GLFW.glfwSetCursor(Minecraft.getInstance().getWindow().getWindow(), cursor);
    }
}
