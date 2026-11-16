package org.embeddedt.embeddium.impl.mixin.features.render.weather;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.server.packs.resources.ResourceProvider;
import org.embeddedt.embeddium.impl.render.weather.WeatherRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public class GameRendererMixin {
    @Inject(method = "reloadShaders", at = @At("RETURN"))
    private void nocturna$reloadWeather(ResourceProvider resources, CallbackInfo ci) { WeatherRenderer.clear(); }
}
