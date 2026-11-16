package org.embeddedt.embeddium.impl.extra.compat;

import com.mojang.blaze3d.shaders.Program;

import org.lwjgl.opengl.GL20C;

import java.util.regex.*;

/** Adapts the host fog import ABI without collapsing the two distance channels. */
public final class VanillaFogBridge {
    public static final ThreadLocal<Program.Type> STAGE = new ThreadLocal<>();

    public static String transform(String source) {
        var stage = STAGE.get();
        if (source == null
                || stage == null
                || !source.contains("linear_fog")
                || !source.contains("fog_distance")) return source;
        if (stage == Program.Type.VERTEX) {
            String declarations = "\nout vec2 nocturna_Dist;\nuniform mat3 nocturna_InverseView;\n";
            Matcher m =
                    Pattern.compile(
                                    "float\\s+fog_distance\\s*\\(\\s*mat4\\s+(\\w+)\\s*,\\s*vec3\\s+(\\w+)\\s*,\\s*int\\s+\\w+\\s*\\)\\s*\\{")
                            .matcher(source);
            if (!m.find()) throw new IllegalStateException("Host fog_distance ABI changed");
            String body =
                    "\nvec3 nocturnaEye = ("
                            + m.group(1)
                            + " * vec4("
                            + m.group(2)
                            + ", 1.0)).xyz;\n"
                            + "vec3 nocturnaWorld = nocturna_InverseView * nocturnaEye;\n"
                            + "nocturna_Dist = vec2(length(nocturnaEye),"
                            + " max(length(nocturnaWorld.xz), abs(nocturnaWorld.y)));\n";
            return declarations + source.substring(0, m.end()) + body + source.substring(m.end());
        }
        String declarations =
                """
                in vec2 nocturna_Dist;
                uniform int nocturna_FogEnabled;
                uniform vec4 nocturna_FogRanges;
                float nocturnaFogFraction(float d, vec2 r) {
                    if (d <= r.x) return 0.0;
                    if (d >= r.y) return 1.0;
                    return (d-r.x)/(r.y-r.x);
                }
                """;
        Matcher m =
                Pattern.compile(
                                "vec4\\s+linear_fog\\s*\\(\\s*vec4\\s+(\\w+)\\s*,\\s*float\\s+\\w+\\s*,\\s*float\\s+\\w+\\s*,\\s*float\\s+\\w+\\s*,\\s*vec4\\s+(\\w+)\\s*\\)\\s*\\{")
                        .matcher(source);
        if (!m.find()) throw new IllegalStateException("Host linear_fog ABI changed");
        String body =
                "\n"
                    + "if(nocturna_FogEnabled != 0) { float"
                    + " amount=max(nocturnaFogFraction(nocturna_Dist.x,nocturna_FogRanges.xy),nocturnaFogFraction(nocturna_Dist.y,nocturna_FogRanges.zw));return"
                    + " vec4(mix("
                        + m.group(1)
                        + ".rgb,"
                        + m.group(2)
                        + ".rgb, amount*"
                        + m.group(2)
                        + ".a),"
                        + m.group(1)
                        + ".a);}\n";
        return declarations + source.substring(0, m.end()) + body + source.substring(m.end());
    }

    public static void upload(int enable, int ranges, int inverse) {
        boolean active =
                ExtraFog.hasTerrain
                        && ExtraFog.activeMode
                                == net.minecraft.client.renderer.FogRenderer.FogMode.FOG_TERRAIN
                        && !ExtraFog.cloudPass;
        GL20C.glUniform1i(enable, active ? 1 : 0);
        if (active) {
            var f = ExtraFog.terrain;
            GL20C.glUniform4f(
                    ranges,
                    f.environmentalStart,
                    f.environmentalEnd,
                    me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper
                            .decodeRenderDistanceStart(f.renderDistanceStart, f.renderDistanceEnd),
                    me.flashyreese.mods.sodiumextra.client.fog.FogDistanceHelper
                            .decodeRenderDistanceEnd(f.renderDistanceStart, f.renderDistanceEnd));
        }
        if (inverse >= 0)
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var matrix = com.mojang.blaze3d.systems.RenderSystem.getInverseViewRotationMatrix();
                GL20C.glUniformMatrix3fv(inverse, false, matrix.get(stack.mallocFloat(9)));
            }
    }
}
