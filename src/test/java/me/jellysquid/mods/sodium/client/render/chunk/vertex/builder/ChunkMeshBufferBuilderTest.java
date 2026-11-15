package me.jellysquid.mods.sodium.client.render.chunk.vertex.builder;

import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.data.BuiltSectionInfo;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.DefaultTerrainRenderPasses;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexEncoder;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkVertexType;
import org.embeddedt.embeddium.render.chunk.sorting.TranslucentQuadAnalyzer;
import org.embeddedt.embeddium.testutil.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.lwjgl.system.MemoryUtil;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

class ChunkMeshBufferBuilderTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void allocatesLazilyAndRetainsCapacityAcrossSections() throws Exception {
        for (var type : types()) {
            int stride = type.getVertexFormat().getStride();
            var builder = new ChunkMeshBufferBuilder(type, stride * 8, false);
            try {
                builder.start(3);
                assertNull(buffer(builder));
                assertTrue(builder.isEmpty());
                assertThrows(IllegalStateException.class, builder::slice);
                builder.push(vertices(4, 1), DefaultMaterials.SOLID);
                assertEquals(stride * 8, buffer(builder).capacity());
                // Preserve upstream's >= boundary, including expansion when the next write fits exactly.
                builder.push(vertices(4, 2), DefaultMaterials.SOLID);
                assertEquals(stride * 16, buffer(builder).capacity());
                long address = MemoryUtil.memAddress(buffer(builder));
                builder.start(9);
                assertTrue(builder.isEmpty());
                assertEquals(stride * 16, buffer(builder).capacity());
                assertEquals(address, MemoryUtil.memAddress(buffer(builder)));
                builder.push(vertices(4, 3), DefaultMaterials.SOLID);
                assertEquals(4, builder.count());
                builder.destroy();
                builder.start(2);
                assertNull(buffer(builder));
                builder.push(vertices(4, 4), DefaultMaterials.SOLID);
                assertEquals(stride * 8, buffer(builder).capacity());
            } finally {
                builder.destroy();
            }
        }
    }

    @Test
    void preservesEveryEncodedByteThroughRepeatedGrowthAndReuse() {
        for (var type : types()) {
            var builder = new ChunkMeshBufferBuilder(type, 64, false);
            try {
                for (int section : new int[] {0, 3, 255}) {
                    builder.start(section);
                    var all = new ArrayList<ChunkVertexEncoder.Vertex>();
                    for (int batch = 0; batch < 64; batch++) {
                        var data = vertices(4 * (1 + batch % 8), section * 100 + batch);
                        all.addAll(List.of(data));
                        builder.push(data, DefaultMaterials.CUTOUT_MIPPED);
                        assertEquals(all.size(), builder.count());
                        assertArrayEquals(encode(type, all, section), bytes(builder.slice()));
                    }
                }
            } finally {
                builder.destroy();
            }
        }
    }

    @Test
    void resetsSortAnalysisWithoutDiscardingStorage() throws Exception {
        var builder = new ChunkMeshBufferBuilder(ChunkMeshFormats.COMPACT, 64, true);
        try {
            builder.start(0);
            var first = planarQuad(0);
            var second = planarQuad(1);
            builder.push(first, DefaultMaterials.TRANSLUCENT);
            builder.push(second, DefaultMaterials.TRANSLUCENT);
            assertEquals(TranslucentQuadAnalyzer.Level.STATIC, builder.getSortState().level());
            long address = MemoryUtil.memAddress(buffer(builder));
            builder.start(1);
            assertSame(TranslucentQuadAnalyzer.SortState.NONE, builder.getSortState());
            assertEquals(address, MemoryUtil.memAddress(buffer(builder)));
            builder.push(first, DefaultMaterials.TRANSLUCENT);
            assertSame(TranslucentQuadAnalyzer.SortState.NONE, builder.getSortState());
        } finally {
            builder.destroy();
        }
    }

    @Test
    void uses64KiBInitialBuffersForEveryRenderPassAndFacing() throws Exception {
        var buffers = new ChunkBuildBuffers(ChunkMeshFormats.COMPACT);
        try {
            buffers.init(new BuiltSectionInfo.Builder(), 0);
            var visited = java.util.Collections.newSetFromMap(
                    new java.util.IdentityHashMap<ChunkMeshBufferBuilder, Boolean>());
            for (var pass : DefaultTerrainRenderPasses.ALL) {
                for (var facing : ModelQuadFacing.VALUES) {
                    var builder = buffers.get(pass).getVertexBuffer(facing);
                    if (!visited.add(builder)) {
                        continue;
                    }
                    assertNull(buffer(builder));
                    builder.push(vertices(4, 0), DefaultMaterials.SOLID);
                    assertEquals(64 * 1024, buffer(builder).capacity());
                    builder.start(0);
                }
            }
        } finally {
            buffers.destroy();
        }
    }

    private static ChunkVertexType[] types() {
        return new ChunkVertexType[] {ChunkMeshFormats.COMPACT, ChunkMeshFormats.VANILLA_LIKE};
    }

    private static ByteBuffer buffer(ChunkMeshBufferBuilder builder) throws Exception {
        var field = ChunkMeshBufferBuilder.class.getDeclaredField("buffer");
        field.setAccessible(true);
        return (ByteBuffer) field.get(builder);
    }

    private static ChunkVertexEncoder.Vertex[] vertices(int count, long seed) {
        Random random = new Random(seed);
        var result = new ChunkVertexEncoder.Vertex[count];
        for (int i = 0; i < count; i++) {
            var vertex = result[i] = new ChunkVertexEncoder.Vertex();
            vertex.x = random.nextFloat() * 16;
            vertex.y = random.nextFloat() * 16;
            vertex.z = random.nextFloat() * 16;
            vertex.u = random.nextFloat();
            vertex.v = random.nextFloat();
            vertex.color = random.nextInt();
            vertex.light = 0x00F000F0;
        }
        return result;
    }

    private static ChunkVertexEncoder.Vertex[] planarQuad(float z) {
        var result = vertices(4, 0);
        for (int i = 0; i < 4; i++) {
            result[i].x = i >= 2 ? 1 : 0;
            result[i].y = i == 1 || i == 2 ? 1 : 0;
            result[i].z = z;
        }
        return result;
    }

    private static byte[] encode(ChunkVertexType type, List<ChunkVertexEncoder.Vertex> vertices, int section) {
        var output = MemoryUtil.memAlloc(vertices.size() * type.getVertexFormat().getStride());
        try {
            long pointer = MemoryUtil.memAddress(output);
            for (var vertex : vertices) {
                pointer = type.getEncoder().write(pointer, DefaultMaterials.CUTOUT_MIPPED, vertex, section);
            }
            return bytes(output);
        } finally {
            MemoryUtil.memFree(output);
        }
    }

    private static byte[] bytes(ByteBuffer input) {
        byte[] result = new byte[input.remaining()];
        input.duplicate().get(result);
        return result;
    }
}
