package me.flashyreese.mods.reeses_sodium_options.client.gui.frame.option;

import me.flashyreese.mods.reeses_sodium_options.client.gui.layout.LayoutBounds;
import me.flashyreese.mods.reeses_sodium_options.client.gui.state.OptionsScreenUiState;
import me.flashyreese.mods.reeses_sodium_options.client.gui.theme.GuiThemes;
import me.flashyreese.mods.reeses_sodium_options.client.gui.widget.BaseWidget;

import net.minecraft.client.gui.ComponentPath;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.navigation.FocusNavigationEvent;

import org.embeddedt.embeddium.impl.gui.rso.compat.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.config.structure.*;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.ColorTheme;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.gui.options.control.ControlElement;
import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.client.util.Dim2i;

/** RSO-backed fallback element for the four builtin control contracts. */
public final class NativeRowControlElement extends ControlElement {
    private final OptionRow row;

    public NativeRowControlElement(
            net.minecraft.client.gui.screens.Screen screen,
            Dim2i dim,
            ColorTheme theme,
            Option option) {
        super(dim);
        var bounds = new LayoutBounds(dim.x(), dim.y(), dim.width(), dim.height());
        var palette = GuiThemes.fromSodium(theme);
        var state = new OptionsScreenUiState();
        if (option instanceof BooleanOption value)
            row = new BooleanOptionRow(bounds, palette, state, value);
        else if (option instanceof IntegerOption value)
            row = new IntegerSliderOptionRow(bounds, palette, state, value);
        else if (option instanceof EnumOption<?> value)
            row = new EnumOptionRow<>(bounds, palette, state, value);
        else if (option instanceof ExternalButtonOption value)
            row = new ExternalButtonOptionRow(screen, bounds, palette, state, value);
        else throw new IllegalArgumentException("Not a builtin RSO row: " + option.getClass());
    }

    public void extractRenderState(GuiGraphicsExtractor g, int x, int y, float delta) {
        ((Renderable) row).extractRenderState(g, x, y, delta);
    }

    public boolean mouseClicked(MouseButtonEvent e, boolean repeated) {
        return ((GuiEventListener) row).mouseClicked(e, repeated);
    }

    public boolean mouseReleased(MouseButtonEvent e) {
        return ((GuiEventListener) row).mouseReleased(e);
    }

    public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
        return ((GuiEventListener) row).mouseDragged(e, dx, dy);
    }

    public boolean mouseScrolled(double x, double y, double dx, double dy) {
        return ((GuiEventListener) row).mouseScrolled(x, y, dx, dy);
    }

    public boolean keyPressed(KeyEvent e) {
        return ((GuiEventListener) row).keyPressed(e);
    }

    public boolean isFocused() {
        return row.isFocused();
    }

    public void setFocused(boolean focused) {
        row.setFocused(focused);
    }

    public ComponentPath nextFocusPath(FocusNavigationEvent navigation) {
        return row.nextFocusPath(navigation);
    }

    public NarrationPriority narrationPriority() {
        return row.narrationPriority();
    }

    public void updateNarration(NarrationElementOutput output) {
        row.updateNarration(output);
    }

    public void rso$setDimensions(Dim2i dim) {
        super.rso$setDimensions(dim);
        ((BaseWidget) row).setDim(new LayoutBounds(dim.x(), dim.y(), dim.width(), dim.height()));
    }
}
