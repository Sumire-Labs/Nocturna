package org.embeddedt.embeddium.validation;

import com.google.common.collect.ImmutableSet;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.cache.ProgramBinaryCache;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.gl.program.ProgramUniforms;
import net.irisshaders.iris.gl.program.RetainedPrograms;
import net.irisshaders.iris.gl.state.FogMode;
import net.irisshaders.iris.gl.state.ValueUpdateNotifier;
import net.irisshaders.iris.gl.uniform.DynamicUniformHolder;
import net.irisshaders.iris.mixin.GlStateManagerAccessor;
import net.irisshaders.iris.mixin.statelisteners.BooleanStateAccessor;
import net.irisshaders.iris.mixin.texture.TextureAtlasAccessor;
import net.irisshaders.iris.uniforms.CommonUniforms;
import net.irisshaders.iris.uniforms.custom.CustomUniformFixedInputUniformsHolder;
import net.irisshaders.iris.uniforms.custom.CustomUniforms;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.joml.*;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;

import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

import static net.irisshaders.iris.gl.uniform.UniformUpdateFrequency.ONCE;

/** Real GL values, shared supplier ownership and binding transitions; never shipped in the product. */
public final class UniformSupplierValidation {
    private static final String VERTEX = "#version 330\nin vec2 Position;void main(){gl_Position=vec4(Position,0,1);}";
    private static final String FRAGMENT = """
        #version 330
        uniform ivec2 a,b; uniform ivec4 signedValue; uniform uvec4 bits;
        uniform vec4 tint; uniform vec3 truncated; out vec4 result;
        void main(){
            float total=float(a.x+a.y+b.x+b.y+signedValue.x+signedValue.y+signedValue.z+signedValue.w)
                +float(bits.x+bits.y+bits.z+bits.w)+dot(tint,vec4(1))+dot(truncated,vec3(1));
            result=vec4(fract(total*.001),.5,.75,1);
        }
        """;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static <T> T view(Class<T> type, Object vector) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (proxy, method, args) -> method.invoke(vector, args)));
    }

    public static void verify() throws Exception {
        boolean cache = ProgramBinaryCache.isEnabled();
        int previous = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        ProgramBinaryCache.setEnabled(false);
        RetainedPrograms.useConfiguration("uniform-supplier-validation");
        try {
            commonSuppliers();
            programTransitions();
            customExpressions();
            require(GL11C.glGetError() == GL11C.GL_NO_ERROR, "supplier validation GL error");
            Files.writeString(Path.of("uniform-supplier-validation.txt"),
                "PASS shared atlas/texture/blend supplier identity and values; mutable/read-only vectors; " +
                "owned snapshots; all components; signed/unsigned; cold/reused programs; bound/deferred listeners; custom expressions\n");
            System.out.println("PASS uniform suppliers: shared objects, texture/blend values, all components, cold/reused programs and deferred notifications");
        } finally {
            Program.unbind();
            RetainedPrograms.useConfiguration(null);
            ProgramBinaryCache.setEnabled(cache);
            GlStateManager._glUseProgram(previous);
        }
    }

    private static void commonSuppliers() {
        Map<String, Supplier<?>> supplied = new HashMap<>();
        var holder = (DynamicUniformHolder) Proxy.newProxyInstance(DynamicUniformHolder.class.getClassLoader(),
            new Class<?>[]{DynamicUniformHolder.class}, (proxy, method, args) -> {
                if (args != null && args[0] instanceof String name) {
                    for (Object arg : args) if (arg instanceof Supplier<?> supplier) supplied.put(name, supplier);
                }
                return proxy;
            });
        CommonUniforms.addDynamicUniforms(holder, FogMode.OFF);
        int oldShaderTexture = RenderSystem.getShaderTexture(0);
        int oldActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        GlStateManager._activeTexture(GL13C.GL_TEXTURE0);
        int oldBound = GL11C.glGetInteger(GL11C.GL_TEXTURE_BINDING_2D);
        var blend = GlStateManagerAccessor.getBLEND();
        boolean oldBlend = ((BooleanStateAccessor) blend.mode).isEnabled();
        int srcRgb = blend.srcRgb, dstRgb = blend.dstRgb, srcAlpha = blend.srcAlpha, dstAlpha = blend.dstAlpha;
        int[] textures = {GlStateManager._genTexture(), GlStateManager._genTexture()};
        try {
            for (int i = 0; i < textures.length; i++) {
                GlStateManager._bindTexture(textures[i]);
                GL11C.glTexImage2D(GL11C.GL_TEXTURE_2D, 0, GL11C.GL_RGBA8, 8 << i, 16 << i,
                    0, GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, (ByteBuffer) null);
            }
            var atlas = (TextureAtlas) Minecraft.getInstance().getTextureManager().getTexture(TextureAtlas.LOCATION_BLOCKS);
            var dimensions = (TextureAtlasAccessor) atlas;
            RenderSystem.setShaderTexture(0, atlas.getId());
            Object shared = supplied.get("atlasSize").get();
            require(new Vector2i(dimensions.callGetWidth(), dimensions.callGetHeight()).equals(shared), "atlas dimensions changed");
            Vector2i copy = new Vector2i((Vector2ic) shared);
            for (int i = 0; i < textures.length; i++) {
                RenderSystem.setShaderTexture(0, textures[i]);
                GlStateManager._bindTexture(textures[i]);
                require(supplied.get("gtextureSize").get() == shared, "texture supplier allocated another vector");
                require(new Vector2i(8 << i, 16 << i).equals(shared), "texture dimensions changed");
                require(supplied.get("atlasSize").get() == shared && new Vector2i().equals(shared), "non-atlas fallback was not reset");
            }
            require(copy.equals(new Vector2i(dimensions.callGetWidth(), dimensions.callGetHeight())), "copied dimensions aliased shared scratch");
            Object blendScratch = null;
            for (int i = 0; i < 3; i++) {
                if (i == 1) GlStateManager._disableBlend(); else GlStateManager._enableBlend();
                GlStateManager._blendFuncSeparate(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ONE, GL11C.GL_ZERO);
                Object current = supplied.get("blendFunc").get();
                if (blendScratch != null) require(current == blendScratch, "blend supplier allocated another vector");
                Vector4i expected = i == 1 ? new Vector4i(0) : new Vector4i(GL11C.GL_SRC_ALPHA, GL11C.GL_ONE_MINUS_SRC_ALPHA, GL11C.GL_ONE, GL11C.GL_ZERO);
                require(expected.equals(current), "blend factors changed at " + i + ": " + current + " != " + expected
                    + "; enabled=" + ((BooleanStateAccessor) blend.mode).isEnabled());
                blendScratch = current;
            }
        } finally {
            RenderSystem.setShaderTexture(0, oldShaderTexture);
            GlStateManager._bindTexture(oldBound);
            GlStateManager._activeTexture(oldActive);
            GlStateManager._blendFuncSeparate(srcRgb, dstRgb, srcAlpha, dstAlpha);
            if (oldBlend) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
            for (int texture : textures) GlStateManager._deleteTexture(texture);
        }
    }

    private static final class Notifier implements ValueUpdateNotifier {
        Runnable listener;
        public void setListener(Runnable listener) { this.listener = listener; }
        void fire() { if (listener != null) listener.run(); }
    }

    private record Inputs(Vector2i shared, Vector4i integers, Vector4f floats, int[] phase) {
        Inputs() { this(new Vector2i(), new Vector4i(), new Vector4f(), new int[]{1}); }
        Vector2ic pair(boolean second) { return shared.set(phase[0] * (second ? 5 : 2), phase[0] * (second ? 7 : 3)); }
        Vector4ic signed() { return integers.set(phase[0], -phase[0], phase[0] * 2, -phase[0] * 2); }
        Vector4fc color() { return floats.set(phase[0] * .125f, phase[0] * .25f, phase[0] * .5f, phase[0] * .75f); }
    }

    private static Program build(String suffix, Inputs inputs, Notifier notifier) {
        var builder = ProgramBuilder.begin("uniform-supplier" + suffix, VERTEX, null, FRAGMENT + suffix, ImmutableSet.of());
        // The two integer suppliers deliberately return the same mutable object with different contents.
        Supplier<Vector2i> legacy = () -> (Vector2i) inputs.pair(false);
        builder.uniform2i("a", legacy, notifier);
        builder.uniform2i("b", () -> view(Vector2ic.class, inputs.pair(true)), ValueUpdateNotifier.NONE);
        builder.uniform4i("signedValue", () -> view(Vector4ic.class, inputs.signed()), ValueUpdateNotifier.NONE);
        builder.uniform4f("tint", () -> view(Vector4fc.class, inputs.color()), ValueUpdateNotifier.NONE);
        builder.uniformTruncated3f(ONCE, "truncated", () -> view(Vector4fc.class, inputs.color()));
        builder.uniform4ui(ONCE, "bits", () -> view(Vector4ic.class, new Vector4i(inputs.phase[0], 0, -1, Integer.MIN_VALUE)));
        return builder.build();
    }

    private static void programTransitions() {
        Inputs inputs = new Inputs();
        int retained = 0;
        for (int phase : new int[]{1, 0}) {
            inputs.phase[0] = phase;
            var notifier = new Notifier();
            var reused = build("\n// reusable", inputs, notifier);
            var reference = build("\n// independent " + phase, inputs, new Notifier());
            try {
                if (retained != 0) require(reused.getProgramId() == retained, "linked program was not reused");
                reused.bind(); check(reused, phase);
                reference.bind(); check(reference, phase);
                reused.bind(); check(reused, phase);
                // A raw program switch must defer the previous owner's notifier upload.
                GlStateManager._glUseProgram(reference.getProgramId());
                inputs.phase[0] = 11; notifier.fire();
                require(ProgramUniforms.activeHasDeferredUploads(), "wrong-program notification was not deferred");
                ints(reference, "a", new int[]{phase * 2, phase * 3}, false);
                reused.bind();
                ints(reused, "a", new int[]{22, 33}, false);
                // Mutating the shared supplier alone cannot change an already uploaded uniform.
                inputs.shared.zero();
                ints(reused, "a", new int[]{22, 33}, false);
                inputs.phase[0] = phase;
                notifier.fire();
                ints(reused, "a", new int[]{phase * 2, phase * 3}, false);
                retained = reused.getProgramId();
            } finally { Program.unbind(); reused.delete(); reference.delete(); }
        }
    }

    private static void check(Program program, int phase) {
        ints(program, "a", new int[]{phase * 2, phase * 3}, false);
        ints(program, "b", new int[]{phase * 5, phase * 7}, false);
        ints(program, "signedValue", new int[]{phase, -phase, phase * 2, -phase * 2}, false);
        ints(program, "bits", new int[]{phase, 0, -1, Integer.MIN_VALUE}, true);
        floats(program, "tint", new float[]{phase * .125f, phase * .25f, phase * .5f, phase * .75f});
        floats(program, "truncated", new float[]{phase * .125f, phase * .25f, phase * .5f});
    }

    private static void customExpressions() {
        var inputVector = new Vector4f(2, 3, 5, 7);
        var inputPair = new Vector2i(11, 13);
        int previousId = 0;
        for (int phase : new int[]{1, 0}) {
            inputVector.set(2 * phase, 3 * phase, 5 * phase, 7 * phase);
            inputPair.set(11 * phase, 13 * phase);
            var inputs = new CustomUniformFixedInputUniformsHolder.Builder();
            inputs.uniform4f(ONCE, "sourceVector", () -> view(Vector4fc.class, inputVector));
            inputs.uniform2i(ONCE, "pair", () -> view(Vector2ic.class, inputPair));
            var expressions = new CustomUniforms.Builder();
            expressions.addVariable("vec4", "derived", "sourceVector", true);
            var custom = expressions.build(inputs.build());
            var builder = ProgramBuilder.begin("supplier-expression", VERTEX, null,
                "#version 330\nuniform vec4 derived;uniform ivec2 pair;out vec4 result;void main(){result=derived+vec4(pair,0,0)*.001;}",
                ImmutableSet.of());
            custom.assignTo(builder);
            var program = builder.build();
            try {
                if (previousId != 0) require(program.getProgramId() == previousId, "custom expression program not reused");
                custom.mapholderToPass(builder, program);
                custom.update();
                // A subsequent mutation must not alter the cached expression until update is called again.
                inputVector.zero(); inputPair.zero();
                program.bind(); custom.push(program);
                floats(program, "derived", new float[]{2 * phase, 3 * phase, 5 * phase, 7 * phase});
                ints(program, "pair", new int[]{11 * phase, 13 * phase}, false);
                custom.update(); custom.push(program);
                floats(program, "derived", new float[]{0, 0, 0, 0});
                ints(program, "pair", new int[]{0, 0}, false);
                previousId = program.getProgramId();
            } finally { Program.unbind(); program.delete(); }
        }
    }

    private static void ints(Program program, String name, int[] expected, boolean unsigned) {
        try (var stack = MemoryStack.stackPush()) {
            int location = GL20C.glGetUniformLocation(program.getProgramId(), name);
            require(location >= 0, "inactive integer fixture " + name);
            var values = stack.mallocInt(4);
            if (unsigned) GL30C.glGetUniformuiv(program.getProgramId(), location, values);
            else GL20C.glGetUniformiv(program.getProgramId(), location, values);
            for (int i = 0; i < expected.length; i++) require(values.get(i) == expected[i], name + " component " + i + ": " + values.get(i) + " != " + expected[i]);
        }
    }

    private static void floats(Program program, String name, float[] expected) {
        try (var stack = MemoryStack.stackPush()) {
            int location = GL20C.glGetUniformLocation(program.getProgramId(), name);
            require(location >= 0, "inactive float fixture " + name);
            var values = stack.mallocFloat(4);
            GL20C.glGetUniformfv(program.getProgramId(), location, values);
            for (int i = 0; i < expected.length; i++) require(values.get(i) == expected[i], name + " component " + i + ": " + values.get(i) + " != " + expected[i]);
        }
    }
}
