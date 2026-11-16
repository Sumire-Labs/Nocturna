package org.embeddedt.embeddium.extravalidation;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;

import net.minecraft.client.Minecraft;

import org.lwjgl.opengl.GL33C;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.lang.reflect.Method;

/** Independent CPU projection oracle against real driver color, mask and nearest-depth output. */
final class PaniniPixels {
    static void verify() throws Exception {
        int oldRead = GL33C.glGetInteger(GL33C.GL_READ_FRAMEBUFFER_BINDING);
        int oldDraw = GL33C.glGetInteger(GL33C.GL_DRAW_FRAMEBUFFER_BINDING);
        RenderTarget source = new TextureTarget(96, 72, true, Minecraft.ON_OSX);
        RenderTarget output = new TextureTarget(64, 48, true, Minecraft.ON_OSX);
        Object program = null;
        var colors = MemoryUtil.memAlloc(source.width * source.height * 4);
        var depths = MemoryUtil.memAllocFloat(source.width * source.height);
        try {
            source.setFilterMode(GL33C.GL_LINEAR);
            for (int y = 0; y < source.height; y++)
                for (int x = 0; x < source.width; x++) {
                    colors.put((byte) Math.round(255 * (x + 0.5) / source.width))
                            .put((byte) Math.round(255 * (y + 0.5) / source.height))
                            .put((byte) 32)
                            .put((byte) 255);
                    depths.put(
                            0.01F
                                    + 0.98F
                                            * (x + y * source.width)
                                            / (source.width * source.height));
                }
            colors.flip();
            depths.flip();
            GlStateManager._bindTexture(source.getColorTextureId());
            GL33C.glTexSubImage2D(
                    GL33C.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    source.width,
                    source.height,
                    GL33C.GL_RGBA,
                    GL33C.GL_UNSIGNED_BYTE,
                    colors);
            GlStateManager._bindTexture(source.getDepthTextureId());
            GL33C.glTexSubImage2D(
                    GL33C.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    source.width,
                    source.height,
                    GL33C.GL_DEPTH_COMPONENT,
                    GL33C.GL_FLOAT,
                    depths);
            GlStateManager._bindTexture(0);
            Class<?> type =
                    Class.forName("me.flashyreese.mods.sodiumextra.client.render.PaniniProgram");
            var constructor = type.getDeclaredConstructor();
            constructor.setAccessible(true);
            program = constructor.newInstance();
            Method draw =
                    type.getDeclaredMethod(
                            "draw",
                            RenderTarget.class,
                            RenderTarget.class,
                            float.class,
                            float.class,
                            float.class,
                            boolean.class,
                            boolean.class,
                            int.class);
            draw.setAccessible(true);
            while (GL33C.glGetError() != GL33C.GL_NO_ERROR) {}
            for (float strength : new float[] {0, 0.25F, 1}) {
                draw.invoke(program, source, output, strength, 1.2F, 0.9F, false, false, -1);
                draw.invoke(program, source, output, strength, 1.2F, 0.9F, false, true, -1);
                GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, output.frameBufferId);
                try (MemoryStack stack = MemoryStack.stackPush()) {
                    var pixel = stack.malloc(4);
                    var depth = stack.mallocFloat(1);
                    for (int y = 6; y < 42; y += 7)
                        for (int x = 6; x < 58; x += 9) {
                            double[] uv =
                                    uv(
                                            (x + 0.5) / output.width,
                                            (y + 0.5) / output.height,
                                            strength,
                                            1.2,
                                            0.9);
                            GL33C.glReadPixels(
                                    x, y, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
                            double red =
                                    255
                                            * Math.max(
                                                    0.5 / source.width,
                                                    Math.min(1 - 0.5 / source.width, uv[0]));
                            double green =
                                    255
                                            * Math.max(
                                                    0.5 / source.height,
                                                    Math.min(1 - 0.5 / source.height, uv[1]));
                            ExtraValidation.require(
                                    Math.abs(Byte.toUnsignedInt(pixel.get(0)) - red) <= 2,
                                    "Panini red differs from original projection at "
                                            + x
                                            + ","
                                            + y);
                            ExtraValidation.require(
                                    Math.abs(Byte.toUnsignedInt(pixel.get(1)) - green) <= 2,
                                    "Panini green differs from original projection");
                            ExtraValidation.require(
                                    Byte.toUnsignedInt(pixel.get(3)) == 255,
                                    "Panini changed world coverage");
                            GL33C.glReadPixels(
                                    x, y, 1, 1, GL33C.GL_DEPTH_COMPONENT, GL33C.GL_FLOAT, depth);
                            int tx =
                                    Math.max(
                                            0,
                                            Math.min(
                                                    source.width - 1,
                                                    (int) Math.floor(uv[0] * source.width)));
                            int ty =
                                    Math.max(
                                            0,
                                            Math.min(
                                                    source.height - 1,
                                                    (int) Math.floor(uv[1] * source.height)));
                            float expected = depths.get(tx + ty * source.width);
                            ExtraValidation.require(
                                    Math.abs(depth.get(0) - expected) < 0.000003,
                                    "Panini depth selected/interpolated the wrong surface");
                        }
                }
            }
            colors.clear();
            for (int i = 0; i < source.width * source.height; i++)
                colors.put((byte) 128).put((byte) 64).put((byte) 32).put((byte) 128);
            colors.flip();
            GlStateManager._bindTexture(source.getColorTextureId());
            GL33C.glTexSubImage2D(
                    GL33C.GL_TEXTURE_2D,
                    0,
                    0,
                    0,
                    source.width,
                    source.height,
                    GL33C.GL_RGBA,
                    GL33C.GL_UNSIGNED_BYTE,
                    colors);
            GlStateManager._bindTexture(0);
            draw.invoke(program, source, output, 0.25F, 1.2F, 0.9F, true, false, -1);
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, output.frameBufferId);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                var pixel = stack.malloc(4);
                GL33C.glReadPixels(32, 24, 1, 1, GL33C.GL_RGBA, GL33C.GL_UNSIGNED_BYTE, pixel);
                ExtraValidation.require(
                        Byte.toUnsignedInt(pixel.get(0)) == 255
                                && Math.abs(Byte.toUnsignedInt(pixel.get(1)) - 128) <= 1
                                && Math.abs(Byte.toUnsignedInt(pixel.get(2)) - 64) <= 1,
                        "outline mask RGB was not restored before Sobel");
                ExtraValidation.require(
                        Byte.toUnsignedInt(pixel.get(3)) == 128, "outline mask coverage changed");
            }
            ExtraValidation.require(
                    GL33C.glGetError() == GL33C.GL_NO_ERROR, "OpenGL error in projection oracle");
            System.out.println("PASS Panini GPU color/depth/mask oracle at strengths 0/25/100");
        } finally {
            if (program instanceof AutoCloseable closeable) closeable.close();
            MemoryUtil.memFree(colors);
            MemoryUtil.memFree(depths);
            source.destroyBuffers();
            output.destroyBuffers();
            GL33C.glBindFramebuffer(GL33C.GL_READ_FRAMEBUFFER, oldRead);
            GL33C.glBindFramebuffer(GL33C.GL_DRAW_FRAMEBUFFER, oldDraw);
            Minecraft.getInstance().getMainRenderTarget().bindWrite(true);
        }
    }

    private static double[] uv(double u, double v, double d, double horizontal, double vertical) {
        // Inverse Panini projection solved independently in scalar arithmetic.
        double edgeCos = 1 / Math.sqrt(1 + horizontal * horizontal);
        double fit = (d + 1) / (d + edgeCos) * edgeCos;
        double px = (2 * u - 1) * horizontal * fit, py = (2 * v - 1) * vertical * fit;
        double normalized = px / (d + 1), square = normalized * normalized;
        double cosine =
                (-square * d + Math.sqrt(Math.max(0, 1 + square * (1 - d * d)))) / (square + 1);
        double scale = (d + 1) / (d + cosine);
        double divisor = Math.max(scale * cosine, 0.0001);
        return new double[] {
            (px / (divisor * horizontal)) * 0.5 + 0.5, (py / (divisor * vertical)) * 0.5 + 0.5
        };
    }
}
