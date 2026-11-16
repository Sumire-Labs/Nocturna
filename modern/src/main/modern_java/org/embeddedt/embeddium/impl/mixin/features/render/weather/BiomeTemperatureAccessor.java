package org.embeddedt.embeddium.impl.mixin.features.render.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(Biome.class)
public interface BiomeTemperatureAccessor {
    @Invoker("getTemperature")
    float nocturna$getTemperature(BlockPos position);
}
