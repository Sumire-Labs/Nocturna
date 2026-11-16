package org.embeddedt.embeddium.impl.gui.rso.compat;

import net.minecraft.client.gui.GuiGraphics;

public interface Renderable extends net.minecraft.client.gui.components.Renderable {
    void extractRenderState(GuiGraphicsExtractor graphics, int x, int y, float delta);

    @Override
    default void render(GuiGraphics graphics, int x, int y, float delta) {
        extractRenderState(new GuiGraphicsExtractor(graphics), x, y, delta);
    }
}
