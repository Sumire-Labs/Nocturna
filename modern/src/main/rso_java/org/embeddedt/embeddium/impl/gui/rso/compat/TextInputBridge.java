package org.embeddedt.embeddium.impl.gui.rso.compat;

import net.minecraft.client.Minecraft;

import org.embeddedt.embeddium.impl.mixin.core.gui.RsoKeyboardInvoker;

/** A scoped SDL input session; GLFW retains the window, mouse and rendering context. */
public final class TextInputBridge {
    private static SdlTextInput backend;
    private static GuiEventListener owner;
    private static boolean dispatching;
    private static Integer logicalKey;

    private TextInputBridge() {}

    public static void beginSession() {
        if (backend == null)
            backend =
                    new SdlTextInput(
                            new SdlTextInput.Host() {
                                public long glfwWindow() {
                                    return Minecraft.getInstance().getWindow().getWindow();
                                }

                                public double coordinateScale() {
                                    var w = Minecraft.getInstance().getWindow();
                                    return w.getGuiScale()
                                            * w.getScreenWidth()
                                            / (double) w.getWidth();
                                }

                                public java.nio.file.Path cacheDirectory() {
                                    return net.minecraftforge.fml.loading.FMLPaths.GAMEDIR
                                            .get()
                                            .resolve(".nocturna/native/sdl3");
                                }

                                public void acceptText(String text) {
                                    TextInputBridge.acceptText(text);
                                }

                                public void acceptKey(
                                        int scan, int raw, int action, int modifiers, int keycode) {
                                    TextInputBridge.acceptKey(
                                            scan, raw, action, modifiers, keycode);
                                }
                            });
    }

    public static void clearFocus() {
        owner = null;
        if (backend != null) {
            var previous = backend;
            backend = null;
            previous.close();
        }
    }

    public static void onFocusChange(GuiEventListener widget, boolean active) {
        if (active) {
            beginSession();
            if (owner == widget) return;
            if (owner != null) backend.stop();
            owner = widget;
            backend.start();
        } else if (owner == widget) {
            owner = null;
            if (backend != null) backend.stop();
        }
    }

    public static void setTextInputArea(int x, int y, int x2, int y2) {
        if (owner != null && backend != null) backend.area(x, y, x2, y2);
    }

    public static void pumpEvents() {
        if (backend != null) backend.pump();
    }

    public static void prepareEvents() {
        if (backend != null) backend.prepare();
    }

    public static boolean ownsKeyboard() {
        return backend != null;
    }

    public static boolean isDispatching() {
        return dispatching;
    }

    public static Integer logicalKey() {
        return logicalKey;
    }

    public static int modifiers() {
        return backend != null ? backend.modifiers() : InputState.glfwModifiers();
    }

    static void acceptText(String text) {
        if (owner == null) return;
        var mc = Minecraft.getInstance();
        var input = (RsoKeyboardInvoker) mc.keyboardHandler;
        boolean previous = dispatching;
        dispatching = true;
        try {
            text.codePoints()
                    .forEach(
                            cp ->
                                    input.nocturna$charTyped(
                                            mc.getWindow().getWindow(), cp, modifiers()));
        } finally {
            dispatching = previous;
        }
    }

    static void acceptKey(int scan, int raw, int action, int mods, int keycode) {
        var mc = Minecraft.getInstance();
        boolean previous = dispatching;
        Integer oldLogical = logicalKey;
        dispatching = true;
        int key = SdlKeyMapping.glfwKey(scan);
        logicalKey =
                keycode >= 32 && keycode != 127 && keycode <= Character.MAX_CODE_POINT
                        ? Character.toUpperCase(keycode)
                        : key;
        try {
            ((RsoKeyboardInvoker) mc.keyboardHandler)
                    .nocturna$keyPress(
                            mc.getWindow().getWindow(),
                            key,
                            raw,
                            action,
                            SdlTextInput.modifiers(mods));
        } finally {
            dispatching = previous;
            logicalKey = oldLogical;
        }
    }
}
