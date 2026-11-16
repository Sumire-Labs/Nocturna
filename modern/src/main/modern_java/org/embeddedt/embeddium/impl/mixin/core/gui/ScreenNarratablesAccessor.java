package org.embeddedt.embeddium.impl.mixin.core.gui;

import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.client.gui.screens.Screen;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(Screen.class)
public interface ScreenNarratablesAccessor {
    @Accessor("narratables")
    List<NarratableEntry> nocturna$getNarratables();
}
