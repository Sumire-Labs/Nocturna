// Forge 1.20.1 hooks for Sodium Extra 0.9.6-beta.4 (LGPL-3.0).
package me.flashyreese.mods.sodiumextra.mixin.gui;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(net.minecraft.client.gui.Gui.class)
public class MixinGui {
    @Inject(method = "render", at = @At("TAIL"))
    private void sodiumExtra$hud(
            net.minecraft.client.gui.GuiGraphics gui, float delta, CallbackInfo ci) {
        SodiumExtraClientMod.onHudRender(gui, delta);
        org.embeddedt.embeddium.impl.extra.compat.ExtraDebugOptionsScreen.renderOverlays(gui);
    }
}
