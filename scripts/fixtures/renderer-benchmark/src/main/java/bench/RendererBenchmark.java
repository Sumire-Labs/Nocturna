package bench;

import com.google.gson.GsonBuilder;

import net.minecraft.client.*;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.*;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.lwjgl.glfw.GLFW;
import org.lwjgl.opengl.GL11C;

import java.lang.management.*;
import java.lang.reflect.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;

@Mod("renderer_benchmark")
public class RendererBenchmark {
    private static RendererBenchmark instance;
    private final Path output = Path.of(System.getProperty("bench.result"));
    private final boolean prepare = Boolean.getBoolean("bench.prepare");
    private final String label = System.getProperty("bench.label");
    private final long warmNs =
            Long.parseLong(System.getProperty("bench.warmup", "15")) * 1_000_000_000L;
    private final long measureNs =
            Long.parseLong(System.getProperty("bench.seconds", "30")) * 1_000_000_000L;
    private final long deadline = System.nanoTime() + 1_500_000_000_000L;
    private final Map<String, Object> report = new LinkedHashMap<>();
    private final List<Map<String, Object>> results = new ArrayList<>();
    private final List<String> scenarios =
            new ArrayList<>(List.of("terrain", "items", "precipitation", "travel"));
    private final long[] durations = new long[300_000], timestamps = new long[300_000];
    private boolean launched, bootstrapped, ready, stopping, pending, phaseReady;
    private int phase = -1, sampleCount;
    private long phaseStart, lastFrame, recordStart, gcCount, gcMs, recordUptime, recordUnixMillis;
    private Map<String, Object> clockStart, clockEnd;
    private String chunksStart;
    private long queryNotifications, queryUpdates, finalQueryNotifications, finalQueryUpdates;
    private double cameraY;
    private int pendingSteps;
    private static final double X = 8.5, Z = 8.5;

    public RendererBenchmark() {
        instance = this;
        MinecraftForge.EVENT_BUS.register(this);
    }

    public static void frame() {
        if (instance != null) instance.onFrame();
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || stopping) return;
        var mc = Minecraft.getInstance();
        try {
            if (System.nanoTime() > deadline) throw new AssertionError("Benchmark timeout");
            if (!launched
                    && (mc.screen instanceof TitleScreen
                            || mc.screen instanceof AccessibilityOnboardingScreen)) {
                launched = true;
                Files.createDirectories(output.getParent());
                setupOptions(mc);
                report.put("label", label);
                report.put("queryProbeEnabled", Boolean.getBoolean("bench.queryProbe"));
                report.put("controlledAnimation", System.getProperty("bench.animationStart"));
                report.put("processId", ProcessHandle.current().pid());
                report.put("startedAt", java.time.Instant.now().toString());
                report.put("java", System.getProperty("java.version"));
                report.put("cpu", System.getenv("PROCESSOR_IDENTIFIER"));
                report.put("gpu", GL11C.glGetString(GL11C.GL_RENDERER));
                report.put("glVersion", GL11C.glGetString(GL11C.GL_VERSION));
                report.put(
                        "glContextFlags",
                        GL11C.glGetInteger(org.lwjgl.opengl.GL30C.GL_CONTEXT_FLAGS));
                report.put(
                        "glKhrNoErrorSupported",
                        org.lwjgl.opengl.GL.getCapabilities().GL_KHR_no_error);
                report.put(
                        "mods",
                        net.minecraftforge.fml.ModList.get().getMods().stream()
                                .map(m -> m.getModId() + ":" + m.getVersion())
                                .toList());
                report.put("artifact", System.getProperty("bench.artifact"));
                report.put("artifactSha256", System.getProperty("bench.artifactSha256"));
                report.put("sourceCommit", System.getProperty("bench.sourceCommit"));
                report.put("memoryMax", Runtime.getRuntime().maxMemory());
                report.put(
                        "resolution",
                        List.of(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
                report.put("gcLog", "gc.log");
                report.put("renderDistance", 12);
                report.put("simulationDistance", 5);
                report.put("seed", 20261117);
                report.put("warmupSeconds", warmNs / 1e9);
                report.put("measureSeconds", measureNs / 1e9);
                String shader = System.getProperty("bench.shader");
                if (shader != null && !shader.isBlank()) scenarios.add("shader");
                if (Boolean.getBoolean("bench.shaderOnly")) {
                    scenarios.clear();
                    scenarios.add("shader");
                }
                disableShader();
                String world = System.getProperty("bench.world", "benchmark");
                if (prepare) {
                    var settings =
                            new LevelSettings(
                                    world,
                                    GameType.CREATIVE,
                                    false,
                                    Difficulty.PEACEFUL,
                                    true,
                                    new GameRules(),
                                    WorldDataConfiguration.DEFAULT);
                    mc.forceSetScreen(
                            new GenericDirtMessageScreen(
                                    Component.literal("Preparing benchmark world")));
                    mc.createWorldOpenFlows()
                            .createFreshLevel(
                                    world,
                                    settings,
                                    new WorldOptions(20261117, false, false),
                                    r ->
                                            r.registryOrThrow(Registries.WORLD_PRESET)
                                                    .getHolderOrThrow(WorldPresets.NORMAL)
                                                    .value()
                                                    .createWorldDimensions());
                } else mc.createWorldOpenFlows().loadLevel(new TitleScreen(), world);
            }
            if (mc.level == null || mc.player == null || mc.screen != null) return;
            if (!bootstrapped) {
                bootstrapped = true;
                bootstrap(mc);
                return;
            }
            if (!ready) return;
            if (phase < 0) {
                beginPhase(mc);
                return;
            }
            if (pending) {
                pending = false;
                finishPhase(mc);
                return;
            }
            double targetX = X;
            if (scenarios.get(phase).equals("travel") && recordStart != 0)
                targetX += Math.min(480, Math.max(0, (System.nanoTime() - recordStart) / 1e9) * 16);
            if (Math.abs(mc.player.getX() - targetX) > 0.00001
                    || Math.abs(mc.player.getY() - cameraY) > 0.00001
                    || Math.abs(mc.player.getZ() - Z) > 0.00001)
                mc.player.setPos(targetX, cameraY, Z);
            if (!scenarios.get(phase).equals("travel")) {
                mc.player.xo = targetX;
                mc.player.yo = cameraY;
                mc.player.zo = Z;
            }
            mc.player.setYRot(-90);
            mc.player.setXRot(20);
            mc.player.setDeltaMovement(0, 0, 0);
            for (var key :
                    new net.minecraft.client.KeyMapping[] {
                        mc.options.keyUp,
                        mc.options.keyDown,
                        mc.options.keyLeft,
                        mc.options.keyRight,
                        mc.options.keyJump,
                        mc.options.keyShift
                    }) key.setDown(false);
        } catch (Throwable t) {
            fail(mc, t);
        }
    }

    public static boolean cameraLocked() {
        return instance != null && instance.ready && !instance.stopping;
    }

    private void setupOptions(Minecraft mc) {
        mc.options.renderDistance().set(12);
        mc.options.simulationDistance().set(5);
        mc.options.enableVsync().set(false);
        mc.options.framerateLimit().set(260);
        mc.options.pauseOnLostFocus = false;
        mc.options.hideGui = true;
        mc.options.graphicsMode().set(GraphicsStatus.FANCY);
        mc.options.cloudStatus().set(CloudStatus.OFF);
        mc.options.fov().set(70);
        mc.options.bobView().set(false);
        mc.options.mipmapLevels().set(4);
        GLFW.glfwSetWindowSize(mc.getWindow().getWindow(), 1280, 720);
        GLFW.glfwSwapInterval(0);
        mc.options.save();
    }

    private void bootstrap(Minecraft mc) {
        mc.getSingleplayerServer()
                .execute(
                        () -> {
                            try {
                                var server = mc.getSingleplayerServer();
                                var level = server.overworld();
                                level.getGameRules()
                                        .getRule(GameRules.RULE_DAYLIGHT)
                                        .set(false, server);
                                level.getGameRules()
                                        .getRule(GameRules.RULE_WEATHER_CYCLE)
                                        .set(false, server);
                                level.getGameRules()
                                        .getRule(GameRules.RULE_DOMOBSPAWNING)
                                        .set(false, server);
                                level.getGameRules()
                                        .getRule(GameRules.RULE_RANDOMTICKING)
                                        .set(0, server);
                                level.setDayTime(6000);
                                level.setWeatherParameters(60000, 0, false, false);
                                var player = server.getPlayerList().getPlayers().get(0);
                                player.setGameMode(GameType.SPECTATOR);
                                if (prepare) {
                                    int done = 0;
                                    for (int x = -13; x <= 45; x++)
                                        for (int z = -13; z <= 13; z++) {
                                            level.getChunk(x, z);
                                            if (++done % 150 == 0)
                                                System.out.println(
                                                        "BENCH PREGEN " + done + "/1593");
                                        }
                                    int highest = 64;
                                    for (int x = 0; x <= 512; x += 8)
                                        highest =
                                                Math.max(
                                                        highest,
                                                        level.getHeight(
                                                                Heightmap.Types
                                                                        .MOTION_BLOCKING_NO_LEAVES,
                                                                x,
                                                                8));
                                    var metadata = new LinkedHashMap<String, Object>();
                                    metadata.put("cameraY", highest + 24);
                                    metadata.put("highestRouteTerrain", highest);
                                    metadata.put(
                                            "biome",
                                            level.getBiome(new BlockPos(8, highest, 8))
                                                    .unwrapKey()
                                                    .orElseThrow()
                                                    .location()
                                                    .toString());
                                    Files.writeString(
                                            output,
                                            new GsonBuilder()
                                                    .setPrettyPrinting()
                                                    .create()
                                                    .toJson(metadata));
                                    server.saveEverything(true, true, true);
                                    mc.execute(
                                            () -> {
                                                stopping = true;
                                                mc.stop();
                                            });
                                } else {
                                    var metadata =
                                            com.google.gson.JsonParser.parseString(
                                                            Files.readString(
                                                                    Path.of(
                                                                            System.getProperty(
                                                                                    "bench.worldMeta"))))
                                                    .getAsJsonObject();
                                    cameraY = metadata.get("cameraY").getAsDouble();
                                    player.setYRot(-90);
                                    player.setXRot(20);
                                    player.teleportTo(X, cameraY, Z);
                                    mc.execute(
                                            () -> {
                                                mc.player.setYRot(-90);
                                                mc.player.setXRot(20);
                                                ready = true;
                                            });
                                }
                            } catch (Throwable t) {
                                mc.execute(() -> fail(mc, t));
                            }
                        });
    }

    private void beginPhase(Minecraft mc) throws Exception {
        phase++;
        if (phase >= scenarios.size()) {
            report.put("results", results);
            report.put("success", true);
            report.put("finishedAt", java.time.Instant.now().toString());
            write();
            stopping = true;
            mc.stop();
            return;
        }
        phaseReady = false;
        String scenario = scenarios.get(phase);
        if (scenario.equals("shader")) enableShader(Path.of(System.getProperty("bench.shader")));
        boolean rain = scenario.equals("precipitation");
        mc.level.setRainLevel(rain ? 1 : 0);
        mc.getSingleplayerServer()
                .execute(
                        () -> {
                            try {
                                var s = mc.getSingleplayerServer();
                                s.overworld()
                                        .setWeatherParameters(rain ? 0 : 60000, 60000, rain, false);
                                var toRemove = new ArrayList<net.minecraft.world.entity.Entity>();
                                for (var entity : s.overworld().getAllEntities())
                                    if (entity
                                            instanceof net.minecraft.world.entity.item.ItemEntity)
                                        toRemove.add(entity);
                                for (var entity : toRemove) entity.discard();
                                if (scenario.equals("items"))
                                    for (int ix = 0; ix < 20; ix++)
                                        for (int iz = 0; iz < 20; iz++) {
                                            var item =
                                                    new net.minecraft.world.entity.item.ItemEntity(
                                                            s.overworld(),
                                                            24 + ix * 1.5,
                                                            cameraY - 12,
                                                            -6 + iz * 1.5,
                                                            new net.minecraft.world.item.ItemStack(
                                                                    net.minecraft.world.item.Items
                                                                            .DIAMOND,
                                                                    64));
                                            item.setNeverPickUp();
                                            item.setUnlimitedLifetime();
                                            item.setNoGravity(true);
                                            item.setDeltaMovement(0, 0, 0);
                                            s.overworld().addFreshEntity(item);
                                        }
                                var p = s.getPlayerList().getPlayers().get(0);
                                p.setYRot(-90);
                                p.setXRot(20);
                                p.teleportTo(X, cameraY, Z);
                                mc.execute(
                                        () -> {
                                            phaseStart = System.nanoTime();
                                            lastFrame = 0;
                                            phaseReady = true;
                                        });
                            } catch (Throwable t) {
                                mc.execute(() -> fail(mc, t));
                            }
                        });
        mc.player.setYRot(-90);
        mc.player.setXRot(20);
        mc.player.setDeltaMovement(0, 0, 0);
        sampleCount = 0;
        recordStart = 0;
        lastFrame = 0;
        phaseStart = Long.MAX_VALUE;
        gcCount = gcCount();
        gcMs = gcMs();
        if (scenario.equals("shader")) ShaderAnimationClock.begin();
        System.out.println("BENCH WARMUP " + label + " " + scenario);
    }

    private void onFrame() {
        if (prepare || !ready || stopping || phase < 0 || pending || !phaseReady) return;
        long now = System.nanoTime();
        if (now - phaseStart < warmNs) {
            lastFrame = now;
            return;
        }
        if (recordStart == 0) {
            System.out.println("BENCH MEASURE " + label + " " + scenarios.get(phase));
            gcCount = gcCount();
            gcMs = gcMs();
            recordUptime = ManagementFactory.getRuntimeMXBean().getUptime();
            recordUnixMillis = System.currentTimeMillis();
            clockStart = shaderClock();
            chunksStart = Minecraft.getInstance().levelRenderer.getChunkStatistics();
            queryNotifications = QueryProbe.notifications;
            queryUpdates = QueryProbe.updates;
            recordStart = System.nanoTime();
            lastFrame = recordStart;
            return;
        }
        if (sampleCount >= durations.length) {
            fail(
                    Minecraft.getInstance(),
                    new IllegalStateException(
                            "Frame recorder capacity exceeded; shorten the recording"));
            return;
        }
        durations[sampleCount] = now - lastFrame;
        timestamps[sampleCount] = now - recordStart;
        sampleCount++;
        lastFrame = now;
        if (now - recordStart >= measureNs) {
            finalQueryNotifications = QueryProbe.notifications;
            finalQueryUpdates = QueryProbe.updates;
            clockEnd = shaderClock();
            pending = true;
        }
    }

    private void finishPhase(Minecraft mc) throws Exception {
        String scenario = scenarios.get(phase);
        long[] sorted = Arrays.copyOf(durations, sampleCount);
        Arrays.sort(sorted);
        long sum = 0;
        int s33 = 0, s50 = 0, s100 = 0;
        var csv = new StringBuilder("time_ns,frame_ns\n");
        for (int i = 0; i < sampleCount; i++) {
            long t = durations[i];
            sum += t;
            if (t > 33_333_333) s33++;
            if (t > 50_000_000) s50++;
            if (t > 100_000_000) s100++;
            csv.append(timestamps[i]).append(',').append(t).append('\n');
        }
        Files.writeString(
                output.resolveSibling(
                        output.getFileName().toString().replace(".json", "-" + scenario + ".csv")),
                csv);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("scenario", scenario);
        r.put("shaderClockStart", clockStart);
        r.put("shaderClockEnd", clockEnd);
        r.put("chunksStart", chunksStart);
        r.put("resolution", List.of(mc.getWindow().getWidth(), mc.getWindow().getHeight()));
        r.put("frames", sampleCount);
        r.put("seconds", sum / 1e9);
        r.put("avgFps", sampleCount / (sum / 1e9));
        r.put("meanMs", sum / 1e6 / sampleCount);
        r.put("p50Ms", quantile(sorted, .5) / 1e6);
        r.put("p95Ms", quantile(sorted, .95) / 1e6);
        r.put("p99Ms", quantile(sorted, .99) / 1e6);
        r.put("p999Ms", quantile(sorted, .999) / 1e6);
        r.put("jvmStartUptimeMs", recordUptime);
        r.put("measurementStartUtc", java.time.Instant.ofEpochMilli(recordUnixMillis).toString());
        r.put("maxMs", sorted[sorted.length - 1] / 1e6);
        r.put("over33ms", s33);
        r.put("over50ms", s50);
        r.put("over100ms", s100);
        r.put(
                "notificationQueriesPerFrame",
                (finalQueryNotifications - queryNotifications) / (double) sampleCount);
        r.put("updateQueriesPerFrame", (finalQueryUpdates - queryUpdates) / (double) sampleCount);
        r.put("gcCount", gcCount() - gcCount);
        r.put("gcTimeMs", gcMs() - gcMs);
        int items = 0;
        for (var e : mc.level.entitiesForRendering())
            if (e instanceof net.minecraft.world.entity.item.ItemEntity) items++;
        r.put("itemEntities", items);
        r.put("rainStrength", mc.level.getRainLevel(1));
        r.put("serverMspt", mc.getSingleplayerServer().getAverageTickTime());
        r.put("chunks", mc.levelRenderer.getChunkStatistics());
        r.put("loadedChunks", mc.level.getChunkSource().getLoadedChunksCount());
        r.put(
                "camera",
                List.of(
                        mc.gameRenderer.getMainCamera().getPosition().x,
                        mc.gameRenderer.getMainCamera().getPosition().y,
                        mc.gameRenderer.getMainCamera().getPosition().z));
        results.add(r);
        System.out.println("BENCH RESULT " + new GsonBuilder().create().toJson(r));
        try (var image = Screenshot.takeScreenshot(mc.getMainRenderTarget())) {
            image.writeToFile(
                    output.resolveSibling(
                            output.getFileName()
                                    .toString()
                                    .replace(".json", "-" + scenario + ".png")));
        }
        write();
        beginPhase(mc);
    }

    private static Map<String, Object> shaderClock() {
        var out = new LinkedHashMap<String, Object>();
        try {
            var c = Class.forName("net.irisshaders.iris.uniforms.SystemTimeUniforms");
            var timer = c.getField("TIMER").get(null);
            out.put(
                    "frameTimeCounter",
                    timer.getClass().getMethod("getFrameTimeCounter").invoke(timer));
            out.put(
                    "frameCounter",
                    ((java.util.function.IntSupplier) c.getField("COUNTER").get(null)).getAsInt());
        } catch (Exception ignored) {
        }
        return out;
    }

    private static long quantile(long[] a, double p) {
        return a[Math.min(a.length - 1, (int) Math.ceil(a.length * p) - 1)];
    }

    private static long gcCount() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(b -> Math.max(0, b.getCollectionCount()))
                .sum();
    }

    private static long gcMs() {
        return ManagementFactory.getGarbageCollectorMXBeans().stream()
                .mapToLong(b -> Math.max(0, b.getCollectionTime()))
                .sum();
    }

    private static Class<?> iris() {
        for (String n : new String[] {"net.irisshaders.iris.Iris", "net.coderbot.iris.Iris"})
            try {
                return Class.forName(n);
            } catch (ClassNotFoundException ignored) {
            }
        return null;
    }

    private static void disableShader() throws Exception {
        var c = iris();
        if (c == null) return;
        var config = c.getMethod("getIrisConfig").invoke(null);
        config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, false);
        config.getClass().getMethod("save").invoke(config);
        c.getMethod("reload").invoke(null);
    }

    private static void enableShader(Path pack) throws Exception {
        var c = iris();
        if (c == null) throw new AssertionError("No shader runtime");
        var dir = (Path) c.getMethod("getShaderpacksDirectory").invoke(null);
        Files.createDirectories(dir);
        Files.copy(pack, dir.resolve(pack.getFileName()), StandardCopyOption.REPLACE_EXISTING);
        var config = c.getMethod("getIrisConfig").invoke(null);
        config.getClass()
                .getMethod("setShaderPackName", String.class)
                .invoke(config, pack.getFileName().toString());
        config.getClass().getMethod("setShadersEnabled", boolean.class).invoke(config, true);
        config.getClass().getMethod("save").invoke(config);
        c.getMethod("reload").invoke(null);
        var pm = c.getMethod("getPipelineManager").invoke(null);
        var pipeline = pm.getClass().getMethod("getPipelineNullable").invoke(pm);
        if (pipeline == null || pipeline.getClass().getSimpleName().contains("Vanilla"))
            throw new AssertionError("Shader fallback");
    }

    private void fail(Minecraft mc, Throwable t) {
        t.printStackTrace();
        report.put("success", false);
        report.put("error", t.toString());
        report.put("results", results);
        try {
            write();
        } catch (Exception ignored) {
        }
        stopping = true;
        mc.stop();
    }

    private void write() throws Exception {
        Files.createDirectories(output.getParent());
        Files.writeString(output, new GsonBuilder().setPrettyPrinting().create().toJson(report));
    }
}
