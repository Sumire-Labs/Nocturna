// Sodium Extra 0.9.6-beta.4 (LGPL-3.0), upstream 7abb4236d5d6310633084cd2f613b63fdd1ccb5c.
package me.flashyreese.mods.sodiumextra.client.gui;

import me.flashyreese.mods.sodiumextra.client.FrameCounter;

import net.minecraft.network.chat.Component;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;

import org.embeddedt.embeddium.impl.extra.compat.DebugEntryCategory;
import org.embeddedt.embeddium.impl.extra.compat.DebugScreenDisplayer;
import org.embeddedt.embeddium.impl.extra.compat.DebugScreenEntry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SodiumExtraDebugEntryFps implements DebugScreenEntry {
    private final FrameCounter stats = FrameCounter.getInstance();

    private final boolean extended;

    public SodiumExtraDebugEntryFps(boolean extended) {
        this.extended = extended;
    }

    @Override
    public void display(
            @NotNull DebugScreenDisplayer displayer,
            @Nullable Level serverOrClientLevel,
            @Nullable LevelChunk clientChunk,
            @Nullable LevelChunk serverChunk) {
        int currentFPS = FrameCounter.getInstance().getSmoothFps();
        Component text = Component.translatable("sodium-extra.overlay.fps", currentFPS);

        if (this.extended)
            text =
                    Component.literal(
                            String.format(
                                    "%s %s",
                                    text.getString(),
                                    Component.translatable(
                                                    "sodium-extra.overlay.fps_extended",
                                                    this.stats.getAverageFps(),
                                                    this.stats.getOnePercentLowFps(),
                                                    this.stats.getPointOnePercentLowFps())
                                            .getString()));
        displayer.addLine(text.getString());
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
