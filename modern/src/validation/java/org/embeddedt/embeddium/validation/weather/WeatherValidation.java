package org.embeddedt.embeddium.validation.weather;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.irisshaders.iris.Iris;
import org.embeddedt.embeddium.api.weather.*;
import org.embeddedt.embeddium.impl.Celeritas;
import org.embeddedt.embeddium.impl.render.weather.WeatherRenderer;
import org.lwjgl.opengl.*;

/** Test-only integration fixtures for rain, provider layers, legacy hooks and state restoration. */
public final class WeatherValidation {
    public static DimensionSpecialEffects overrideEffects;
    public static net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> overrideBiome;
    private static int ticks;
    private static boolean validated;

    public static void tick(Minecraft mc) throws Exception {
        if (mc.level == null || mc.player == null) return;
        mc.level.setRainLevel(1);
        if (++ticks < 60 || validated) return;
        require(WeatherRenderer.lastColumns > 0 && WeatherRenderer.lastDrawCalls > 0, "Weather was not instanced in the real frame");
        System.out.println("PASS real rain frame: " + WeatherRenderer.lastColumns + " columns / " + WeatherRenderer.lastDrawCalls + " draws");
        checkDraws(mc);
        nativeSnowAndDryBiome(mc);
        Iris.getIrisConfig().setShadersEnabled(false); Iris.getIrisConfig().save(); Iris.reload();
        checkDraws(mc);
        Iris.getIrisConfig().setShadersEnabled(true); Iris.getIrisConfig().save(); Iris.reload();
        validated = true;
    }

    private static void nativeSnowAndDryBiome(Minecraft mc) throws Exception {
        var position=mc.gameRenderer.getMainCamera().getPosition();
        var biomes=mc.level.registryAccess().registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        try {
            overrideBiome=biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.SNOWY_PLAINS);
            WeatherRenderer.clear();
            require(WeatherRenderer.render(mc.gameRenderer.lightTexture(),300,.5f,position.x,position.y,position.z),"Native snow was rejected");
            var field=WeatherRenderer.class.getDeclaredField("VANILLA");field.setAccessible(true);var batch=field.get(null);
            var firsts=batch.getClass().getDeclaredField("firsts");firsts.setAccessible(true);
            require(((int[])firsts.get(batch))[1]==0,"Snowy biome emitted rain columns");
            require(WeatherRenderer.lastColumns>0&&WeatherRenderer.lastDrawCalls==1,"Native snow did not render in one draw");
            overrideBiome=biomes.getHolderOrThrow(net.minecraft.world.level.biome.Biomes.DESERT);
            WeatherRenderer.clear();
            WeatherRenderer.render(mc.gameRenderer.lightTexture(),301,.5f,position.x,position.y,position.z);
            require(WeatherRenderer.lastColumns==0&&WeatherRenderer.lastDrawCalls==0,"Dry biome emitted precipitation");
            System.out.println("PASS native snowy/dry biome precipitation via Biome.getPrecipitationAt");
        } finally {overrideBiome=null;WeatherRenderer.clear();}
    }

    private static void checkDraws(Minecraft mc) {
        var provider = new Provider(); overrideEffects = provider;
        var position = mc.gameRenderer.getMainCamera().getPosition();
        int vao = GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING);
        int program = GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM);
        int fbo = GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING);
        try {
            require(WeatherRenderer.render(mc.gameRenderer.lightTexture(), 200, .5f, position.x, position.y, position.z), "Provider path was rejected");
            require(WeatherRenderer.lastColumns == 2 && WeatherRenderer.lastDrawCalls == 2, "Mixed provider did not use two layers");
            require(provider.collections == 1, "Provider columns were not collected once");
            WeatherRenderer.render(mc.gameRenderer.lightTexture(), 201, .5f, position.x, position.y, position.z);
            require(provider.collections == 1, "Unchanged provider geometry was rebuilt");
            provider.revision++;
            WeatherRenderer.render(mc.gameRenderer.lightTexture(), 202, .5f, position.x, position.y, position.z);
            require(provider.collections == 2, "Changed provider geometry was not rebuilt");
            var legacy = new Effects(); overrideEffects = legacy;
            WeatherRenderer.render(mc.gameRenderer.lightTexture(), 203, .5f, position.x, position.y, position.z);
            require(legacy.calls == 1, "Legacy hook was skipped or called twice");
            Celeritas.options().performance.useInstancedWeather = false;
            require(!WeatherRenderer.render(mc.gameRenderer.lightTexture(), 204, .5f, position.x, position.y, position.z), "Disabled path did not fall back");
            require(legacy.calls == 1, "Disabled path consumed the legacy hook");
            require(GL11C.glGetInteger(GL30C.GL_VERTEX_ARRAY_BINDING) == vao, "Leaked VAO");
            require(GL11C.glGetInteger(GL20C.GL_CURRENT_PROGRAM) == program, "Leaked program");
            require(GL11C.glGetInteger(GL30C.GL_FRAMEBUFFER_BINDING) == fbo, "Leaked framebuffer");
            require(GL11C.glGetError() == GL11C.GL_NO_ERROR, "Weather caused an OpenGL error");
            System.out.println("PASS weather provider revision/legacy hook/state, shaders=" + Iris.getIrisConfig().areShadersEnabled());
        } finally {overrideEffects = null;Celeritas.options().performance.useInstancedWeather = true;}
    }

    private static class Effects extends DimensionSpecialEffects {
        int calls;
        Effects() {super(192, true, SkyType.NORMAL, false, false);}
        public Vec3 getBrightnessDependentFogColor(Vec3 value, float brightness) {return value;}
        public boolean isFoggyAt(int x, int y) {return false;}
        public boolean renderSnowAndRain(ClientLevel world, int tick, float delta, LightTexture light, double x, double y, double z) {calls++;return true;}
    }

    private static final class Provider extends Effects implements WeatherColumnProvider {
        int revision, collections;
        public boolean rendersVanillaWeather() {return false;}
        public int beginFrame(ClientLevel world, Entity view, float partialTicks) {return 100;}
        public int columnRevision() {return revision;}
        public float fadeRadius() {return 10;}
        public void collectColumns(WeatherColumnSink sink) {
            collections++;
            var player = Minecraft.getInstance().player;int x = player.getBlockX(),z = player.getBlockZ();float y = (float)player.getY();
            sink.beginLayer(new ResourceLocation("textures/environment/rain.png"),32,0,0,.5f);
            sink.column(x+1,z+2,y-2,y+5,0,0,0,0,0xf000f0,0,3,1,1,1,1);
            sink.beginLayer(new ResourceLocation("textures/environment/snow.png"),512,.01f,.001f,.3f);
            sink.column(x+2,z+3,y-2,y+5,.5f,1,.25f,1,0xf000f0,0,1,1,1,1,1);
        }
    }

    private static void require(boolean value, String message) {if (!value) throw new AssertionError(message);}
}
