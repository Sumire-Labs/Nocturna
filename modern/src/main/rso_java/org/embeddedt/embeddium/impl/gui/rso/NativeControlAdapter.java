package org.embeddedt.embeddium.impl.gui.rso;

import org.embeddedt.embeddium.impl.gui.modern.framework.ModernDrawContext;
import org.embeddedt.embeddium.impl.gui.modern.framework.ModernInteractionContext;
import org.embeddedt.embeddium.impl.gui.rso.compat.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.Option;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.StatefulOption;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.ColorTheme;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i;

public final class NativeControlAdapter implements Control {
    private final org.taumc.celeritas.api.options.structure.Option<?> source;
    private final StatefulOption<?> option;

    public NativeControlAdapter(
            org.taumc.celeritas.api.options.structure.Option<?> source, StatefulOption<?> option) {
        this.source = source;
        this.option = option;
    }

    public Option getOption() {
        return option;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public ControlElement createElement(
            net.minecraft.client.gui.screens.Screen screen,
            AbstractOptionList list,
            Dim2i dim,
            ColorTheme theme) {
        // The host's custom control keeps ownership of its native row and writes into the RSO edit
        // transaction.
        var original = (org.taumc.celeritas.api.options.structure.Option) source;
        var initialDim =
                new org.embeddedt.embeddium.impl.util.Dim2i(
                        dim.x(), dim.y(), dim.width(), dim.height());
        return new ControlElement(dim) {
            private org.embeddedt.embeddium.impl.util.Dim2i origin = initialDim;
            private org.taumc.celeritas.api.options.control.ControlElement element =
                    original.getControl().createElement(origin);

            private int dx() {
                return getX() - origin.x();
            }

            private int dy() {
                return getY() - origin.y();
            }

            private void pull() {
                original.setValue(option.getValidatedValue());
            }

            private void push() {
                ((StatefulOption) option).modifyValue(original.getValue());
            }

            public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
                pull();
                var pose = g.nativeGraphics().pose();
                pose.pushPose();
                pose.translate(dx(), dy(), 0);
                try {
                    element.render(
                            new ModernDrawContext(
                                    g.nativeGraphics(),
                                    net.minecraft.client.Minecraft.getInstance().font),
                            x - dx(),
                            y - dy(),
                            delta);
                } finally {
                    pose.popPose();
                }
            }

            public boolean mouseClicked(MouseButtonEvent e, boolean repeated) {
                pull();
                boolean handled =
                        element.mouseClicked(
                                ModernInteractionContext.INSTANCE,
                                e.x() - dx(),
                                e.y() - dy(),
                                e.button());
                push();
                return handled;
            }

            public boolean mouseReleased(MouseButtonEvent e) {
                pull();
                boolean handled =
                        element.mouseReleased(
                                ModernInteractionContext.INSTANCE,
                                e.x() - dx(),
                                e.y() - dy(),
                                e.button());
                push();
                return handled;
            }

            public boolean mouseDragged(MouseButtonEvent e, double x, double y) {
                pull();
                boolean handled =
                        element.mouseDragged(
                                ModernInteractionContext.INSTANCE,
                                e.x() - dx(),
                                e.y() - dy(),
                                e.button(),
                                x,
                                y);
                push();
                return handled;
            }

            public boolean mouseScrolled(double x, double y, double sx, double sy) {
                pull();
                boolean handled =
                        element.mouseScrolled(
                                ModernInteractionContext.INSTANCE, x - dx(), y - dy(), sx, sy);
                push();
                return handled;
            }

            public boolean keyPressed(KeyEvent e) {
                pull();
                boolean handled =
                        element
                                        instanceof
                                        net.minecraft.client.gui.components.events.GuiEventListener
                                                input
                                && input.keyPressed(e.key(), e.scancode(), e.modifiers());
                push();
                return handled;
            }

            public boolean keyReleased(KeyEvent e) {
                return element
                                instanceof
                                net.minecraft.client.gui.components.events.GuiEventListener input
                        && input.keyReleased(e.key(), e.scancode(), e.modifiers());
            }

            public boolean charTyped(CharacterEvent e) {
                if (!(element
                        instanceof
                        net.minecraft.client.gui.components.events.GuiEventListener input))
                    return false;
                pull();
                boolean handled = false;
                for (char c : Character.toChars(e.codepoint()))
                    handled |= input.charTyped(c, e.modifiers());
                push();
                return handled;
            }

            public void setFocused(boolean focused) {
                super.setFocused(focused);
                if (element
                        instanceof
                        net.minecraft.client.gui.components.events.GuiEventListener input)
                    input.setFocused(focused);
            }

            public void updateNarration(
                    net.minecraft.client.gui.narration.NarrationElementOutput output) {
                if (element instanceof net.minecraft.client.gui.narration.NarratableEntry entry)
                    entry.updateNarration(output);
                else
                    output.add(
                            net.minecraft.client.gui.narration.NarratedElementType.TITLE,
                            option.getName());
            }

            public void rso$setDimensions(Dim2i updated) {
                super.rso$setDimensions(updated);
                if (origin.width() != updated.width() || origin.height() != updated.height()) {
                    origin =
                            new org.embeddedt.embeddium.impl.util.Dim2i(
                                    updated.x(), updated.y(), updated.width(), updated.height());
                    element = original.getControl().createElement(origin);
                    if (element
                            instanceof
                            net.minecraft.client.gui.components.events.GuiEventListener input)
                        input.setFocused(isFocused());
                }
            }
        };
    }
}
