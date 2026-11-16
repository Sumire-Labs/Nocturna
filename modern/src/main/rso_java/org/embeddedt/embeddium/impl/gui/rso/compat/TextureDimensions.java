package org.embeddedt.embeddium.impl.gui.rso.compat;

import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.resources.ResourceLocation;

import org.lwjgl.opengl.GL11;

public final class TextureDimensions {
    private TextureDimensions() {}

    public static AbstractTexture load(ResourceLocation id) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        var manager = Minecraft.getInstance().getTextureManager();
        try {
            manager.bindForSetup(id);
            return manager.getTexture(id);
        } finally {
            GlStateManager._bindTexture(previous);
        }
    }

    public static int width(AbstractTexture texture) {
        return dimension(texture, GL11.GL_TEXTURE_WIDTH);
    }

    public static int height(AbstractTexture texture) {
        return dimension(texture, GL11.GL_TEXTURE_HEIGHT);
    }

    private static int dimension(AbstractTexture texture, int parameter) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        GlStateManager._bindTexture(texture.getId());
        try {
            return GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, parameter);
        } finally {
            GlStateManager._bindTexture(previous);
        }
    }
}
