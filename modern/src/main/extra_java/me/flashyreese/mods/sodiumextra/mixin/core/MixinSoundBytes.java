package me.flashyreese.mods.sodiumextra.mixin.core;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(com.mojang.blaze3d.audio.SoundBuffer.class)
public class MixinSoundBytes implements org.embeddedt.embeddium.impl.extra.compat.SoundBytes {
    @Unique private int nocturna$bytes;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void nocturna$size(
            java.nio.ByteBuffer bytes, javax.sound.sampled.AudioFormat format, CallbackInfo ci) {
        nocturna$bytes = bytes.remaining();
    }

    public int nocturna$bytes() {
        return nocturna$bytes;
    }
}
