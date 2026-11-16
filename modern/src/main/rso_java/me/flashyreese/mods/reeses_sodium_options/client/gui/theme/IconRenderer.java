package me.flashyreese.mods.reeses_sodium_options.client.gui.theme;

import net.minecraft.client.Minecraft;
import org.embeddedt.embeddium.impl.gui.rso.compat.GuiGraphicsExtractor;
import org.embeddedt.embeddium.impl.gui.rso.compat.RenderPipelines;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

public final class IconRenderer {
    public static int renderIconWithSpacing(GuiGraphicsExtractor guiGraphics, ResourceLocation icon, int color, boolean monochrome, int x, int y, int height, int spacing) {
        int iconSize = height - spacing * 2;
        AbstractTexture texture = org.embeddedt.embeddium.impl.gui.rso.compat.TextureDimensions.load(icon);
        int textureWidth = org.embeddedt.embeddium.impl.gui.rso.compat.TextureDimensions.width(texture);
        int textureHeight = org.embeddedt.embeddium.impl.gui.rso.compat.TextureDimensions.height(texture);
        int iconX = x + spacing;
        int iconY = y + height / 2 - iconSize / 2;

        if (monochrome) {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, textureWidth, textureHeight, textureWidth, textureHeight, color);
        } else {
            guiGraphics.blit(RenderPipelines.GUI_TEXTURED, icon, iconX, iconY, 0.0F, 0.0F, iconSize, iconSize, textureWidth, textureHeight, textureWidth, textureHeight);
        }

        return spacing * 2 + iconSize;
    }
}
