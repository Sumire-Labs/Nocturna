package me.jellysquid.mods.sodium.client.render.chunk.compile.pipeline;

import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.render.chunk.compile.ChunkBuildBuffers;
import me.jellysquid.mods.sodium.client.render.chunk.compile.buffers.ChunkModelBuilder;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.DefaultMaterials;
import me.jellysquid.mods.sodium.client.render.chunk.terrain.material.Material;
import me.jellysquid.mods.sodium.client.render.chunk.vertex.format.ChunkMeshFormats;
import net.minecraft.client.renderer.block.model.BakedQuad;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.testutil.TestBakedQuads;
import org.embeddedt.embeddium.testutil.MinecraftBootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Tests the real renderer's policy methods; the game-only world/lighting constructor is not invoked. */
class BlockRendererPolicyTest {
    private static final int REORIENT = 1;
    private static final int OPTIMIZE = 2;
    private static final int ALL = REORIENT | OPTIMIZE;

    @BeforeAll
    static void bootstrapMinecraft() {
        MinecraftBootstrap.initialize();
    }

    @Test
    void preservesLayerOrderForEveryTransparencySequenceUpToFourQuads() throws Exception {
        var renderer = policy(true);
        try (var factory = new TestBakedQuads()) {
            for (int length = 1; length <= 4; length++) {
                for (int encoded = 0; encoded < Math.pow(3, length); encoded++) {
                    int digits = encoded;
                    var quads = new ArrayList<BakedQuad>();
                    int[] order = new int[length];
                    for (int i = 0; i < length; i++) {
                        order[i] = digits % 3;
                        digits /= 3;
                        quads.add(factory.quad(SpriteTransparencyLevel.values()[order[i]], 255, true, true, true));
                    }
                    boolean ordered = true;
                    for (int i = 1; i < length; i++) {
                        ordered &= order[i - 1] <= order[i];
                    }
                    int flags = check(renderer, quads, ALL);
                    assertEquals(ordered, (flags & OPTIMIZE) != 0, java.util.Arrays.toString(order));
                    assertEquals(REORIENT, flags & REORIENT);
                }
            }
        }
    }

    @Test
    void lightingDifferencesDisableReorientationWithoutDiscardingPassEligibility() throws Exception {
        var renderer = policy(true);
        try (var factory = new TestBakedQuads()) {
            for (int first = 0; first < 4; first++) {
                for (int second = 0; second < 4; second++) {
                    var a = factory.quad(SpriteTransparencyLevel.OPAQUE, 255,
                            (first & 1) != 0, (first & 2) != 0, true);
                    var b = factory.quad(SpriteTransparencyLevel.OPAQUE, 255,
                            (second & 1) != 0, (second & 2) != 0, true);
                    int flags = check(renderer, List.of(a, b), ALL);
                    assertEquals(first == second, (flags & REORIENT) != 0);
                    assertEquals(OPTIMIZE, flags & OPTIMIZE);
                }
            }
        }
    }

    @Test
    void handlesUnknownSpritesAndVertexAlphaAsTranslucentInTheLayerOrderCheck() throws Exception {
        var renderer = policy(true);
        try (var factory = new TestBakedQuads()) {
            var opaque = factory.quad(SpriteTransparencyLevel.OPAQUE, 255, true, true, true);
            for (var uncertain : List.of(
                    factory.quad(SpriteTransparencyLevel.OPAQUE, 255, true, true, false),
                    factory.quad(SpriteTransparencyLevel.OPAQUE, 127, true, true, true),
                    factory.quad(null, 255, true, true, true))) {
                assertEquals(0, check(renderer, List.of(uncertain, opaque), ALL) & OPTIMIZE);
                assertEquals(OPTIMIZE, check(renderer, List.of(opaque, uncertain), ALL) & OPTIMIZE);
            }
        }
    }

    @Test
    void honorsDisabledOptimizationAndInheritedNullFaceRestrictions() throws Exception {
        try (var factory = new TestBakedQuads()) {
            var opaque = factory.quad(SpriteTransparencyLevel.OPAQUE, 255, true, true, true);
            var translucent = factory.quad(SpriteTransparencyLevel.TRANSLUCENT, 255, true, true, true);
            assertEquals(REORIENT, check(policy(false), List.of(opaque), ALL));
            assertEquals(REORIENT, check(policy(false), List.of(), ALL));
            var renderer = policy(true);
            int inherited = check(renderer, List.of(translucent, opaque), ALL);
            assertEquals(REORIENT, inherited);
            assertEquals(REORIENT, check(renderer, List.of(opaque), inherited));
            assertEquals(OPTIMIZE, check(renderer, List.of(opaque), OPTIMIZE));
        }
    }

    @Test
    void routesEligibleQuadsToTheOriginalUpstreamPasses() throws Exception {
        var renderer = policy(true);
        var buffers = new ChunkBuildBuffers(ChunkMeshFormats.COMPACT);
        try (var factory = new TestBakedQuads()) {
            var opaque = factory.quad(SpriteTransparencyLevel.OPAQUE, 255, true, true, true);
            var cutout = factory.quad(SpriteTransparencyLevel.TRANSPARENT, 255, true, true, true);
            var translucent = factory.quad(SpriteTransparencyLevel.TRANSLUCENT, 255, true, true, true);
            check(renderer, List.of(opaque, cutout, translucent), ALL);
            assertSame(buffers.get(DefaultMaterials.SOLID), choose(renderer, DefaultMaterials.CUTOUT_MIPPED, buffers, opaque));
            assertSame(buffers.get(DefaultMaterials.CUTOUT_MIPPED), choose(renderer, DefaultMaterials.TRANSLUCENT, buffers, cutout));
            assertSame(buffers.get(DefaultMaterials.TRANSLUCENT), choose(renderer, DefaultMaterials.TRANSLUCENT, buffers, translucent));
            assertSame(buffers.get(DefaultMaterials.SOLID), choose(renderer, DefaultMaterials.SOLID, buffers, translucent));
            var withAlpha = factory.quad(SpriteTransparencyLevel.OPAQUE, 127, true, true, true);
            assertSame(buffers.get(DefaultMaterials.CUTOUT_MIPPED), choose(renderer, DefaultMaterials.CUTOUT_MIPPED, buffers, withAlpha));

            // Regression: an opaque top layer may not move ahead of its cutout base layer into the solid pass.
            check(renderer, List.of(cutout, opaque), ALL);
            assertSame(buffers.get(DefaultMaterials.CUTOUT_MIPPED), choose(renderer, DefaultMaterials.CUTOUT_MIPPED, buffers, opaque));
        } finally {
            buffers.destroy();
        }
    }

    private static BlockRenderer policy(boolean enabled) throws Exception {
        var unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        var renderer = (BlockRenderer) ((Unsafe) unsafeField.get(null)).allocateInstance(BlockRenderer.class);
        var enabledField = BlockRenderer.class.getDeclaredField("enableRenderPassOptimization");
        enabledField.setAccessible(true);
        enabledField.setBoolean(renderer, enabled);
        return renderer;
    }

    private static int check(BlockRenderer renderer, List<? extends BakedQuad> quads, int initialFlags) throws Exception {
        var flags = BlockRenderer.class.getDeclaredField("quadRenderingFlags");
        flags.setAccessible(true);
        flags.setInt(renderer, initialFlags);
        Method check = BlockRenderer.class.getDeclaredMethod("checkQuadsHaveSameLightingConfig", List.class);
        check.setAccessible(true);
        check.invoke(renderer, quads);
        return flags.getInt(renderer);
    }

    private static ChunkModelBuilder choose(BlockRenderer renderer, Material material,
                                            ChunkBuildBuffers buffers, BakedQuadView quad) throws Exception {
        var choose = BlockRenderer.class.getDeclaredMethod("chooseOptimalBuilder", Material.class,
                ChunkBuildBuffers.class, ChunkModelBuilder.class, BakedQuadView.class);
        choose.setAccessible(true);
        return (ChunkModelBuilder) choose.invoke(renderer, material, buffers, buffers.get(material), quad);
    }
}
