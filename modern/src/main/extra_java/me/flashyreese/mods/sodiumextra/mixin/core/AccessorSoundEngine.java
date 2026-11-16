package me.flashyreese.mods.sodiumextra.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.sounds.SoundEngine.class)
public interface AccessorSoundEngine {
    @Accessor("soundBuffers")
    net.minecraft.client.sounds.SoundBufferLibrary nocturna$getBuffers();
}
