package org.embeddedt.embeddium.impl.gui.rso.compat;

public interface GuiEventListener
        extends net.minecraft.client.gui.components.events.GuiEventListener {
    default boolean capturesInput() {
        return false;
    }

    default boolean mouseClicked(MouseButtonEvent event, boolean repeated) {
        return false;
    }

    default boolean mouseReleased(MouseButtonEvent event) {
        return false;
    }

    default boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return false;
    }

    default boolean mouseScrolled(double x, double y, double dx, double dy) {
        return false;
    }

    default boolean keyPressed(KeyEvent event) {
        return false;
    }

    default boolean keyReleased(KeyEvent event) {
        return false;
    }

    default boolean charTyped(CharacterEvent event) {
        return false;
    }

    @Override
    default boolean mouseClicked(double x, double y, int button) {
        return mouseClicked(new MouseButtonEvent(x, y, button, InputState.modifiers()), false);
    }

    @Override
    default boolean mouseReleased(double x, double y, int button) {
        return mouseReleased(new MouseButtonEvent(x, y, button, InputState.modifiers()));
    }

    @Override
    default boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return mouseDragged(new MouseButtonEvent(x, y, button, InputState.modifiers()), dx, dy);
    }

    @Override
    default boolean mouseScrolled(double x, double y, double dy) {
        return mouseScrolled(x, y, 0, dy);
    }

    @Override
    default boolean keyPressed(int key, int scan, int modifiers) {
        return keyPressed(new KeyEvent(key, scan, modifiers));
    }

    @Override
    default boolean keyReleased(int key, int scan, int modifiers) {
        return keyReleased(new KeyEvent(key, scan, modifiers));
    }

    @Override
    default boolean charTyped(char c, int modifiers) {
        return charTyped(new CharacterEvent(c, modifiers));
    }
}
