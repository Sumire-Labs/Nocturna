package me.jellysquid.mods.sodium.mixin.features.world.biome;

import me.jellysquid.mods.sodium.client.world.biome.BiomeColorMaps;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = Biome.class, priority = 800)
public abstract class BiomeMixin {
    @Shadow
    public abstract BiomeSpecialEffects getSpecialEffects();

    @Shadow
    @Final
    private Biome.ClimateSettings climateSettings;

    @Unique
    private int defaultColorIndex;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void setupColors(CallbackInfo ci) {
        this.defaultColorIndex = this.getDefaultColorIndex();
    }

    /**
     * @author JellySquid
     * @reason Avoid unnecessary allocations
     */
    @Overwrite
    public int getGrassColor(double x, double z) {
        // Forge redirects vanilla field reads to its modifiable biome info before our mixin is applied.
        // Calling the vanilla getter preserves that hook for biome modifiers and resource packs.
        var effects = this.getSpecialEffects();
        var override = effects.getGrassColorOverride().orElse(null);
        int color = override != null ? override.intValue() : BiomeColorMaps.getGrassColor(this.defaultColorIndex);

        var modifier = effects.getGrassColorModifier();

        if (modifier != BiomeSpecialEffects.GrassColorModifier.NONE) {
            color = modifier.modifyColor(x, z, color);
        }

        return color;
    }

    /**
     * @author JellySquid
     * @reason Avoid allocations
     */
    @Overwrite
    public int getFoliageColor() {
        var override = this.getSpecialEffects().getFoliageColorOverride().orElse(null);
        return override != null ? override.intValue() : BiomeColorMaps.getFoliageColor(this.defaultColorIndex);
    }

    @Unique
    private int getDefaultColorIndex() {
        double temperature = Mth.clamp(this.climateSettings.temperature(), 0.0F, 1.0F);
        double humidity = Mth.clamp(this.climateSettings.downfall(), 0.0F, 1.0F);

        return BiomeColorMaps.getIndex(temperature, humidity);
    }
}
