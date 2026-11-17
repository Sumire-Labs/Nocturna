// Ported from Angelica (LGPL-3.0), 996fb54cb05e256ea6039f4e2c8457a221a179b7.
package net.irisshaders.iris.gl.program;

import com.mojang.blaze3d.platform.GlStateManager;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Map;

/** Linked programs kept from destroyed pipelines, using Angelica's consuming two-generation pool. */
public final class RetainedPrograms {
    public record Sources(@Nullable String vertex, @Nullable String geometry, @Nullable String tessControl,
                          @Nullable String tessEval, @Nullable String fragment, @Nullable String compute,
                          Map<String, Integer> attributes) {
        public Sources { attributes = Map.copyOf(attributes); }
    }

    /** Native terrain can outlive the pack that created it; a retired configuration must never regain its handles. */
    public record Ownership(Sources sources, long epoch) {}

    private static Map<Sources, ArrayDeque<Integer>> recent = new HashMap<>();
    private static Map<Sources, ArrayDeque<Integer>> older = new HashMap<>();
    private static String configuration;
    private static long epoch;

    private RetainedPrograms() {}

    public static void useConfiguration(@Nullable String settings) {
        if (settings == null || !settings.equals(configuration)) {
            afterPipelineBuilt();
            afterPipelineBuilt();
            epoch++;
        }
        configuration = settings;
    }

    public static Ownership ownership(Map<ShaderType, String> stages, Map<String, Integer> attributes) {
        return new Ownership(new Sources(stages.get(ShaderType.VERTEX), stages.get(ShaderType.GEOM),
            stages.get(ShaderType.TESS_CTRL), stages.get(ShaderType.TESS_EVALUATE),
            stages.get(ShaderType.FRAGMENT), stages.get(ShaderType.COMPUTE), attributes), epoch);
    }

    static void retain(Sources sources, int program) {
        recent.computeIfAbsent(sources, key -> new ArrayDeque<>()).add(program);
    }

    public static void release(@Nullable Ownership owner, int program) {
        if (owner != null && owner.epoch() == epoch) retain(owner.sources(), program);
        else GlStateManager.glDeleteProgram(program);
    }

    public static int take(Ownership owner) {
        return owner.epoch() == epoch ? take(owner.sources()) : 0;
    }

    static int take(Sources sources) {
        final int program = take(recent, sources);
        return program != 0 ? program : take(older, sources);
    }

    private static int take(Map<Sources, ArrayDeque<Integer>> pool, Sources sources) {
        if (pool.isEmpty()) return 0;
        final ArrayDeque<Integer> programs = pool.get(sources);
        if (programs == null) return 0;
        final int program = programs.poll();
        if (programs.isEmpty()) pool.remove(sources);
        return program;
    }

    public static void afterPipelineBuilt() {
        for (ArrayDeque<Integer> programs : older.values()) {
            for (int program : programs) {
                GlStateManager.glDeleteProgram(program);
            }
        }
        older = recent;
        recent = new HashMap<>();
    }
}
