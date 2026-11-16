package org.embeddedt.embeddium.impl.gui.rso.compat;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFW;

public record KeyEvent(int key, int scancode, int modifiers) {
    public int shortcutKey() {
        String name = GLFW.glfwGetKeyName(key, scancode);
        if (name != null && name.codePointCount(0, name.length()) == 1) {
            return Character.toUpperCase(name.codePointAt(0));
        }
        return key;
    }

    public boolean hasShiftDown() {
        return (modifiers & GLFW.GLFW_MOD_SHIFT) != 0;
    }

    public boolean hasAltDown() {
        return (modifiers & GLFW.GLFW_MOD_ALT) != 0;
    }

    public boolean hasControlDown() {
        return (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
    }

    public boolean hasControlDownWithQuirk() {
        return Minecraft.ON_OSX ? (modifiers & GLFW.GLFW_MOD_SUPER) != 0 : hasControlDown();
    }

    public boolean isEscape() {
        return key == GLFW.GLFW_KEY_ESCAPE;
    }

    public boolean isSelection() {
        return isConfirmation() || key == GLFW.GLFW_KEY_SPACE;
    }

    public boolean isConfirmation() {
        return key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER;
    }

    public boolean isLeft() {
        return key == GLFW.GLFW_KEY_LEFT;
    }

    public boolean isRight() {
        return key == GLFW.GLFW_KEY_RIGHT;
    }

    private boolean editShortcut(int expected) {
        return shortcutKey() == expected
                && hasControlDownWithQuirk()
                && !hasShiftDown()
                && !hasAltDown();
    }

    public boolean isSelectAll() {
        return editShortcut(GLFW.GLFW_KEY_A);
    }

    public boolean isCopy() {
        return editShortcut(GLFW.GLFW_KEY_C);
    }

    public boolean isPaste() {
        return editShortcut(GLFW.GLFW_KEY_V);
    }

    public boolean isCut() {
        return editShortcut(GLFW.GLFW_KEY_X);
    }
}
