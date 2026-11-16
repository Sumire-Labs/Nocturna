package org.embeddedt.embeddium.validation.mixin;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import org.embeddedt.embeddium.validation.weather.WeatherValidation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(ClientLevel.class)
public class WeatherEffectsOverrideMixin {
    @Inject(method="effects",at=@At("HEAD"),cancellable=true)
    private void validation$effects(CallbackInfoReturnable<DimensionSpecialEffects> ci) {
        if(WeatherValidation.overrideEffects!=null)ci.setReturnValue(WeatherValidation.overrideEffects);
    }
}
