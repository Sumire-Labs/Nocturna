package org.embeddedt.embeddium.impl.gui.rso.compat;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.NativeLibrary;
import com.sun.jna.Pointer;

import net.minecraft.client.Minecraft;

import org.lwjgl.glfw.GLFWNativeWin32;
import org.lwjgl.system.Platform;

/** GLFW delivers committed Unicode text; IMM supplies the Windows composition caret. */
public final class TextInputBridge {
    private static GuiEventListener owner;
    private static final NativeLibrary IMM =
            Platform.get() == Platform.WINDOWS ? NativeLibrary.getInstance("imm32") : null;

    private TextInputBridge() {}

    public static void clearFocus() {
        owner = null;
    }

    public static void onFocusChange(GuiEventListener widget, boolean active) {
        if (active) owner = widget;
        else if (owner == widget) owner = null;
    }

    public static void setTextInputArea(int x, int y, int x2, int y2) {
        if (owner == null || IMM == null) return;
        var window = Minecraft.getInstance().getWindow();
        Pointer hwnd = new Pointer(GLFWNativeWin32.glfwGetWin32Window(window.getWindow()));
        Pointer context = function("ImmGetContext").invokePointer(new Object[] {hwnd});
        if (context == null) return;
        try {
            Memory form = new Memory(28);
            form.clear();
            form.setInt(0, 2); // CFS_POINT
            double scale = window.getGuiScale();
            form.setInt(4, (int) (x * scale));
            form.setInt(8, (int) (y2 * scale));
            function("ImmSetCompositionWindow").invokeInt(new Object[] {context, form});
        } finally {
            function("ImmReleaseContext").invokeInt(new Object[] {hwnd, context});
        }
    }

    private static Function function(String name) {
        return IMM.getFunction(name, Function.ALT_CONVENTION);
    }
}
