package org.embeddedt.embeddium.impl.mixin.features.render.weather;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import org.embeddedt.embeddium.impl.render.weather.WeatherRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LevelRenderer.class)
public class LevelRendererMixin {
    @Shadow private int ticks;

    @Inject(method = "renderSnowAndRain", at = @At("HEAD"), cancellable = true)
    private void nocturna$weather(LightTexture light, float delta, double x, double y, double z, CallbackInfo ci) {
        if (WeatherRenderer.render(light, this.ticks, delta, x, y, z)) ci.cancel();
    }

    @Inject(method = "setLevel", at = @At("HEAD"))
    private void nocturna$clearWeather(ClientLevel level, CallbackInfo ci) { WeatherRenderer.clear(); }
}
