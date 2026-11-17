package org.embeddedt.embeddium.impl.gui.rso.compat;

import net.minecraft.client.Minecraft;

import org.embeddedt.embeddium.impl.mixin.core.gui.RsoKeyboardInvoker;

/** A scoped SDL input session; GLFW retains the window, mouse and rendering context. */
public final class TextInputBridge {
    private static final org.slf4j.Logger LOGGER =
            org.slf4j.LoggerFactory.getLogger("Nocturna-Input");
    private static java.util.function.Supplier<SdlTextInput> backendFactory =
            TextInputBridge::createBackend;
    private static boolean unavailable;
    private static SdlTextInput backend;
    private static net.minecraft.client.gui.components.events.GuiEventListener owner;
    private static boolean dispatching;
    private static Integer logicalKey;

    private TextInputBridge() {}

    public static void beginSession() {
        if (backend != null || unavailable) return;
        try {
            backend = java.util.Objects.requireNonNull(backendFactory.get());
        } catch (RuntimeException | LinkageError failure) {
            unavailable = true;
            LOGGER.warn(
                    "SDL text input is unavailable; keeping GLFW keyboard input for this settings"
                        + " session",
                    failure);
        }
    }

    private static SdlTextInput createBackend() {
        return new SdlTextInput(
                new SdlTextInput.Host() {
                    public long glfwWindow() {
                        return Minecraft.getInstance().getWindow().getWindow();
                    }

                    public double coordinateScale() {
                        var w = Minecraft.getInstance().getWindow();
                        return w.getGuiScale() * w.getScreenWidth() / (double) w.getWidth();
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
                        TextInputBridge.acceptKey(scan, raw, action, modifiers, keycode);
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
        unavailable = false;
    }

    public static void onFocusChange(
            net.minecraft.client.gui.components.events.GuiEventListener widget, boolean active) {
        if (active) {
            beginSession();
            if (backend == null) return;
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
        if (backend == null) return;
        var minecraft = Minecraft.getInstance();
        if (minecraft != null && minecraft.screen instanceof RsoScreen screen) syncFocus(screen);
        if (backend != null) backend.prepare();
    }

    public static void syncFocus(RsoScreen screen) {
        if (backend == null || Minecraft.getInstance().screen != screen) return;
        net.minecraft.client.gui.components.events.GuiEventListener leaf = screen.getFocused();
        while (leaf
                instanceof
                net.minecraft.client.gui.components.events.ContainerEventHandler container) {
            var next = container.getFocused();
            if (next == null || next == leaf) break;
            leaf = next;
        }
        boolean captures =
                leaf instanceof GuiEventListener compatible && compatible.capturesInput()
                        || leaf instanceof net.minecraft.client.gui.components.EditBox field
                                && field.canConsumeInput();
        if (!captures) {
            if (owner != null) onFocusChange(owner, false);
            return;
        }
        onFocusChange(leaf, true);
        if (leaf instanceof net.minecraft.client.gui.components.EditBox field) {
            var accessor = (org.embeddedt.embeddium.impl.mixin.core.gui.RsoEditBoxAccessor) field;
            int x = field.getScreenX(field.getCursorPosition());
            int y =
                    field.getY()
                            + (accessor.nocturna$isBordered() ? (field.getHeight() - 8) / 2 : 0);
            setTextInputArea(x, y, x + 1, y + accessor.nocturna$getFont().lineHeight + 1);
        }
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
