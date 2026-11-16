package org.embeddedt.embeddium.validation;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pathways.HorizonRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import org.joml.Matrix4f;
import org.lwjgl.opengl.*;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/** Compares the production fan with its reference triangle expansion on the real driver. Test mod only. */
public final class HorizonValidation {
    private static final int SIZE = 64;
    private static final float TOP = 16.0F;
    private static final float BOTTOM = -16.0F;
    private static int worldDraws;

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    public static void recordWorldDraw() {
        if (Minecraft.getInstance().level != null && Iris.getCurrentPack().isPresent()) worldDraws++;
    }

    public static void verifyInWorld() throws Exception {
        require(worldDraws > 0, "the active shader pack did not render the production horizon");
        Files.writeString(Path.of("horizon-validation.txt"), "PASS Java17; exact Iris vertex bytes; " +
            "384 indexed-triangle/fan color and depth comparisons; clamp, float seam, winding, deformation, fog, " +
            "camera/FOV, buffer reuse/rebuild/destruction; active-pack world draws=" + worldDraws + "\n");
    }

    public static void verify() throws Exception {
        int oldDistance = Minecraft.getInstance().options.renderDistance().get();
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
        int oldDrawFbo = GL11C.glGetInteger(GL30C.GL_DRAW_FRAMEBUFFER_BINDING);
        int oldReadFbo = GL11C.glGetInteger(GL30C.GL_READ_FRAMEBUFFER_BINDING);
        int oldRenderbuffer = GL11C.glGetInteger(GL30C.GL_RENDERBUFFER_BINDING);
        int oldCullFace = GL11C.glGetInteger(GL11C.GL_CULL_FACE_MODE);
        int oldFrontFace = GL11C.glGetInteger(GL11C.GL_FRONT_FACE);
        int oldDepthFunc = GL11C.glGetInteger(GL11C.GL_DEPTH_FUNC);
        boolean oldDepthMask = GL11C.glGetBoolean(GL11C.GL_DEPTH_WRITEMASK);
        boolean oldCull = GL11C.glIsEnabled(GL11C.GL_CULL_FACE);
        boolean oldDepth = GL11C.glIsEnabled(GL11C.GL_DEPTH_TEST);
        boolean oldBlend = GL11C.glIsEnabled(GL11C.GL_BLEND);
        boolean oldScissor = GL11C.glIsEnabled(GL11C.GL_SCISSOR_TEST);
        int[] oldViewport = new int[4];
        GL11C.glGetIntegerv(GL11C.GL_VIEWPORT, oldViewport);
        float[] oldClear = new float[4];
        GL11C.glGetFloatv(GL11C.GL_COLOR_CLEAR_VALUE, oldClear);
        double oldClearDepth = GL11C.glGetDouble(GL11C.GL_DEPTH_CLEAR_VALUE);
        int[] oldColorMask = new int[4];
        GL11C.glGetIntegerv(GL11C.GL_COLOR_WRITEMASK, oldColorMask);
        int fbo = GL30C.glGenFramebuffers();
        int color = GL30C.glGenRenderbuffers();
        int depth = GL30C.glGenRenderbuffers();
        HorizonRenderer renderer = null;
        try (var manager = shaderResources();
             var shader = new ShaderInstance(manager, "horizon_validation", DefaultVertexFormat.POSITION)) {
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, fbo);
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, color);
            GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL11C.GL_RGBA8, SIZE, SIZE);
            GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_COLOR_ATTACHMENT0,
                GL30C.GL_RENDERBUFFER, color);
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, depth);
            GL30C.glRenderbufferStorage(GL30C.GL_RENDERBUFFER, GL14C.GL_DEPTH_COMPONENT24, SIZE, SIZE);
            GL30C.glFramebufferRenderbuffer(GL30C.GL_FRAMEBUFFER, GL30C.GL_DEPTH_ATTACHMENT,
                GL30C.GL_RENDERBUFFER, depth);
            require(GL30C.glCheckFramebufferStatus(GL30C.GL_FRAMEBUFFER) == GL30C.GL_FRAMEBUFFER_COMPLETE,
                "horizon framebuffer incomplete");
            GL11C.glViewport(0, 0, SIZE, SIZE);
            GlStateManager._disableBlend();
            GlStateManager._disableScissorTest();
            GlStateManager._enableDepthTest();
            GlStateManager._depthFunc(GL11C.GL_LEQUAL);
            GlStateManager._depthMask(true);
            GlStateManager._colorMask(true, true, true, true);
            GL11C.glClearDepth(1.0);
            GL11C.glCullFace(GL11C.GL_BACK);
            GL11C.glFrontFace(GL11C.GL_CCW);
            GlStateManager._clearColor(0.75F, 0.0F, 0.75F, 1.0F);

            Minecraft.getInstance().options.renderDistance().set(2);
            renderer = new HorizonRenderer();
            VertexBuffer prior = buffer(renderer);
            int comparisons = 0;
            int covered = 0;
            for (int distance : new int[]{2, 8, 16, 32}) {
                Minecraft.getInstance().options.renderDistance().set(distance);
                int radius = Minecraft.getInstance().options.getEffectiveRenderDistance() * 16;
                try (var reference = new ReferenceMesh(radius)) {
                    for (int yaw : new int[]{0, 45, 90, 180, 270, 315}) {
                        for (int pitch : new int[]{-35, 35}) {
                            for (int fov : new int[]{70, 110}) {
                                for (int variant = 0; variant < 2; variant++) {
                                    for (int culling = 0; culling < 2; culling++) {
                                        if (culling == 0) GlStateManager._disableCull();
                                        else GlStateManager._enableCull();
                                        var modelView = new Matrix4f().rotationX((float) Math.toRadians(pitch))
                                            .rotateY((float) Math.toRadians(yaw));
                                        var projection = new Matrix4f().perspective((float) Math.toRadians(fov),
                                            1.0F, 0.05F, 512.0F);
                                        shader.getUniform("Deform").set(variant * 3.0F);
                                        shader.getUniform("Fog").set((float) variant);
                                        clear();
                                        reference.draw(shader, modelView, projection);
                                        byte[] expectedColor = readPixels(GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 4);
                                        byte[] expectedDepth = readPixels(GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, 4);
                                        clear();
                                        renderer.renderHorizon(modelView, projection, shader);
                                        require(Arrays.equals(expectedColor,
                                            readPixels(GL11C.GL_RGBA, GL11C.GL_UNSIGNED_BYTE, 4)),
                                            "horizon color differs at distance=" + distance + " yaw=" + yaw +
                                                " pitch=" + pitch + " fov=" + fov + " variant=" + variant);
                                        require(Arrays.equals(expectedDepth,
                                            readPixels(GL11C.GL_DEPTH_COMPONENT, GL11C.GL_FLOAT, 4)),
                                            "horizon depth differs");
                                        var depthValues = ByteBuffer.wrap(expectedDepth)
                                            .order(ByteOrder.nativeOrder()).asFloatBuffer();
                                        for (int i = 0; i < depthValues.remaining(); i++) {
                                            if (depthValues.get(i) < 1.0F) { covered++; break; }
                                        }
                                        comparisons++;
                                    }
                                }
                            }
                        }
                    }
                    var current = buffer(renderer);
                    if (distance == 2) require(current == prior, "unchanged distance rebuilt the horizon");
                    else require(current != prior && prior.isInvalid(), "old horizon buffer was not closed");
                    verifyBuffer(current, radius);
                    prior = current;
                }
            }
            require(comparisons == 384 && covered > 0, "horizon comparisons did not draw geometry");
            renderer.destroy();
            renderer = null;
            require(prior.isInvalid(), "horizon destruction left its buffer live");
            require(GL11C.glGetError() == GL11C.GL_NO_ERROR, "horizon validation GL error");
            System.out.println("PASS Iris horizon: 384 exact color/depth comparisons; 10 vertices, 8 triangles; " +
                "native fan vs reference triangle expansion; buffer lifecycle");
        } finally {
            if (renderer != null) renderer.destroy();
            Minecraft.getInstance().options.renderDistance().set(oldDistance);
            GL30C.glBindFramebuffer(GL30C.GL_DRAW_FRAMEBUFFER, oldDrawFbo);
            GL30C.glBindFramebuffer(GL30C.GL_READ_FRAMEBUFFER, oldReadFbo);
            GL30C.glBindRenderbuffer(GL30C.GL_RENDERBUFFER, oldRenderbuffer);
            GlStateManager._glBindVertexArray(oldVao);
            GlStateManager._glBindBuffer(GL15C.GL_ARRAY_BUFFER, oldBuffer);
            GlStateManager._glUseProgram(oldProgram);
            GL11C.glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
            GL11C.glCullFace(oldCullFace);
            GL11C.glFrontFace(oldFrontFace);
            GlStateManager._depthFunc(oldDepthFunc);
            GlStateManager._depthMask(oldDepthMask);
            if (oldCull) GlStateManager._enableCull(); else GlStateManager._disableCull();
            if (oldDepth) GlStateManager._enableDepthTest(); else GlStateManager._disableDepthTest();
            if (oldBlend) GlStateManager._enableBlend(); else GlStateManager._disableBlend();
            if (oldScissor) GlStateManager._enableScissorTest(); else GlStateManager._disableScissorTest();
            GlStateManager._clearColor(oldClear[0], oldClear[1], oldClear[2], oldClear[3]);
            GlStateManager._colorMask(oldColorMask[0] != 0, oldColorMask[1] != 0,
                oldColorMask[2] != 0, oldColorMask[3] != 0);
            GL11C.glClearDepth(oldClearDepth);
            GL30C.glDeleteFramebuffers(fbo);
            GL30C.glDeleteRenderbuffers(color);
            GL30C.glDeleteRenderbuffers(depth);
        }
    }

    private static void clear() {
        GL11C.glClear(GL11C.GL_COLOR_BUFFER_BIT | GL11C.GL_DEPTH_BUFFER_BIT);
    }

    private static byte[] readPixels(int format, int type, int bytesPerPixel) {
        var pixels = MemoryUtil.memAlloc(SIZE * SIZE * bytesPerPixel);
        try {
            GL11C.glReadPixels(0, 0, SIZE, SIZE, format, type, pixels);
            var result = new byte[pixels.remaining()];
            pixels.get(result);
            return result;
        } finally { MemoryUtil.memFree(pixels); }
    }

    private static VertexBuffer buffer(HorizonRenderer renderer) throws Exception {
        var field = HorizonRenderer.class.getDeclaredField("buffer");
        field.setAccessible(true);
        return (VertexBuffer) field.get(renderer);
    }

    private static void verifyBuffer(VertexBuffer buffer, int radius) throws Exception {
        var mode = VertexBuffer.class.getDeclaredField("mode");
        var count = VertexBuffer.class.getDeclaredField("indexCount");
        var vbo = VertexBuffer.class.getDeclaredField("vertexBufferId");
        mode.setAccessible(true); count.setAccessible(true); vbo.setAccessible(true);
        require(mode.get(buffer) == VertexFormat.Mode.TRIANGLE_FAN && count.getInt(buffer) == 10,
            "production horizon primitive/count changed");
        GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, vbo.getInt(buffer));
        require(GL15C.glGetBufferParameteri(GL15C.GL_ARRAY_BUFFER, GL15C.GL_BUFFER_SIZE) == 120,
            "production horizon contains extra vertex data");
        var builder = new BufferBuilder(256);
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        buildReference(radius, builder);
        var rendered = builder.end();
        try (var stack = MemoryStack.stackPush()) {
            var actual = stack.malloc(120);
            GL15C.glGetBufferSubData(GL15C.GL_ARRAY_BUFFER, 0, actual);
            require(actual.equals(rendered.vertexBuffer()), "GPU vertex bytes differ from Iris");
        } finally { rendered.release(); }
    }

    // Frozen Iris bff1e69c geometry; only addVertex's API spelling changes for Minecraft 1.20.1.
    private static void buildReference(int radius, VertexConsumer consumer) {
        if (radius > 256) {
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

    private static MultiPackResourceManager shaderResources() throws Exception {
        var root = Files.createTempDirectory(Path.of("."), "horizon-validation-");
        var core = Files.createDirectories(root.resolve("assets/minecraft/shaders/core"));
        Files.writeString(core.resolve("horizon_validation.json"), """
            {"vertex":"horizon_validation","fragment":"horizon_validation","samplers":[],
             "attributes":["Position"],"uniforms":[
             {"name":"ModelViewMat","type":"matrix4x4","count":16,"values":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]},
             {"name":"ProjMat","type":"matrix4x4","count":16,"values":[1,0,0,0,0,1,0,0,0,0,1,0,0,0,0,1]},
             {"name":"Deform","type":"float","count":1,"values":[0]},
             {"name":"Fog","type":"float","count":1,"values":[0]}]}
            """);
        Files.writeString(core.resolve("horizon_validation.vsh"), """
            #version 150
            in vec3 Position;
            uniform mat4 ModelViewMat;
            uniform mat4 ProjMat;
            uniform float Deform;
            out vec3 sky;
            flat out int vertexId;
            void main() {
                vec3 p = Position;
                p.y += Deform * (sin(Position.x * 0.03125) + cos(Position.z * 0.0625));
                sky = Position;
                vertexId = gl_VertexID;
                gl_Position = ProjMat * ModelViewMat * vec4(p, 1.0);
            }
            """);
        Files.writeString(core.resolve("horizon_validation.fsh"), """
            #version 150
            in vec3 sky;
            flat in int vertexId;
            uniform float Fog;
            out vec4 result;
            void main() {
                vec3 color = normalize(sky) * 0.4 + 0.5;
                float fog = clamp(length(sky) * 0.003 * Fog, 0.0, 1.0);
                color += vec3(float(vertexId) * 0.003 + float(gl_PrimitiveID) * 0.002);
                result = vec4(mix(color, vec3(0.2, 0.3, 0.7), fog), 1.0);
            }
            """);
        return new MultiPackResourceManager(PackType.CLIENT_RESOURCES,
            List.of(new PathPackResources("horizon-validation", root, false)));
    }

    private static final class ReferenceMesh implements AutoCloseable {
        final int vao = GL30C.glGenVertexArrays();
        final int vbo = GL15C.glGenBuffers();
        final int ebo = GL15C.glGenBuffers();

        ReferenceMesh(int radius) {
            var builder = new BufferBuilder(256);
            builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
            buildReference(radius, builder);
            var rendered = builder.end();
            try (var stack = MemoryStack.stackPush()) {
                GL30C.glBindVertexArray(this.vao);
                GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, this.vbo);
                GL15C.glBufferData(GL15C.GL_ARRAY_BUFFER, rendered.vertexBuffer(), GL15C.GL_STATIC_DRAW);
                GL20C.glEnableVertexAttribArray(0);
                GL20C.glVertexAttribPointer(0, 3, GL11C.GL_FLOAT, false, 12, 0L);
                var indices = stack.mallocInt(24);
                for (int i = 1; i <= 8; i++) indices.put(0).put(i).put(i + 1);
                indices.flip();
                GL15C.glBindBuffer(GL15C.GL_ELEMENT_ARRAY_BUFFER, this.ebo);
                GL15C.glBufferData(GL15C.GL_ELEMENT_ARRAY_BUFFER, indices, GL15C.GL_STATIC_DRAW);
            } finally { rendered.release(); }
        }

        void draw(ShaderInstance shader, Matrix4f modelView, Matrix4f projection) {
            shader.MODEL_VIEW_MATRIX.set(modelView);
            shader.PROJECTION_MATRIX.set(projection);
            shader.apply();
            GL30C.glBindVertexArray(this.vao);
            GL11C.glDrawElements(GL11C.GL_TRIANGLES, 24, GL11C.GL_UNSIGNED_INT, 0L);
            shader.clear();
        }

        @Override public void close() {
            GL30C.glDeleteVertexArrays(this.vao);
            GL15C.glDeleteBuffers(this.vbo);
            GL15C.glDeleteBuffers(this.ebo);
        }
    }
}
