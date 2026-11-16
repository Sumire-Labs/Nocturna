package org.embeddedt.embeddium.impl.render.weather;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.pipeline.programs.GbufferProgram;
import net.irisshaders.iris.pipeline.programs.VanillaDrawState;
import net.irisshaders.iris.pipeline.foss_transform.WeatherVertices;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.embeddedt.embeddium.impl.gl.shader.GlShader;
import org.lwjgl.opengl.*;


final class WeatherShader {
    private static int vanilla;
    private static boolean vanillaUnavailable;
    private static final WeatherShader VANILLA = new WeatherShader(null);
    private static WeatherShader packShader;
    private final float[] matrix = new float[16];
    private final GbufferProgram pack;
    private final VanillaDrawState state = new VanillaDrawState();

    private WeatherShader(GbufferProgram pack) { this.pack = pack; }

    static WeatherShader select() {
        if (!(GL.getCapabilities().OpenGL33)) return null;
        var pipeline = Iris.getPipelineManager().getPipelineNullable();
        if (pipeline instanceof IrisRenderingPipeline iris) {
            var program = iris.getInstancedWeather();
            if (program == null) return null;
            if (packShader == null || packShader.pack != program) packShader = new WeatherShader(program);
            return packShader;
        }
        if (vanillaUnavailable) return null;
        if (vanilla == 0) {
            try { vanilla = createVanilla(); }
            catch (RuntimeException e) {
                vanillaUnavailable = true;
                org.embeddedt.embeddium.impl.Celeritas.logger().warn("Instanced weather unavailable for this particle shader; using ordinary weather", e);
                return null;
            }
        }
        packShader = null;
        return VANILLA;
    }

    void bind(ResourceLocation texture) {
        RenderSystem.setShaderTexture(0, texture);
        int id;
        if (this.pack != null) {
            this.state.modelView.set(RenderSystem.getModelViewMatrix());
            this.state.projection.set(RenderSystem.getProjectionMatrix());
            this.state.textureMatrix.set(RenderSystem.getTextureMatrix());
            this.state.colorModulator.set(1);
            float[] fog = RenderSystem.getShaderFogColor(); this.state.fogColor.set(fog[0], fog[1], fog[2]);
            this.state.fogStart = RenderSystem.getShaderFogStart(); this.state.fogEnd = RenderSystem.getShaderFogEnd();
            this.state.albedoTexture = Minecraft.getInstance().getTextureManager().getTexture(texture).getId();
            this.state.lightmapTexture = RenderSystem.getShaderTexture(2);
            this.pack.bind(this.state);
            id = this.pack.getProgramId();
        } else {
            id = vanilla; GlStateManager._glUseProgram(id);
            GL20C.glUniformMatrix4fv(GL20C.glGetUniformLocation(id, "ModelViewMat"), false,
                    RenderSystem.getModelViewMatrix().get(this.matrix));
            GL20C.glUniformMatrix4fv(GL20C.glGetUniformLocation(id, "ProjMat"), false,
                    RenderSystem.getProjectionMatrix().get(this.matrix));
            GL20C.glUniform1i(GL20C.glGetUniformLocation(id, "Sampler0"), 0);
            GL20C.glUniform1i(GL20C.glGetUniformLocation(id, "Sampler2"), 2);
            GL20C.glUniform1i(GL20C.glGetUniformLocation(id, "FogShape"), RenderSystem.getShaderFogShape().getIndex());
            GL20C.glUniform4f(GL20C.glGetUniformLocation(id, "ColorModulator"), 1, 1, 1, 1);
            GL13C.glActiveTexture(GL13C.GL_TEXTURE0);
            GlStateManager._bindTexture(Minecraft.getInstance().getTextureManager().getTexture(texture).getId());
            GL20C.glUniform1f(GL20C.glGetUniformLocation(id, "FogStart"), RenderSystem.getShaderFogStart());
            GL20C.glUniform1f(GL20C.glGetUniformLocation(id, "FogEnd"), RenderSystem.getShaderFogEnd());
            float[] fog = RenderSystem.getShaderFogColor();
            GL20C.glUniform4f(GL20C.glGetUniformLocation(id, "FogColor"), fog[0], fog[1], fog[2], fog[3]);
        }
        GL20C.glUniform4f(GL20C.glGetUniformLocation(id, "iris_WeatherParams0"), WeatherParams.translateX,
                WeatherParams.translateY, WeatherParams.translateZ, WeatherParams.invRadius);
        GL20C.glUniform4f(GL20C.glGetUniformLocation(id, "iris_WeatherParams1"), WeatherParams.cameraFracX,
                WeatherParams.cameraFracZ, WeatherParams.partialTicks, WeatherParams.age);
        GL20C.glUniform4f(GL20C.glGetUniformLocation(id, "iris_WeatherParams2"), WeatherParams.vanillaTick,
                WeatherParams.providerTick, WeatherParams.rainStrength, WeatherParams.providerAge);
        GL20C.glUniform4f(GL20C.glGetUniformLocation(id, "iris_WeatherParams3"), WeatherParams.providerInvRadius, 0, 0, 0);
    }

    void unbind() { if (this.pack != null) this.pack.unbind(); }
    int drawMode() { return this.pack != null && this.pack.usesTessellation() ? GL40C.GL_PATCHES : GL11C.GL_TRIANGLE_FAN; }

    static void releasePack() { packShader = null; }
    static void clear() { releasePack(); vanillaUnavailable = false; if (vanilla != 0) {GlStateManager.glDeleteProgram(vanilla);vanilla = 0;} }

    private static int createVanilla() {
        String vertex = "#version 150\n" + WeatherVertices.declarations()
                + "vec3 iris_weatherPosition;vec4 iris_weatherColor;vec2 iris_weatherUV;ivec2 iris_weatherLight;"
                + "uniform mat4 ModelViewMat;uniform mat4 ProjMat;uniform int FogShape;uniform sampler2D Sampler2;"
                + "out vec2 texCoord0;out vec4 vertexColor;out float vertexDistance;"
                + WeatherVertices.generator()
                + "void main(){iris_generateWeatherVertex();vec4 eye=ModelViewMat*vec4(iris_weatherPosition,1);"
                + "gl_Position=ProjMat*eye;texCoord0=iris_weatherUV;"
                + "vertexColor=iris_weatherColor*texelFetch(Sampler2,iris_weatherLight/16,0);"
                + "vertexDistance=FogShape==0?length(eye.xyz):max(length((ModelViewMat*vec4(iris_weatherPosition.x,0,iris_weatherPosition.z,1)).xyz),"
                + "length((ModelViewMat*vec4(0,iris_weatherPosition.y,0,1)).xyz));}";
        // Reuse the runtime vanilla/resource-pack fragment; do not redistribute or approximate Minecraft's fog shader.
        int fragment = net.minecraft.client.renderer.GameRenderer.getParticleShader().getFragmentProgram().getId();
        GlShader shader = new GlShader(ShaderType.VERTEX, "nocturna:weather", vertex);
        try {
            int program = GlStateManager.glCreateProgram();
            try {
                GL20C.glAttachShader(program, shader.handle()); GL20C.glAttachShader(program, fragment);
                WeatherVertices.ATTRIBUTES.forEach((name, index) -> GL20C.glBindAttribLocation(program, index, name));
                GL20C.glLinkProgram(program);
                if (GL20C.glGetProgrami(program, GL20C.GL_LINK_STATUS) == 0) throw new IllegalStateException(GL20C.glGetProgramInfoLog(program));
                GL20C.glDetachShader(program, shader.handle()); GL20C.glDetachShader(program, fragment);
                return program;
            } catch (RuntimeException e) { GlStateManager.glDeleteProgram(program); throw e; }
        } finally { shader.delete(); }
    }
}
