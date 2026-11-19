package org.embeddedt.embeddium.validation;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexSorting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.GuiGraphics;

import org.embeddedt.embeddium.impl.gui.rso.compat.GuiGraphicsExtractor;
import org.joml.Matrix4f;

/** Pixel readback for managed GUI text buffered underneath an option tooltip. */
final class RsoOverlayValidation {
    private static final int X = 4, Y = 4, WIDTH = 90, HEIGHT = 24;

    static void verify(Minecraft mc) throws Exception {
        var projection = new Matrix4f(RenderSystem.getProjectionMatrix());
        var sorting = RenderSystem.getVertexSorting();
        var color = RenderSystem.getShaderColor().clone();
        var view = RenderSystem.getModelViewStack();
        view.pushPose();
        try {
            view.setIdentity();
            view.translate(0, 0, -11000);
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(new Matrix4f().setOrtho(0,
                    mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
                    0, 1000, 21000), VertexSorting.ORTHOGRAPHIC_Z);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            drawAndVerify(mc);
        } finally {
            view.popPose();
            RenderSystem.applyModelViewMatrix();
            RenderSystem.setProjectionMatrix(projection, sorting);
            RenderSystem.setShaderColor(color[0], color[1], color[2], color[3]);
        }
    }

    @SuppressWarnings("deprecation")
    private static void drawAndVerify(Minecraft mc) throws Exception {
        var target = mc.getMainRenderTarget();
        var graphics = new GuiGraphics(mc, mc.renderBuffers().bufferSource());
        var bridge = new GuiGraphicsExtractor(graphics);
        Runnable base =
                () -> {
                    graphics.fill(X, Y, X + WIDTH, Y + HEIGHT, 0xFF000000);
                    graphics.drawString(mc.font, "MMMMMMMMMMMM", X + 2, Y + 2, 0xFFFF0000, false);
                };
        target.clear(Minecraft.ON_OSX);
        target.bindWrite(true);
        graphics.drawManaged(base);
        try (var mask = Screenshot.takeScreenshot(target)) {
            mask.writeToFile(java.nio.file.Path.of("rso-tooltip-mask.png"));
            target.clear(Minecraft.ON_OSX);
            target.bindWrite(true);
            graphics.drawManaged(
                    () -> {
                        base.run();
                        bridge.overlay(
                                () -> {
                                    graphics.fill(X, Y, X + WIDTH, Y + HEIGHT, 0xE0000000);
                                    graphics.drawString(
                                            mc.font, "TIP", X + 2, Y + 14, 0xFF00FF00, false);
                                });
                    });
            try (var overlay = Screenshot.takeScreenshot(target)) {
                int checked = 0;
                double scale = mc.getWindow().getGuiScale();
                for (int y = (int) (Y * scale); y < (Y + 12) * scale; y++) {
                    for (int x = (int) (X * scale); x < (X + WIDTH) * scale; x++) {
                        int before = mask.getPixelRGBA(x, y);
                        if ((before & 255) > 200 && (before >>> 8 & 255) < 30) {
                            int after = overlay.getPixelRGBA(x, y);
                            RsoValidation.require(
                                    (after & 255) < 60,
                                    "Underlying GUI text rendered above tooltip background");
                            checked++;
                        }
                    }
                }
                RsoValidation.require(
                        checked > 10, "Overlay fixture did not draw the underlying text");
                overlay.writeToFile(java.nio.file.Path.of("rso-tooltip-layer.png"));
                System.out.println(
                        "PASS tooltip foreground layer: "
                                + checked
                                + " underlying glyph pixels occluded");
            }
        }
    }
}
