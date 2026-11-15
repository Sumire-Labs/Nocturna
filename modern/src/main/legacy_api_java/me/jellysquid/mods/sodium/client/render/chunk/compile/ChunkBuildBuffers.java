package me.jellysquid.mods.sodium.client.render.chunk.compile;
/** The mesh-appender ABI retains this carrier; vertexConsumerProvider is the portable writing API. */
public final class ChunkBuildBuffers {
    private final org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers delegate;
    public ChunkBuildBuffers(org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers delegate) { this.delegate=delegate; }
    public org.embeddedt.embeddium.impl.render.chunk.compile.ChunkBuildBuffers unwrap() { return delegate; }
}
