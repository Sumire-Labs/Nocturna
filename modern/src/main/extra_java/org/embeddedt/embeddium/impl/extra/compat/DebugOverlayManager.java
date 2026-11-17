package org.embeddedt.embeddium.impl.extra.compat;

import com.mojang.blaze3d.platform.GlUtil;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.*;

import java.util.*;
import java.util.function.*;

/**
 * Complete debug-entry catalog with host-version data providers, shared by both profiles and all
 * views.
 */
public final class DebugOverlayManager {
    public interface AllocationRate {
        long nocturna$rate(long heapUsage);
    }

    public static AllocationRate allocationRate;

    public record Entry(
            ResourceLocation id,
            Component name,
            Component tooltip,
            String category,
            boolean reducedAllowed,
            boolean right,
            Supplier<List<String>> text) {}

    private static final LinkedHashMap<ResourceLocation, Entry> ENTRIES = new LinkedHashMap<>();
    private static DebugOverlayState state;
    private static long nextRefresh;
    private static long revision;
    private static net.minecraft.client.multiplayer.ClientLevel hudLevel;
    private static boolean hudDebug, hudReduced;
    private static List<String> left = List.of(), right = List.of();
    private static net.minecraft.server.level.ServerLevel chunkLevel;
    private static net.minecraft.world.level.ChunkPos chunkPosition;
    private static java.util.concurrent.CompletableFuture<
                    net.minecraft.world.level.chunk.LevelChunk>
            serverChunk;

    public static DebugOverlayState state() {
        if (state == null) {
            catalog();
            state =
                    new DebugOverlayState(
                            net.minecraftforge.fml.loading.FMLPaths.GAMEDIR
                                    .get()
                                    .resolve("debug-profile.json"));
            state.importLegacy(
                    net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR
                            .get()
                            .resolve("sodium-extra-debug.json"));
        }
        return state;
    }

    public static Collection<Entry> entries() {
        state();
        return Collections.unmodifiableCollection(ENTRIES.values());
    }

    private static Minecraft mc() {
        return Minecraft.getInstance();
    }

    private static void add(
            String id,
            String label,
            String description,
            String category,
            boolean reduced,
            boolean right,
            Supplier<List<String>> provider) {
        var key = new ResourceLocation("minecraft", id);
        ENTRIES.put(
                key,
                new Entry(
                        key,
                        Component.literal(label),
                        Component.literal(description),
                        category,
                        reduced,
                        right,
                        provider));
    }

    private static List<String> one(Object value) {
        return List.of(String.valueOf(value));
    }

    private static void catalog() {
        if (!ENTRIES.isEmpty()) return;
        add(
                "game_version",
                "Game version",
                "Version, launcher and client brand.",
                "General",
                true,
                false,
                () ->
                        one(
                                "Minecraft "
                                        + net.minecraft.SharedConstants.getCurrentVersion()
                                                .getName()
                                        + " ("
                                        + net.minecraft.client.ClientBrandRetriever
                                                .getClientModName()
                                        + ")"));
        add(
                "fps",
                "FPS",
                "Rendered frames per second.",
                "Performance",
                true,
                false,
                () -> one(mc().fpsString));
        add(
                "tps",
                "Server tick time",
                "Server tick duration and network packet rates.",
                "Performance",
                true,
                false,
                () -> {
                    var c = mc().getConnection().getConnection();
                    var server = mc().getSingleplayerServer();
                    return one(
                            server != null
                                    ? String.format(
                                            Locale.ROOT,
                                            "Integrated server @ %.1f/50.0 ms, %.0f tx, %.0f rx",
                                            server.getAverageTickTime(),
                                            c.getAverageSentPackets(),
                                            c.getAverageReceivedPackets())
                                    : String.format(
                                            Locale.ROOT,
                                            "\"%s\" server, %.0f tx, %.0f rx",
                                            mc().player.getServerBrand(),
                                            c.getAverageSentPackets(),
                                            c.getAverageReceivedPackets()));
                });
        add(
                "memory",
                "Memory",
                "Used and allocated Java heap.",
                "System",
                true,
                true,
                () -> {
                    var r = Runtime.getRuntime();
                    long used = r.totalMemory() - r.freeMemory();
                    return List.of(
                            String.format(
                                    Locale.ROOT,
                                    "Mem: %2d%% %03d/%03dMiB",
                                    used * 100 / r.maxMemory(),
                                    used / 1048576,
                                    r.maxMemory() / 1048576),
                            String.format(
                                    Locale.ROOT,
                                    "Allocation rate: %03dMiB/s",
                                    allocationRate.nocturna$rate(used) / 1048576),
                            String.format(
                                    Locale.ROOT,
                                    "Allocated: %2d%% %03dMiB",
                                    r.totalMemory() * 100 / r.maxMemory(),
                                    r.totalMemory() / 1048576));
                });
        add(
                "detailed_memory",
                "Detailed memory",
                "Initial, used, committed and maximum heap and non-heap memory.",
                "System",
                true,
                true,
                () -> {
                    var bean = java.lang.management.ManagementFactory.getMemoryMXBean();
                    return List.of(
                            memoryUsage("heap", bean.getHeapMemoryUsage()),
                            memoryUsage("non-heap", bean.getNonHeapMemoryUsage()));
                });
        add(
                "system_specs",
                "System specifications",
                "Java, CPU, display and graphics driver.",
                "System",
                true,
                true,
                () ->
                        List.of(
                                "Java: "
                                        + System.getProperty("java.version")
                                        + " "
                                        + System.getProperty("os.arch"),
                                "CPU: " + GlUtil.getCpuInfo(),
                                "Display: "
                                        + mc().getWindow().getWidth()
                                        + "x"
                                        + mc().getWindow().getHeight(),
                                org.lwjgl.opengl.GL11C.glGetString(
                                        org.lwjgl.opengl.GL11C.GL_VENDOR),
                                org.lwjgl.opengl.GL11C.glGetString(
                                        org.lwjgl.opengl.GL11C.GL_RENDERER),
                                org.lwjgl.opengl.GL11C.glGetString(
                                        org.lwjgl.opengl.GL11C.GL_VERSION)));
        add(
                "chunk_render_stats",
                "Chunk render statistics",
                "The renderer's live section and rebuild statistics.",
                "Renderer",
                true,
                false,
                () -> one(mc().levelRenderer.getChunkStatistics()));
        add(
                "chunk_generation_stats",
                "Chunk generation statistics",
                "Integrated server chunk generation statistics.",
                "World",
                false,
                false,
                () -> {
                    var level = serverLevel();
                    return level != null ? one(level.gatherChunkSourceStats()) : List.of();
                });
        add(
                "entity_render_stats",
                "Entity render statistics",
                "Rendered and loaded entities.",
                "Renderer",
                true,
                false,
                () -> one(mc().levelRenderer.getEntityStatistics()));
        add(
                "particle_render_stats",
                "Particle render statistics",
                "Active particles and total entities.",
                "Renderer",
                true,
                false,
                () ->
                        one(
                                "P: "
                                        + mc().particleEngine.countParticles()
                                        + ". T: "
                                        + mc().level.getEntityCount()));
        add(
                "chunk_source_stats",
                "Chunk source statistics",
                "Client chunk source statistics.",
                "World",
                true,
                false,
                () -> one(mc().level.gatherChunkSourceStats()));
        add(
                "player_position",
                "Position",
                "Coordinates, block coordinates and facing direction.",
                "Player",
                false,
                false,
                () -> {
                    var e = mc().getCameraEntity();
                    var p = e.position();
                    var b = e.blockPosition();
                    return List.of(
                            String.format(Locale.ROOT, "XYZ: %.3f / %.5f / %.3f", p.x, p.y, p.z),
                            "Block: " + b.toShortString(),
                            String.format(
                                    Locale.ROOT,
                                    "Facing: %s (%.1f / %.1f)",
                                    e.getDirection(),
                                    e.getYRot(),
                                    e.getXRot()));
                });
        add(
                "player_section_position",
                "Section position",
                "Section and chunk-relative coordinates.",
                "Player",
                true,
                false,
                () -> {
                    var b = mc().getCameraEntity().blockPosition();
                    return List.of(
                            "Section: "
                                    + (b.getX() >> 4)
                                    + " "
                                    + (b.getY() >> 4)
                                    + " "
                                    + (b.getZ() >> 4),
                            "Chunk-relative: "
                                    + (b.getX() & 15)
                                    + " "
                                    + (b.getY() & 15)
                                    + " "
                                    + (b.getZ() & 15));
                });
        add(
                "player_speed",
                "Player speed",
                "Horizontal and vertical movement in blocks per second.",
                "Player",
                false,
                false,
                () -> {
                    var v = mc().player.getDeltaMovement().scale(20);
                    return one(
                            String.format(
                                    Locale.ROOT,
                                    "Speed: %.3f b/s horizontal, %.3f b/s vertical",
                                    Math.hypot(v.x, v.z),
                                    v.y));
                });
        add(
                "light_levels",
                "Light levels",
                "Client block and sky light at the camera.",
                "World",
                false,
                false,
                () -> {
                    var b = mc().getCameraEntity().blockPosition();
                    return one(
                            "Client Light: "
                                    + mc().level.getRawBrightness(b, 0)
                                    + " ("
                                    + mc().level
                                            .getBrightness(
                                                    net.minecraft.world.level.LightLayer.SKY, b)
                                    + " sky, "
                                    + mc().level
                                            .getBrightness(
                                                    net.minecraft.world.level.LightLayer.BLOCK, b)
                                    + " block)");
                });
        add(
                "heightmap",
                "Heightmaps",
                "Client and server heightmap values.",
                "World",
                false,
                false,
                DebugOverlayManager::heightmaps);
        add(
                "biome",
                "Biome",
                "Biome registry id at the camera.",
                "World",
                false,
                false,
                () ->
                        one(
                                "Biome: "
                                        + mc().level
                                                .getBiome(mc().getCameraEntity().blockPosition())
                                                .unwrapKey()
                                                .map(k -> k.location().toString())
                                                .orElse("unregistered")));
        add(
                "local_difficulty",
                "Local difficulty",
                "Effective local difficulty and special multiplier.",
                "World",
                false,
                false,
                () -> {
                    var d = mc().level.getCurrentDifficultyAt(mc().player.blockPosition());
                    return one(
                            String.format(
                                    Locale.ROOT,
                                    "Local Difficulty: %.2f // %.2f",
                                    d.getEffectiveDifficulty(),
                                    d.getSpecialMultiplier()));
                });
        add(
                "day_count",
                "Day count",
                "World day number.",
                "World",
                false,
                false,
                () -> one("Day " + mc().level.getDayTime() / 24000));
        add(
                "entity_spawn_counts",
                "Entity spawn counts",
                "Server mob cap and spawning statistics.",
                "World",
                false,
                false,
                () -> {
                    var l = serverLevel();
                    var spawn = l != null ? l.getChunkSource().getLastSpawnState() : null;
                    if (spawn == null) return List.of();
                    var out = new ArrayList<String>();
                    out.add("SC: " + spawn.getSpawnableChunkCount());
                    spawn.getMobCategoryCounts().forEach((k, v) -> out.add(k.getName() + ": " + v));
                    return out;
                });
        add(
                "sound_mood",
                "Sound mood",
                "Ambient mood percentage.",
                "Sound",
                false,
                false,
                () -> one("Mood: " + Math.round(mc().player.getCurrentMood() * 100) + "%"));
        add(
                "sound_cache",
                "Sound cache",
                "Sound engine source and cache statistics.",
                "Sound",
                true,
                false,
                DebugOverlayManager::soundCache);
        add(
                "post_effects",
                "Post effects",
                "Currently applied post-processing effect.",
                "Renderer",
                true,
                false,
                () ->
                        one(
                                mc().gameRenderer.currentEffect() != null
                                        ? "Shader: " + mc().gameRenderer.currentEffect().getName()
                                        : "Shader: none"));
        add(
                "gpu_utilization",
                "GPU utilization",
                "Measured GPU frame utilization.",
                "Performance",
                true,
                false,
                () -> one(String.format(Locale.ROOT, "GPU: %.0f%%", mc().getGpuUtilization())));
        add(
                "simple_performance_impactors",
                "Performance impactors",
                "Render distance, graphics and simulation settings.",
                "Performance",
                true,
                false,
                () ->
                        one(
                                "D: "
                                        + mc().options.renderDistance().get()
                                        + " SD: "
                                        + mc().options.simulationDistance().get()
                                        + " "
                                        + mc().options.graphicsMode().get()));
        for (String kind : new String[] {"block", "fluid"})
            for (boolean tags : new boolean[] {false, true}) {
                String id = "looking_at_" + kind + "_" + (tags ? "tags" : "state");
                add(
                        id,
                        "Targeted " + kind + " " + (tags ? "tags" : "state"),
                        "Registry id, properties or tags of the targeted " + kind + ".",
                        "Target",
                        false,
                        true,
                        () -> target(kind.equals("fluid"), tags));
            }
        add(
                "looking_at_entity",
                "Targeted entity",
                "Entity type and identity under the crosshair.",
                "Target",
                false,
                true,
                () -> {
                    var e = targetEntity();
                    return e != null
                            ? List.of(
                                    "Targeted Entity: "
                                            + BuiltInRegistries.ENTITY_TYPE.getKey(e.getType()),
                                    e.getUUID().toString())
                            : List.of();
                });
        add(
                "looking_at_entity_tags",
                "Targeted entity tags",
                "Registry tags of the targeted entity type.",
                "Target",
                false,
                true,
                () -> {
                    var e = targetEntity();
                    return e != null
                            ? e.getType()
                                    .builtInRegistryHolder()
                                    .tags()
                                    .map(t -> "#" + t.location())
                                    .toList()
                            : List.of();
                });
        for (String flag :
                new String[] {
                    "entity_hitboxes",
                    "chunk_borders",
                    "3d_crosshair",
                    "chunk_section_paths",
                    "chunk_section_octree",
                    "visualize_water_levels",
                    "visualize_heightmap",
                    "visualize_collision_boxes",
                    "visualize_entity_supporting_blocks",
                    "visualize_block_light_levels",
                    "visualize_sky_light_levels",
                    "visualize_solid_faces",
                    "visualize_chunks_on_server",
                    "visualize_sky_light_sections",
                    "chunk_section_visibility"
                })
            add(
                    flag,
                    title(flag),
                    "Show " + title(flag).toLowerCase(Locale.ROOT) + " in the world.",
                    "Visualizations",
                    false,
                    false,
                    List::of);
        SodiumExtraClientMod.registerAll(
                (id, entry) ->
                        ENTRIES.put(
                                id,
                                new Entry(
                                        id,
                                        Component.translatable(id.getPath()),
                                        Component.translatable(id.getPath() + ".tooltip"),
                                        "Sodium Extra",
                                        entry.isAllowed(true),
                                        false,
                                        () -> {
                                            var lines = new ArrayList<String>();
                                            var priority = new ArrayList<String>();
                                            entry.display(
                                                    new DebugScreenDisplayer(lines, priority),
                                                    mc().level,
                                                    null,
                                                    null);
                                            priority.addAll(lines);
                                            return priority;
                                        })));
    }

    private static String memoryUsage(String label, java.lang.management.MemoryUsage usage) {
        return String.format(
                Locale.ROOT,
                "Memory (%s): i=%03dMiB u=%03dMiB c=%03dMiB m=%03dMiB",
                label,
                usage.getInit() / 1024 / 1024,
                usage.getUsed() / 1024 / 1024,
                usage.getCommitted() / 1024 / 1024,
                usage.getMax() / 1024 / 1024);
    }

    private static List<String> heightmaps() {
        var b = mc().getCameraEntity().blockPosition();
        var position = new net.minecraft.world.level.ChunkPos(b);
        var level = serverLevel();
        if (serverChunk == null || level != chunkLevel || !position.equals(chunkPosition)) {
            chunkLevel = level;
            chunkPosition = position;
            serverChunk =
                    level == null
                            ? java.util.concurrent.CompletableFuture.completedFuture(
                                    mc().level.getChunkAt(b))
                            : level.getChunkSource()
                                    .getChunkFuture(
                                            position.x,
                                            position.z,
                                            net.minecraft.world.level.chunk.ChunkStatus.FULL,
                                            false)
                                    .thenApply(
                                            value ->
                                                    value.left()
                                                            .map(
                                                                    chunk ->
                                                                            (net.minecraft.world
                                                                                            .level
                                                                                            .chunk
                                                                                            .LevelChunk)
                                                                                    chunk)
                                                            .orElse(null));
        }
        var client = mc().level.getChunkAt(b);
        var server = serverChunk.getNow(null);
        var out = new ArrayList<String>();
        for (var type : Heightmap.Types.values()) {
            if (type.sendToClient())
                out.add(
                        "Client "
                                + type.getSerializationKey()
                                + ": "
                                + client.getHeight(type, b.getX() & 15, b.getZ() & 15));
            if (type.keepAfterWorldgen())
                out.add(
                        "Server "
                                + type.getSerializationKey()
                                + ": "
                                + (server == null
                                        ? "??"
                                        : server.getHeight(type, b.getX() & 15, b.getZ() & 15)));
        }
        return out;
    }

    private static List<String> soundCache() {
        var engine =
                ((me.flashyreese.mods.sodiumextra.mixin.core.AccessorSoundManager)
                                mc().getSoundManager())
                        .nocturna$getEngine();
        var library =
                ((me.flashyreese.mods.sodiumextra.mixin.core.AccessorSoundEngine) engine)
                        .nocturna$getBuffers();
        var cache =
                ((me.flashyreese.mods.sodiumextra.mixin.core.AccessorSoundCache) library)
                        .nocturna$getCache();
        long bytes = 0;
        int loaded = 0, loading = 0, failed = 0;
        for (var future : new ArrayList<>(cache.values())) {
            if (!future.isDone()) {
                loading++;
                continue;
            }
            if (future.isCompletedExceptionally() || future.isCancelled()) {
                failed++;
                continue;
            }
            var buffer = future.getNow(null);
            if (buffer != null) {
                loaded++;
                bytes += ((SoundBytes) buffer).nocturna$bytes();
            }
        }
        return List.of(
                "Sound cache: "
                        + loaded
                        + " loaded, "
                        + loading
                        + " loading, "
                        + failed
                        + " failed",
                String.format(Locale.ROOT, "Sound cache memory: %.2f MiB", bytes / 1048576.0));
    }

    private static String title(String id) {
        String text = id.replace('_', ' ');
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private static net.minecraft.server.level.ServerLevel serverLevel() {
        var s = mc().getSingleplayerServer();
        return s != null ? s.getLevel(mc().level.dimension()) : null;
    }

    private static net.minecraft.world.entity.Entity targetEntity() {
        return net.minecraft.client.renderer.debug.DebugRenderer.getTargetedEntity(
                        mc().getCameraEntity(), 20)
                .orElse(null);
    }

    private static List<String> target(boolean fluid, boolean tags) {
        var hit = mc().getCameraEntity().pick(20, 0, fluid);
        if (!(hit instanceof BlockHitResult block)) return List.of();
        var pos = block.getBlockPos();
        var out = new ArrayList<String>();
        if (fluid) {
            var state = mc().level.getFluidState(pos);
            if (tags) state.getTags().forEach(t -> out.add("#" + t.location()));
            else {
                out.add("Targeted Fluid: " + pos.toShortString());
                out.add(BuiltInRegistries.FLUID.getKey(state.getType()).toString());
                state.getValues().forEach((k, v) -> out.add(k.getName() + ": " + v));
            }
        } else {
            var state = mc().level.getBlockState(pos);
            if (tags) state.getTags().forEach(t -> out.add("#" + t.location()));
            else {
                out.add("Targeted Block: " + pos.toShortString());
                out.add(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
                state.getValues().forEach((k, v) -> out.add(k.getName() + ": " + v));
            }
        }
        return out;
    }

    public static boolean enabled(String id) {
        state();
        return state.get(id.contains(":") ? id : "minecraft:" + id)
                        .visible(mc().options.renderDebug)
                && !mc().showOnlyReducedInfo();
    }

    private static void refresh() {
        state();
        cacheRevision();
        if (mc().level == null) return;
        var settings = SodiumExtraClientMod.options().extraSettings;
        boolean steady =
                settings.steadyDebugHud
                        && SodiumExtraClientMod.mixinConfig()
                                .getOptions()
                                .get("mixin.steady_debug_hud")
                                .isEnabled();
        long now = net.minecraft.Util.getMillis();
        if (steady && now <= nextRefresh) return;
        nextRefresh = now + settings.steadyDebugHudRefreshInterval * 50L;
        var l = new ArrayList<String>();
        var r = new ArrayList<String>();
        for (var entry : ENTRIES.values())
            if (state.get(entry.id.toString()).visible(mc().options.renderDebug)
                    && (entry.reducedAllowed || !mc().showOnlyReducedInfo())) {
                var destination = entry.right ? r : l;
                var lines = entry.text.get();
                if (entry.id
                        .toString()
                        .equals("sodium-extra:sodium-extra.option.light_updates_warning"))
                    destination.addAll(0, lines);
                else destination.addAll(lines);
            }
        left = l;
        right = r;
    }

    public static void invalidate() {
        nextRefresh = 0;
        revision++;
    }

    /** Permission and F3 transitions must take effect even while text values are frozen. */
    public static long cacheRevision() {
        var client = mc();
        boolean debug = client.options.renderDebug, reduced = client.showOnlyReducedInfo();
        if (hudLevel != client.level || hudDebug != debug || hudReduced != reduced) {
            hudLevel = client.level;
            hudDebug = debug;
            hudReduced = reduced;
            invalidate();
        }
        return revision;
    }

    public static void clearWorld() {
        chunkLevel = null;
        chunkPosition = null;
        serverChunk = null;
        hudLevel = null;
        left = right = List.of();
        invalidate();
        DebugVisuals.clearWorld();
    }

    public static List<String> left() {
        refresh();
        return new ArrayList<>(left);
    }

    public static List<String> right() {
        refresh();
        return new ArrayList<>(right);
    }

    public static void renderAlways(GuiGraphics graphics) {
        if (mc().level == null || mc().options.hideGui || mc().options.renderDebug) return;
        draw(graphics, left(), false);
        draw(graphics, right(), true);
    }

    private static void draw(GuiGraphics g, List<String> lines, boolean right) {
        int y = 2;
        for (String line : lines) {
            int x = right ? g.guiWidth() - mc().font.width(line) - 2 : 2;
            g.fill(
                    x - 1,
                    y - 1,
                    x + mc().font.width(line) + 1,
                    y + mc().font.lineHeight,
                    0x90505050);
            g.drawString(mc().font, line, x, y, 0xffe0e0e0, false);
            y += mc().font.lineHeight;
        }
    }
}
