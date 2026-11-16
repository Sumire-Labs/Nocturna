package org.embeddedt.embeddium.impl.gui.rso.compat;

import java.util.Collection;
import java.util.List;

/** A composite narration entry, flattened by RsoScreen for Minecraft 1.20.1. */
public interface NarratableEntry extends net.minecraft.client.gui.narration.NarratableEntry {
    default Collection<? extends NarratableEntry> getNarratables() {
        return List.of(this);
    }
}
