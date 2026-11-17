package org.embeddedt.embeddium.impl.extra.compat;

import com.mojang.blaze3d.vertex.*;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.debug.DebugRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;

import org.embeddedt.embeddium.impl.render.CeleritasWorldRenderer;

import java.util.*;

/**
 * Routes visualization flags to the host's actual debug renderers and live Celeritas section data.
 */
public final class DebugVisuals {
    public record Node(int x, int y, int z, int incoming, boolean visible) {}

    public static volatile boolean captureRequested;
    private static volatile List<Node> traversal = List.of();

    public static void publish(List<Node> nodes) {
        traversal = List.copyOf(nodes);
    }

    public static List<Node> traversal() {
        return traversal;
    }

    public static void clearWorld() {
        captureRequested = false;
        traversal = List.of();
    }

    private static boolean enabled(String id) {
        return DebugOverlayManager.enabled(id);
    }

    public static void render(
            DebugRenderer renderer,
            PoseStack poses,
            MultiBufferSource.BufferSource buffers,
            double x,
            double y,
            double z) {
        var client = Minecraft.getInstance();
        if (client.level == null || client.showOnlyReducedInfo()) return;
        if (enabled("chunk_borders")) renderer.chunkBorderRenderer.render(poses, buffers, x, y, z);
        if (enabled("visualize_water_levels"))
            renderer.waterDebugRenderer.render(poses, buffers, x, y, z);
        if (enabled("visualize_heightmap"))
            renderer.heightMapRenderer.render(poses, buffers, x, y, z);
        if (enabled("visualize_collision_boxes"))
            renderer.collisionBoxRenderer.render(poses, buffers, x, y, z);
        if (enabled("visualize_entity_supporting_blocks"))
            renderer.supportBlockRenderer.render(poses, buffers, x, y, z);
        if (enabled("visualize_solid_faces"))
            renderer.solidFaceRenderer.render(poses, buffers, x, y, z);
        if (enabled("visualize_chunks_on_server"))
            renderer.chunkRenderer.render(poses, buffers, x, y, z);
        if (enabled("visualize_sky_light_sections"))
            renderer.skyLightSectionDebugRenderer.render(poses, buffers, x, y, z);
        boolean block = enabled("visualize_block_light_levels"),
                sky = enabled("visualize_sky_light_levels");
        if (block || sky) {
            var center = BlockPos.containing(x, y, z);
            for (BlockPos p :
                    BlockPos.betweenClosed(center.offset(-5, -5, -5), center.offset(5, 5, 5))) {
                String label =
                        (block ? "B:" + client.level.getBrightness(LightLayer.BLOCK, p) : "")
                                + (sky
                                        ? " S:" + client.level.getBrightness(LightLayer.SKY, p)
                                        : "");
                DebugRenderer.renderFloatingText(
                        poses,
                        buffers,
                        label,
                        p.getX() + 0.5,
                        p.getY() + 0.2,
                        p.getZ() + 0.5,
                        0xffffffff,
                        0.015F,
                        true,
                        0,
                        true);
            }
        }
        boolean paths = enabled("chunk_section_paths"),
                regions = enabled("chunk_section_octree"),
                visibility = enabled("chunk_section_visibility");
        boolean capture = paths || visibility;
        if (capture && !captureRequested) client.levelRenderer.needsUpdate();
        captureRequested = capture;
        if (!capture) traversal = List.of();
        if (paths || regions || visibility) {
            var world = CeleritasWorldRenderer.instance();
            var manager =
                    ((me.flashyreese.mods.sodiumextra.mixin.core.AccessorDebugSections) world)
                            .nocturna$getSections();
            if (manager == null) return;
            var vertices = buffers.getBuffer(RenderType.lines());
            var seen = new HashSet<Long>();
            for (var section : manager.getAllRenderSections()) {
                int sx = section.getOriginX(), sy = section.getOriginY(), sz = section.getOriginZ();
                if (Math.abs(sx - x) > 256 || Math.abs(sz - z) > 256) continue;
                boolean visible =
                        manager.isSectionVisible(
                                section.getChunkX(), section.getChunkY(), section.getChunkZ());
                if (visibility)
                    LevelRenderer.renderLineBox(
                            poses,
                            vertices,
                            sx - x,
                            sy - y,
                            sz - z,
                            sx + 16 - x,
                            sy + 16 - y,
                            sz + 16 - z,
                            visible ? 0.1F : 1F,
                            visible ? 1F : 0.1F,
                            0.2F,
                            0.8F);
                if (regions) {
                    var region = section.getRegion();
                    long key = region.getId();
                    if (seen.add(key))
                        LevelRenderer.renderLineBox(
                                poses,
                                vertices,
                                region.getOriginX() - x,
                                region.getOriginY() - y,
                                region.getOriginZ() - z,
                                region.getOriginX()
                                        + org.embeddedt.embeddium.impl.render.chunk.region
                                                .RenderRegion.REGION_BLOCK_WIDTH
                                        - x,
                                region.getOriginY()
                                        + org.embeddedt.embeddium.impl.render.chunk.region
                                                .RenderRegion.REGION_BLOCK_HEIGHT
                                        - y,
                                region.getOriginZ()
                                        + org.embeddedt.embeddium.impl.render.chunk.region
                                                .RenderRegion.REGION_BLOCK_LENGTH
                                        - z,
                                0.8F,
                                0.3F,
                                1F,
                                1F);
                }
            }
            if (paths)
                for (var node : traversal) {
                    if (!node.visible) continue;
                    for (var face : net.minecraft.core.Direction.values())
                        if ((node.incoming & (1 << face.ordinal())) != 0) {
                            var matrix = poses.last().pose();
                            var normal = poses.last().normal();
                            float sx = (float) (node.x * 16 + 8 - x),
                                    sy = (float) (node.y * 16 + 8 - y),
                                    sz = (float) (node.z * 16 + 8 - z);
                            vertices.vertex(matrix, sx, sy, sz)
                                    .color(0.2F, 0.7F, 1F, 1F)
                                    .normal(
                                            normal,
                                            face.getStepX(),
                                            face.getStepY(),
                                            face.getStepZ())
                                    .endVertex();
                            vertices.vertex(
                                            matrix,
                                            sx + face.getStepX() * 16,
                                            sy + face.getStepY() * 16,
                                            sz + face.getStepZ() * 16)
                                    .color(0.2F, 0.7F, 1F, 1F)
                                    .normal(
                                            normal,
                                            face.getStepX(),
                                            face.getStepY(),
                                            face.getStepZ())
                                    .endVertex();
                        }
                }
        }
    }
}
