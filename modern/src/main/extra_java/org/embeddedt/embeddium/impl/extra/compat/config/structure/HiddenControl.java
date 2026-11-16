package org.embeddedt.embeddium.impl.extra.compat.config.structure;

import me.jellysquid.mods.sodium.client.gui.options.control.*;
import me.jellysquid.mods.sodium.client.util.Dim2i;

import net.minecraft.client.gui.GuiGraphics;

/** Preserve Extra's distinction between a disabled control and a hidden disabled control. */
final class HiddenControl<T> implements Control<T> {
    private final ExtraOption<T> option;
    private final Control<T> delegate;

    HiddenControl(ExtraOption<T> option, Control<T> delegate) {
        this.option = option;
        this.delegate = delegate;
    }

    public ExtraOption<T> getOption() {
        return option;
    }

    private boolean hidden() {
        return option.spec.hideWhenDisabled && !option.isAvailable();
    }

    public int getMaxWidth() {
        return hidden() ? 0 : delegate.getMaxWidth();
    }

    public ControlElement<T> createElement(Dim2i dim) {
        ControlElement<T> widget = delegate.createElement(dim);
        return new ControlElement<>(option, dim) {
            public void render(GuiGraphics gui, int x, int y, float delta) {
                if (hidden()) super.render(gui, x, y, delta);
                else widget.render(gui, x, y, delta);
            }

            public void setFocused(boolean focused) {
                super.setFocused(focused);
                widget.setFocused(focused);
            }

            public boolean mouseClicked(double x, double y, int button) {
                return !hidden() && widget.mouseClicked(x, y, button);
            }

            public boolean mouseReleased(double x, double y, int button) {
                return !hidden() && widget.mouseReleased(x, y, button);
            }

            public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
                return !hidden() && widget.mouseDragged(x, y, button, dx, dy);
            }

            public boolean mouseScrolled(double x, double y, double delta) {
                return !hidden() && widget.mouseScrolled(x, y, delta);
            }

            public boolean keyPressed(int key, int scan, int modifiers) {
                return !hidden() && widget.keyPressed(key, scan, modifiers);
            }
        };
    }
}
