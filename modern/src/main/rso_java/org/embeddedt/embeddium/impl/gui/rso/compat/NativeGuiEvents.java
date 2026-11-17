package org.embeddedt.embeddium.impl.gui.rso.compat;

/** Dispatches to both the ported RSO widgets and ordinary Minecraft/addon controls. */
public final class NativeGuiEvents {
    private NativeGuiEvents() {}

    public static boolean keyPressed(
            net.minecraft.client.gui.components.events.GuiEventListener listener, KeyEvent event) {
        return listener != null
                && listener.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    public static boolean charTyped(
            net.minecraft.client.gui.components.events.GuiEventListener listener,
            CharacterEvent event) {
        if (listener instanceof GuiEventListener compatible) return compatible.charTyped(event);
        if (listener == null) return false;
        boolean handled = false;
        for (char character : Character.toChars(event.codepoint()))
            handled |= listener.charTyped(character, event.modifiers());
        return handled;
    }

    public static boolean mouseScrolled(
            net.minecraft.client.gui.components.events.GuiEventListener listener,
            double x,
            double y,
            double horizontal,
            double vertical) {
        return listener instanceof GuiEventListener compatible
                ? compatible.mouseScrolled(x, y, horizontal, vertical)
                : listener.mouseScrolled(x, y, vertical);
    }
}
