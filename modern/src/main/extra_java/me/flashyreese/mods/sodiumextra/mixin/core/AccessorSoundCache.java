package me.flashyreese.mods.sodiumextra.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.sounds.SoundBufferLibrary.class)
public interface AccessorSoundCache {
    @Accessor("cache")
    java.util.Map<
                    net.minecraft.resources.ResourceLocation,
                    java.util.concurrent.CompletableFuture<com.mojang.blaze3d.audio.SoundBuffer>>
            nocturna$getCache();
}
