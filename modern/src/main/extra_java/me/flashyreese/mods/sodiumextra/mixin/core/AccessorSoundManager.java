package me.flashyreese.mods.sodiumextra.mixin.core;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(net.minecraft.client.sounds.SoundManager.class)
public interface AccessorSoundManager {
    @Accessor("soundEngine")
    net.minecraft.client.sounds.SoundEngine nocturna$getEngine();
}
