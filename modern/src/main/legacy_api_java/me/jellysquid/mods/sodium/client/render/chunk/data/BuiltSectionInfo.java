package me.jellysquid.mods.sodium.client.render.chunk.data;

/** Compatibility type for the published ChunkDataBuiltEvent constructor. */
public final class BuiltSectionInfo {
    public static class Builder implements org.embeddedt.embeddium.api.render.chunk.SectionInfoBuilder {
        private final org.embeddedt.embeddium.api.render.chunk.SectionInfoBuilder delegate;
        public Builder(org.embeddedt.embeddium.api.render.chunk.SectionInfoBuilder delegate) {
            this.delegate = java.util.Objects.requireNonNull(delegate);
        }
        public void addSprite(net.minecraft.client.renderer.texture.TextureAtlasSprite sprite) { delegate.addSprite(sprite); }
        public void addBlockEntity(net.minecraft.world.level.block.entity.BlockEntity entity, boolean cull) { delegate.addBlockEntity(entity,cull); }
        public void removeBlockEntitiesIf(java.util.function.Predicate<net.minecraft.world.level.block.entity.BlockEntity> filter) { delegate.removeBlockEntitiesIf(filter); }
    }
}
