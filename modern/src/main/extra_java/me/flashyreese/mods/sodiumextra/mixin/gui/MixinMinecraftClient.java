// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.gui;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.Minecraft.class)
public class MixinMinecraftClient {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void sodiumExtra$tick(CallbackInfo ci) {
        SodiumExtraClientMod.onTick((net.minecraft.client.Minecraft) (Object) this);
    }
}
