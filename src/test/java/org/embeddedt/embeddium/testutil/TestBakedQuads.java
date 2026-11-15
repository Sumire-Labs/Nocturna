package org.embeddedt.embeddium.testutil;

import com.mojang.blaze3d.platform.NativeImage;
import me.jellysquid.mods.sodium.client.model.quad.BakedQuadView;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFacing;
import me.jellysquid.mods.sodium.client.model.quad.properties.ModelQuadFlags;
import me.jellysquid.mods.sodium.mixin.core.model.quad.BakedQuadMixin;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.SpriteContents;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.metadata.animation.AnimationMetadataSection;
import net.minecraft.client.resources.metadata.animation.FrameSize;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevel;
import org.embeddedt.embeddium.impl.render.chunk.sprite.SpriteTransparencyLevelHolder;

import java.util.ArrayList;
import java.util.List;

/** Uses real Minecraft quads/sprites and the production mixin methods without booting a Mixin game launcher. */
public final class TestBakedQuads implements AutoCloseable {
    private final List<SpriteContents> contents = new ArrayList<>();

    public Quad quad(SpriteTransparencyLevel level, int alpha, boolean shade, boolean ao, boolean trusted) {
        TextureAtlasSprite sprite = null;
        if (level != null) {
            var image = new Contents(level);
            this.contents.add(image);
            sprite = new Sprite(image);
        }
        return new Quad(sprite, alpha, shade, ao, trusted);
    }

    @Override
    public void close() {
        this.contents.forEach(SpriteContents::close);
    }

    private static class Contents extends SpriteContents implements SpriteTransparencyLevelHolder {
        private final SpriteTransparencyLevel level;

        Contents(SpriteTransparencyLevel level) {
            super(new ResourceLocation("embeddium", "test"), new FrameSize(1, 1),
                    new NativeImage(1, 1, false), AnimationMetadataSection.EMPTY);
            this.level = level;
        }

        @Override
        public SpriteTransparencyLevel embeddium$getTransparencyLevel() {
            return this.level;
        }
    }

    private static class Sprite extends TextureAtlasSprite {
        Sprite(SpriteContents contents) {
            super(new ResourceLocation("embeddium", "test_atlas"), contents, 1, 1, 0, 0);
        }
    }

    public static final class Quad extends BakedQuad implements BakedQuadView {
        private final View view;

        Quad(TextureAtlasSprite sprite, int alpha, boolean shade, boolean ao, boolean trusted) {
            super(vertices(alpha), -1, Direction.NORTH, sprite, shade, ao);
            this.view = new View(this.vertices, sprite);
            this.view.setFlags(trusted ? ModelQuadFlags.IS_TRUSTED_SPRITE : 0);
        }

        private static int[] vertices(int alpha) {
            int[] data = new int[32];
            float[][] corners = {{0, 0}, {0, 1}, {1, 1}, {1, 0}};
            for (int i = 0; i < 4; i++) {
                data[i * 8] = Float.floatToRawIntBits(corners[i][0]);
                data[i * 8 + 1] = Float.floatToRawIntBits(corners[i][1]);
                data[i * 8 + 3] = alpha << 24 | 0xFFFFFF;
            }
            return data;
        }

        @Override public float getX(int index) { return this.view.getX(index); }
        @Override public float getY(int index) { return this.view.getY(index); }
        @Override public float getZ(int index) { return this.view.getZ(index); }
        @Override public int getColor(int index) { return this.view.getColor(index); }
        @Override public float getTexU(int index) { return this.view.getTexU(index); }
        @Override public float getTexV(int index) { return this.view.getTexV(index); }
        @Override public int getLight(int index) { return this.view.getLight(index); }
        @Override public int getForgeNormal(int index) { return this.view.getForgeNormal(index); }
        @Override public int getComputedFaceNormal() { return this.view.getComputedFaceNormal(); }
        @Override public int getFlags() { return this.view.getFlags(); }
        @Override public void setFlags(int flags) { this.view.setFlags(flags); }
        @Override public int getColorIndex() { return this.view.getColorIndex(); }
        @Override public Direction getLightFace() { return this.view.getLightFace(); }
        @Override public ModelQuadFacing getNormalFace() { return this.view.getNormalFace(); }
        @Override public boolean hasShade() { return this.isShade(); }
        @Override public SpriteTransparencyLevel getTransparencyLevel() { return this.view.getTransparencyLevel(); }
    }

    private static final class View extends BakedQuadMixin {
        View(int[] vertices, TextureAtlasSprite sprite) {
            this.vertices = vertices;
            this.sprite = sprite;
            this.tintIndex = -1;
            this.direction = Direction.NORTH;
        }
    }
}
