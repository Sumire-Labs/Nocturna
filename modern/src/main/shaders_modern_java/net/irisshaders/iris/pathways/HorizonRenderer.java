// Geometry ported from Iris (LGPL-3.0), bff1e69cb6c5519d8745784aa9c8b92984de67e7, PR #2941.
package net.irisshaders.iris.pathways;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;

/** Renders the sky horizon using Iris's inverted octagonal cone. */
public class HorizonRenderer {
    private static final float TOP = 16.0F;
    private static final float BOTTOM = -16.0F;

    private VertexBuffer buffer;
    private int currentRenderDistance;

    public HorizonRenderer() {
        this.currentRenderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
        this.rebuildBuffer();
    }

    private void rebuildBuffer() {
        if (this.buffer != null) {
            this.buffer.close();
        }

        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        buildHorizon(this.currentRenderDistance * 16, builder);

        this.buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        this.buffer.bind();
        this.buffer.upload(builder.end());
        VertexBuffer.unbind();
    }

    // Package visibility lets the geometry be checked against Iris without an OpenGL context.
    static void buildHorizon(int radius, VertexConsumer consumer) {
        if (radius > 256) {
            // Preserve Iris's clamp for packs that modify the vanilla sky.
            radius = 256;
        }

        consumer.vertex(0.0F, BOTTOM, 0.0F).endVertex();

        for (int i = 0; i <= 8; i++) {
            float angle = (float) (-i * Math.PI / 4.0);
            float x = (float) (radius * Math.cos(angle));
            float z = (float) (radius * Math.sin(angle));
            consumer.vertex(x, TOP, z).endVertex();
        }
    }

    public void renderHorizon(Matrix4f modelView, Matrix4f projection, ShaderInstance shader) {
        if (this.currentRenderDistance != Minecraft.getInstance().options.getEffectiveRenderDistance()) {
            this.currentRenderDistance = Minecraft.getInstance().options.getEffectiveRenderDistance();
            this.rebuildBuffer();
        }

        this.buffer.bind();
        this.buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    public void destroy() {
        this.buffer.close();
    }
}
