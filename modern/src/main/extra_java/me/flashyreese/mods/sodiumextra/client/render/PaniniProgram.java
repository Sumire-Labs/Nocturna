package me.flashyreese.mods.sodiumextra.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;

import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.util.*;

/** Executes the original resource graph and depth shader. Hand integration is a separate pass. */
final class PaniniProgram implements AutoCloseable {
    private final ExtraPostChain chain;
    private EffectGpuProgram depth, merge;

    PaniniProgram() throws IOException {
        ExtraPostChain c = null;
        try {
            c = new ExtraPostChain(new ResourceLocation("sodium-extra", "panini"));
            if (!c.hasConfiguration())
                throw new IOException("Panini post chain has no PaniniConfig block");
        } catch (IOException | RuntimeException failure) {
            if (c != null) c.close();
            throw failure;
        }
        chain = c;
    }

    void prepareDepth() throws IOException {
        if (depth != null) return;
        EffectGpuProgram d = null, m = null;
        try {
            String vertex =
                    EffectShaderSource.stage(
                            new ResourceLocation("minecraft", "core/screenquad"), ".vsh");
            d =
                    new EffectGpuProgram(
                            vertex,
                            EffectShaderSource.stage(
                                    new ResourceLocation("sodium-extra", "post/panini_depth"),
                                    ".fsh"));
            m =
                    new EffectGpuProgram(
                            vertex,
                            """
                            #version 330
                            uniform sampler2D WorldSampler;uniform sampler2D HandSampler;
                            void main(){ivec2 texel=ivec2(gl_FragCoord.xy);gl_FragDepth=min(texelFetch(WorldSampler,texel,0).r,texelFetch(HandSampler,texel,0).r);}
                            """);
        } catch (IOException | RuntimeException failure) {
            if (d != null) d.close();
            if (m != null) m.close();
            throw failure;
        }
        depth = d;
        merge = m;
    }

    void draw(
            RenderTarget source,
            RenderTarget output,
            float strength,
            float horizontal,
            float vertical,
            boolean mask,
            boolean writeDepth,
            int handDepth) {
        if (writeDepth) {
            try {
                prepareDepth();
            } catch (IOException failure) {
                throw new IllegalStateException(failure);
            }
            if (handDepth >= 0) {
                merge.draw(
                        output,
                        Map.of(
                                "World",
                                new EffectGpuProgram.Image(
                                        source.getDepthTextureId(),
                                        source.width,
                                        source.height,
                                        false),
                                "Hand",
                                new EffectGpuProgram.Image(
                                        handDepth, output.width, output.height, false)),
                        true);
            } else {
                depth.vector(
                        "PaniniConfig", "Params", strength, horizontal, vertical, mask ? 1 : 0);
                depth.draw(
                        output,
                        Map.of(
                                "Depth",
                                new EffectGpuProgram.Image(
                                        source.getDepthTextureId(),
                                        source.width,
                                        source.height,
                                        false)),
                        true);
            }
        } else {
            chain.parameters(strength, horizontal, vertical, mask);
            chain.execute(source, output);
        }
    }

    @Override
    public void close() {
        chain.close();
        if (depth != null) depth.close();
        if (merge != null) merge.close();
    }
}
