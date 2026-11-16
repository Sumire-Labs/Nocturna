package org.embeddedt.embeddium.impl.gui.rso.compat;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

public final class SDLMouse {
    public static final int SDL_BUTTON_LMASK = 1;

    public static int SDL_GetMouseState(java.nio.FloatBuffer x, java.nio.FloatBuffer y) {
        return GLFW.glfwGetMouseButton(Minecraft.getInstance().getWindow().getWindow(), 0)
                        == GLFW.GLFW_PRESS
                ? 1
                : 0;
    }
}
