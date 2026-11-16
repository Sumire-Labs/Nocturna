// Sodium Extra 0.9.6-beta.4 (LGPL-3.0), upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.client.gui;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import org.embeddedt.embeddium.impl.extra.compat.DebugEntryCategory;
import org.embeddedt.embeddium.impl.extra.compat.DebugScreenDisplayer;
import org.embeddedt.embeddium.impl.extra.compat.DebugScreenEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SodiumExtraDebugEntryLightUpdates implements DebugScreenEntry {
    @Override
    public void display(
            @NotNull DebugScreenDisplayer displayer,
            @Nullable Level serverOrClientLevel,
            @Nullable LevelChunk clientChunk,
            @Nullable LevelChunk serverChunk) {
        if (!SodiumExtraClientMod.options().renderSettings.lightUpdates) {
            Component text = Component.translatable("sodium-extra.overlay.light_updates");
            displayer.addPriorityLine(text.getString());
        }
    }

    @Override
    public boolean isAllowed(boolean reducedDebugInfo) {
        return true;
    }

    @Override
    public @NotNull DebugEntryCategory category() {
        return SodiumExtraDebugEntryCategory.SODIUM_EXTRA_DEBUG_CATEGORY;
    }
}
