package org.embeddedt.embeddium.validation.mixin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.*;
import org.embeddedt.embeddium.validation.weather.WeatherValidation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(BiomeManager.class)
public class WeatherBiomeOverrideMixin {
    @Inject(method="getBiome",at=@At("HEAD"),cancellable=true)
    private void validation$biome(BlockPos position,CallbackInfoReturnable<Holder<Biome>> ci) {
        if(WeatherValidation.overrideBiome!=null && com.mojang.blaze3d.systems.RenderSystem.isOnRenderThread())
            ci.setReturnValue(WeatherValidation.overrideBiome);
    }
}
