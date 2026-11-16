package org.embeddedt.embeddium.impl.render.weather;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.embeddedt.embeddium.api.weather.WeatherColumnProvider;
import org.embeddedt.embeddium.impl.Celeritas;
import net.irisshaders.iris.Iris;
import net.irisshaders.iris.pipeline.WorldRenderingPhase;
import org.lwjgl.opengl.*;

/** Angelica's cached rain/snow and provider batching, with native Forge world/state adapters. */
public final class WeatherRenderer {
    private static final ResourceLocation RAIN_TEXTURE = new ResourceLocation("textures/environment/rain.png");
    private static final ResourceLocation SNOW_TEXTURE = new ResourceLocation("textures/environment/snow.png");
    private static final int REBUILD_TICKS = 20;
    private static final WeatherColumns columns = new WeatherColumns();
    private static final WeatherBatch VANILLA = new WeatherBatch(), PROVIDER = new WeatherBatch();
    private static NativeWeatherWorld worldView;
    private static int builtRevision = Integer.MIN_VALUE, builtX = Integer.MIN_VALUE, builtY = Integer.MIN_VALUE,
            builtZ = Integer.MIN_VALUE, builtRadius = -1, builtUpdateCount = Integer.MIN_VALUE, builtCameraY = Integer.MIN_VALUE;
    private static int builtProviderRevision = Integer.MIN_VALUE, builtProviderX = Integer.MIN_VALUE,
            builtProviderZ = Integer.MIN_VALUE;
    private static Object builtProviderDimension;
    public static int lastColumns, lastDrawCalls, uploads;

    private WeatherRenderer() { }

    public static boolean render(LightTexture light, int tick, float partialTicks, double cameraX, double cameraY, double cameraZ) {
        var mc = Minecraft.getInstance(); var world = mc.level;
        lastColumns = lastDrawCalls = 0;
        if (world == null || !Celeritas.options().performance.useInstancedWeather) return false;
        var view = mc.getCameraEntity(); if (view == null) return false;
        WeatherShader shader = WeatherShader.select();
        if (shader == null) return false; // The original/Forge weather path remains responsible for unsupported draws.
        WeatherColumnProvider provider = world.effects() instanceof WeatherColumnProvider p ? p : null;
        if (provider == null && world.effects().renderSnowAndRain(world, tick, partialTicks, light, cameraX, cameraY, cameraZ)) return true;
        float strength = provider == null || provider.rendersVanillaWeather() ? world.getRainLevel(partialTicks) : 0;
        int radius = Celeritas.options().quality.weatherQuality.isFancy(Celeritas.areGraphicsFancy()) ? 10 : 5;
        boolean vanillaActive = strength > 0 && radius > 0;
        lastColumns = lastDrawCalls = 0;
        if (provider == null && !vanillaActive) return true;
        int x = Mth.floor(view.getX()), y = Mth.floor(view.getY()), z = Mth.floor(view.getZ());
        int cameraBlockY = Mth.floor(cameraY);
        if (worldView == null || !worldView.matches(world, cameraBlockY)) worldView = new NativeWeatherWorld(world, cameraBlockY);
        var pipeline = Iris.getPipelineManager().getPipelineNullable();
        int oldVao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int oldBuffer = GL11C.glGetInteger(GL15C.GL_ARRAY_BUFFER_BINDING);
        int oldProgram = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int oldFbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        int oldActive = GL11C.glGetInteger(GL13C.GL_ACTIVE_TEXTURE);
        boolean oldTessellation = net.irisshaders.iris.vertices.ImmediateState.usingTessellation;
        int oldPatchVertices = shader.drawMode() == GL40C.GL_PATCHES ? GL11C.glGetInteger(GL40C.GL_PATCH_VERTICES) : 0;
        try {
            if (pipeline != null) pipeline.setPhase(WorldRenderingPhase.RAIN_SNOW);
            light.turnOnLightLayer(); RenderSystem.disableCull(); RenderSystem.enableBlend(); RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc(); RenderSystem.setShaderColor(1, 1, 1, 1);
            RenderSystem.depthMask(Minecraft.useShaderTransparency() || pipeline != null && pipeline.shouldWriteRainAndSnowToDepthBuffer());
            if (vanillaActive) {
                columns.update(worldView, x, z, radius, tick);
                if (Boolean.getBoolean("nocturna.weatherRebuildAlways") || tick - builtUpdateCount >= REBUILD_TICKS
                        || columns.revision() != builtRevision || x != builtX || y != builtY || z != builtZ
                        || cameraBlockY != builtCameraY || radius != builtRadius) {
                    VANILLA.begin(x, z, WeatherInstancedAttribs.SOURCE_VANILLA);
                    VANILLA.beginLayer(RAIN_TEXTURE, 32, 0, 0, .5f);
                    writeColumns(x, y, z, cameraBlockY, radius, true);
                    VANILLA.beginLayer(SNOW_TEXTURE, 512, .01f, .001f, .3f);
                    writeColumns(x, y, z, cameraBlockY, radius, false);
                    VANILLA.upload(); uploads++;
                    builtRevision = columns.revision(); builtX = x; builtY = y; builtZ = z;
                    builtRadius = radius; builtUpdateCount = tick; builtCameraY = cameraBlockY;
                }
            }
            int providerTick = 0; float providerAge = 0, providerInvRadius = 0;
            if (provider != null) {
                providerTick = provider.beginFrame(world, view, partialTicks);
                int revision = provider.columnRevision(); float fadeRadius = provider.fadeRadius();
                if (!Float.isFinite(fadeRadius) || fadeRadius <= 0) throw new IllegalArgumentException("Weather fade radius must be finite and positive");
                if (Boolean.getBoolean("nocturna.weatherRebuildAlways") || revision != builtProviderRevision
                        || world.dimension() != builtProviderDimension || x != builtProviderX || z != builtProviderZ) {
                    PROVIDER.begin(x, z, WeatherInstancedAttribs.SOURCE_PROVIDER); provider.collectColumns(PROVIDER); PROVIDER.upload();
                    builtProviderRevision = revision; builtProviderDimension = world.dimension(); builtProviderX = x; builtProviderZ = z;
                }
                providerAge = providerTick + partialTicks; providerInvRadius = 1 / fadeRadius;
            }
            if (vanillaActive && VANILLA.count > 0 || provider != null && PROVIDER.count > 0) {
                WeatherParams.set((float)(x - cameraX), (float)-cameraY, (float)(z - cameraZ), 1f / radius,
                        (float)(view.getX() - x), (float)(view.getZ() - z), partialTicks, tick + partialTicks,
                        tick, strength, providerTick, providerAge, providerInvRadius);
                GL30C.glBindVertexArray(WeatherQuadMesh.vao());
                if (oldPatchVertices != 0) GL40C.glPatchParameteri(GL40C.GL_PATCH_VERTICES, 4);
                if (vanillaActive) { lastDrawCalls += VANILLA.draw(mc, shader); lastColumns += VANILLA.count; }
                if (provider != null) { lastDrawCalls += PROVIDER.draw(mc, shader); lastColumns += PROVIDER.count; }
            }
        } finally {
            shader.unbind();
            GL30C.glBindVertexArray(oldVao); GL15C.glBindBuffer(GL15C.GL_ARRAY_BUFFER, oldBuffer);
            GL30C.glBindFramebuffer(GL30C.GL_FRAMEBUFFER, oldFbo); GlStateManager._glUseProgram(oldProgram);
            GlStateManager._activeTexture(oldActive);
            if (oldPatchVertices != 0) GL40C.glPatchParameteri(GL40C.GL_PATCH_VERTICES, oldPatchVertices);
            net.irisshaders.iris.vertices.ImmediateState.usingTessellation = oldTessellation;
            RenderSystem.enableCull(); RenderSystem.disableBlend(); light.turnOffLightLayer();
            if (pipeline != null) pipeline.setPhase(WorldRenderingPhase.NONE);
        }
        return true;
    }

    private static void writeColumns(int x, int y, int z, int cameraY, int radius, boolean wantRain) {
        for (int dz = z - radius; dz <= z + radius; dz++) for (int dx = x - radius; dx <= x + radius; dx++) {
            int slot = columns.slotAt(dx, dz); if (!columns.precipitatesAtSlot(slot)) continue;
            int height = columns.heightAtSlot(slot), bottom = Math.max(y - radius, height), top = Math.max(y + radius, height);
            if (bottom == top) continue;
            int precipitation = columns.precipitationAtSlot(slot, bottom);
            boolean rain = precipitation == 1; if (precipitation == 0 || rain != wantRain) continue;
            int packed = columns.lightAtSlot(worldView, slot, dx, dz, Math.max(height, cameraY));
            float j0 = columns.jitterAtSlot(slot, 0);
            VANILLA.put(dx, dz, bottom, top, rain ? 0 : j0, rain ? 0 : columns.jitterAtSlot(slot, 1),
                    rain ? 0 : columns.jitterAtSlot(slot, 2), rain ? 0 : columns.jitterAtSlot(slot, 3),
                    rain ? packed : (packed * 3 + 15728880) / 4, rain ? columns.hashAtSlot(slot) : 0,
                    rain ? 3 + j0 : 1, 1, 1, 1, 1);
        }
    }

    public static void clear() {
        VANILLA.release(); PROVIDER.release(); WeatherQuadMesh.delete(); WeatherShader.clear(); worldView = null;
        builtRevision = builtX = builtY = builtZ = builtUpdateCount = builtCameraY = Integer.MIN_VALUE; builtRadius = -1;
        builtProviderRevision = builtProviderX = builtProviderZ = Integer.MIN_VALUE; builtProviderDimension = null;
    }

    public static void releaseShaderPack() { WeatherShader.releasePack(); }
}
