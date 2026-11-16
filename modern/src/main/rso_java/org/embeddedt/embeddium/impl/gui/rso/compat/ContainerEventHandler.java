package org.embeddedt.embeddium.impl.gui.rso.compat;

public interface ContainerEventHandler
        extends GuiEventListener, net.minecraft.client.gui.components.events.ContainerEventHandler {
    @Override
    GuiEventListener getFocused();

    void setFocused(GuiEventListener listener);

    @Override
    default void setFocused(net.minecraft.client.gui.components.events.GuiEventListener listener) {
        setFocused((GuiEventListener) listener);
    }

    @Override
    default boolean mouseClicked(MouseButtonEvent e, boolean repeated) {
        for (var child : children())
            if (child instanceof GuiEventListener listener && listener.mouseClicked(e, repeated)) {
                setFocused(child);
                if (e.button() == 0) setDragging(true);
                return true;
            }
        return false;
    }

    @Override
    default boolean mouseReleased(MouseButtonEvent e) {
        setDragging(false);
        return getChildAt(e.x(), e.y())
                .filter(c -> c instanceof GuiEventListener listener && listener.mouseReleased(e))
                .isPresent();
    }

    @Override
    default boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return isDragging()
                && e.button() == 0
                && getFocused() != null
                && getFocused().mouseDragged(e, dx, dy);
    }

    @Override
    default boolean mouseScrolled(double x, double y, double dx, double dy) {
        return getChildAt(x, y)
                .filter(
                        c ->
                                c instanceof GuiEventListener listener
                                        && listener.mouseScrolled(x, y, dx, dy))
                .isPresent();
    }

    @Override
    default boolean keyPressed(KeyEvent e) {
        return getFocused() != null && getFocused().keyPressed(e);
    }

    @Override
    default boolean keyReleased(KeyEvent e) {
        return getFocused() != null && getFocused().keyReleased(e);
    }

    @Override
    default boolean charTyped(CharacterEvent e) {
        return getFocused() != null && getFocused().charTyped(e);
    }

    @Override
    default boolean mouseClicked(double x, double y, int button) {
        return GuiEventListener.super.mouseClicked(x, y, button);
    }

    @Override
    default boolean mouseReleased(double x, double y, int button) {
        return GuiEventListener.super.mouseReleased(x, y, button);
    }

    @Override
    default boolean mouseDragged(double x, double y, int b, double dx, double dy) {
        return GuiEventListener.super.mouseDragged(x, y, b, dx, dy);
    }

    @Override
    default boolean mouseScrolled(double x, double y, double dy) {
        return GuiEventListener.super.mouseScrolled(x, y, dy);
    }

    @Override
    default boolean keyPressed(int k, int s, int m) {
        return GuiEventListener.super.keyPressed(k, s, m);
    }

    @Override
    default boolean keyReleased(int k, int s, int m) {
        return GuiEventListener.super.keyReleased(k, s, m);
    }

    @Override
    default boolean charTyped(char c, int m) {
        return GuiEventListener.super.charTyped(c, m);
    }
}
