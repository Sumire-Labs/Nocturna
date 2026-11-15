package me.jellysquid.mods.sodium.client.model.quad.properties;

import me.jellysquid.mods.sodium.client.model.quad.ModelQuad;
import net.minecraft.core.Direction;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.testutil.TestBakedQuads;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ModelQuadFlagsTest {
    @Test
    void requiresOpaqueVertexColorsAndATrustedSpriteForPassOptimization() {
        try (var quads = new TestBakedQuads()) {
            for (int alpha : new int[] {0, 1, 127, 254, 255}) {
                for (boolean trusted : new boolean[] {false, true}) {
                    var quad = quads.quad(SpriteTransparencyLevel.OPAQUE, alpha, true, true, trusted);
                    int flags = quad.getFlags();
                    assertEquals(alpha == 255 && trusted, (flags & ModelQuadFlags.IS_PASS_OPTIMIZABLE) != 0);
                    assertTrue((flags & ModelQuadFlags.IS_POPULATED) != 0);
                    assertEquals(flags, quad.getFlags(), "Cached metadata must stay stable");
                }
            }
        }
    }

    @Test
    void oneTranslucentVertexPreventsMovingAnOtherwiseOpaqueQuad() {
        try (var quads = new TestBakedQuads()) {
            for (int vertex = 0; vertex < 4; vertex++) {
                var quad = quads.quad(SpriteTransparencyLevel.OPAQUE, 255, true, true, true);
                quad.getVertices()[vertex * 8 + 3] = 0x7FFFFFFF;
                assertEquals(0, quad.getFlags() & ModelQuadFlags.IS_PASS_OPTIMIZABLE);
            }
        }
    }

    @Test
    void recomputesGeometryWhileRetainingVanillaShadingAndUnrelatedFlags() {
        var quad = new ModelQuad();
        for (int i = 0; i < 4; i++) {
            quad.setX(i, i >= 2 ? 1 : 0);
            quad.setY(i, i == 1 || i == 2 ? 1 : 0);
            quad.setZ(i, 0);
            quad.setColor(i, -1);
        }
        int custom = 1 << 24;
        int existing = custom | ModelQuadFlags.IS_VANILLA_SHADED | ModelQuadFlags.IS_TRUSTED_SPRITE
                | ModelQuadFlags.IS_PARTIAL;
        int flags = ModelQuadFlags.getQuadFlags(quad, Direction.NORTH, existing);
        assertEquals(existing & ~ModelQuadFlags.IS_PARTIAL, flags & existing);
        assertEquals(0, flags & ModelQuadFlags.IS_PARTIAL);
        assertTrue((flags & ModelQuadFlags.IS_ALIGNED) != 0);
        assertTrue((flags & ModelQuadFlags.IS_PARALLEL) != 0);
        assertTrue((flags & ModelQuadFlags.IS_PASS_OPTIMIZABLE) != 0);
        assertEquals(flags & 7, ModelQuadFlags.getQuadFlags(quad, Direction.NORTH) & 7);
    }

    @Test
    void exposesTransparencyOnlyForTrustedSprites() {
        try (var quads = new TestBakedQuads()) {
            for (var level : SpriteTransparencyLevel.values()) {
                assertEquals(level, quads.quad(level, 255, true, true, true).getTransparencyLevel());
                assertNull(quads.quad(level, 255, true, true, false).getTransparencyLevel());
            }
            assertNull(quads.quad(null, 255, true, true, true).getTransparencyLevel());
        }
    }
}
