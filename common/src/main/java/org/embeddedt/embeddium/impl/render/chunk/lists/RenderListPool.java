package org.embeddedt.embeddium.impl.render.chunk.lists;

import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;

import java.util.Arrays;

/**
 * Ported from Angelica's RenderListPool (LGPL-3.0), commit
 * d876a203da9b46ee5b5048171b8e5e4deb9bb967, by Jason Mitchell and contributors.
 * Two slots are sufficient only while each manager rejects a second in-flight search.
 * Each terrain/shadow manager owns its own pool; the currently published lists are never reset by its next search.
 */
final class RenderListPool {
    private final ChunkRenderList[][] slots = {new ChunkRenderList[0], new ChunkRenderList[0]};
    private int generation;

    public ChunkRenderList[] beginSearch(int regionIdsLength) {
        final int s = this.generation++ & 1;
        ChunkRenderList[] slot = this.slots[s];
        if (slot.length < regionIdsLength) {
            slot = this.slots[s] = Arrays.copyOf(slot, regionIdsLength);
        }
        return slot;
    }

    public static ChunkRenderList acquire(ChunkRenderList[] slot, RenderRegion region) {
        final int id = region.getId();
        final ChunkRenderList list = slot[id];
        if (list != null && list.getRegion() == region) {
            list.reset();
            return list;
        }
        return slot[id] = new ChunkRenderList(region);
    }
}
