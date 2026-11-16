package org.embeddedt.embeddium.impl.gui.rso.compat;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public abstract class RsoScreen extends net.minecraft.client.gui.screens.Screen {
    private char pendingHighSurrogate;

    protected RsoScreen(Component title) {
        super(title);
    }

    public void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta) {
        renderBackground(graphics.nativeGraphics());
        super.render(graphics.nativeGraphics(), x, y, delta);
    }

    @Override
    public final void render(GuiGraphics graphics, int x, int y, float delta) {
        CursorTypes.reset();
        extractRenderState(new GuiGraphicsExtractor(graphics), x, y, delta);
    }

    @Override
    protected void updateNarratedWidget(
            net.minecraft.client.gui.narration.NarrationElementOutput output) {
        var entries =
                ((org.embeddedt.embeddium.impl.mixin.core.gui.ScreenNarratablesAccessor) this)
                        .nocturna$getNarratables();
        var roots = java.util.List.copyOf(entries);
        entries.clear();
        for (var entry : roots) {
            if (entry instanceof NarratableEntry composite)
                entries.addAll(composite.getNarratables());
            else entries.add(entry);
        }
        try {
            super.updateNarratedWidget(output);
        } finally {
            entries.clear();
            entries.addAll(roots);
        }
    }

    @Override
    public void removed() {
        CursorTypes.release();
        TextInputBridge.clearFocus();
        super.removed();
    }

    public boolean keyPressed(KeyEvent event) {
        return super.keyPressed(event.key(), event.scancode(), event.modifiers());
    }

    public boolean keyReleased(KeyEvent event) {
        return super.keyReleased(event.key(), event.scancode(), event.modifiers());
    }

    public boolean mouseClicked(MouseButtonEvent event, boolean repeated) {
        return super.mouseClicked(event.x(), event.y(), event.button());
    }

    public boolean mouseReleased(MouseButtonEvent event) {
        return super.mouseReleased(event.x(), event.y(), event.button());
    }

    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return super.mouseDragged(event.x(), event.y(), event.button(), dx, dy);
    }

    public boolean charTyped(CharacterEvent event) {
        return getFocused() != null && getFocused().charTyped(event);
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return super.mouseScrolled(x, y, dy);
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        return keyPressed(new KeyEvent(key, scan, mods));
    }

    @Override
    public boolean keyReleased(int key, int scan, int mods) {
        return keyReleased(new KeyEvent(key, scan, mods));
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return mouseClicked(new MouseButtonEvent(x, y, button, InputState.modifiers()), false);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        return mouseReleased(new MouseButtonEvent(x, y, button, InputState.modifiers()));
    }

    @Override
    public boolean mouseDragged(double x, double y, int b, double dx, double dy) {
        return mouseDragged(new MouseButtonEvent(x, y, b, InputState.modifiers()), dx, dy);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double dy) {
        return mouseScrolled(x, y, 0, dy);
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (Character.isHighSurrogate(c)) {
            pendingHighSurrogate = c;
            return true;
        }
        int cp = c;
        if (Character.isLowSurrogate(c) && pendingHighSurrogate != 0)
            cp = Character.toCodePoint(pendingHighSurrogate, c);
        pendingHighSurrogate = 0;
        return charTyped(new CharacterEvent(cp, mods));
    }

    @Override
    public GuiEventListener getFocused() {
        return (GuiEventListener) super.getFocused();
    }
}
