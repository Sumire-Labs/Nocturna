package org.embeddedt.embeddium.impl.gui.rso.compat;

import com.mojang.blaze3d.systems.RenderSystem;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

public final class GuiGraphicsExtractor {
    private final GuiGraphics graphics;

    public GuiGraphicsExtractor(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    public GuiGraphics nativeGraphics() {
        return graphics;
    }

    /** Keep an overlay above text queued by the 1.20.1 managed GUI renderer. */
    public void overlay(Runnable draw) {
        graphics.flush();
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 400);
        try {
            draw.run();
        } finally {
            try {
                graphics.flush();
            } finally {
                graphics.pose().popPose();
            }
        }
    }

    public void fill(int x, int y, int x2, int y2, int color) {
        graphics.fill(x, y, x2, y2, color);
    }

    public void fill(int pipeline, int x, int y, int x2, int y2, int color) {
        if (pipeline == RenderPipelines.GUI_TEXT_HIGHLIGHT) {
            graphics.flush();
            RenderSystem.enableColorLogicOp();
            RenderSystem.logicOp(com.mojang.blaze3d.platform.GlStateManager.LogicOp.OR_REVERSE);
            graphics.fill(x, y, x2, y2, color);
            graphics.flush();
            RenderSystem.disableColorLogicOp();
        } else fill(x, y, x2, y2, color);
    }

    public void text(Font font, String text, int x, int y, int color) {
        text(font, text, x, y, color, true);
    }

    public void text(Font font, String text, int x, int y, int color, boolean shadow) {
        if (text != null && (color >>> 24) != 0)
            graphics.drawString(font, Component.literal(text), x, y, color, shadow);
    }

    public void text(Font font, Component text, int x, int y, int color) {
        text(font, text, x, y, color, true);
    }

    public void text(Font font, FormattedCharSequence text, int x, int y, int color) {
        text(font, text, x, y, color, true);
    }

    public void text(Font font, Component text, int x, int y, int color, boolean shadow) {
        if ((color >>> 24) != 0) graphics.drawString(font, text, x, y, color, shadow);
    }

    public void text(
            Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        if ((color >>> 24) != 0) graphics.drawString(font, text, x, y, color, shadow);
    }

    public void centeredText(Font font, Component text, int x, int y, int color) {
        if ((color >>> 24) != 0) graphics.drawCenteredString(font, text, x, y, color);
    }

    public void enableScissor(int x, int y, int x2, int y2) {
        graphics.enableScissor(x, y, x2, y2);
    }

    public void disableScissor() {
        graphics.disableScissor();
    }

    public void requestCursor(int type) {
        CursorTypes.request(type);
    }

    public void blit(
            int pipeline,
            ResourceLocation id,
            int x,
            int y,
            float u,
            float v,
            int width,
            int height,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight) {
        graphics.blit(
                id,
                x,
                y,
                width,
                height,
                u,
                v,
                sourceWidth,
                sourceHeight,
                textureWidth,
                textureHeight);
    }

    public void blit(
            int pipeline,
            ResourceLocation id,
            int x,
            int y,
            float u,
            float v,
            int width,
            int height,
            int textureWidth,
            int textureHeight,
            int color) {
        blit(
                pipeline,
                id,
                x,
                y,
                u,
                v,
                width,
                height,
                width,
                height,
                textureWidth,
                textureHeight,
                color);
    }

    public void blit(
            int pipeline,
            ResourceLocation id,
            int x,
            int y,
            float u,
            float v,
            int width,
            int height,
            int sourceWidth,
            int sourceHeight,
            int textureWidth,
            int textureHeight,
            int color) {
        float[] prior = RenderSystem.getShaderColor().clone();
        graphics.flush();
        RenderSystem.setShaderColor(
                (color >> 16 & 255) / 255f,
                (color >> 8 & 255) / 255f,
                (color & 255) / 255f,
                (color >>> 24) / 255f);
        blit(
                pipeline,
                id,
                x,
                y,
                u,
                v,
                width,
                height,
                sourceWidth,
                sourceHeight,
                textureWidth,
                textureHeight);
        graphics.flush();
        RenderSystem.setShaderColor(prior[0], prior[1], prior[2], prior[3]);
    }
}
