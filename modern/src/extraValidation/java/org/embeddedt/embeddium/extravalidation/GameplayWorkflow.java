package org.embeddedt.embeddium.extravalidation;

import com.google.gson.*;
import com.mojang.blaze3d.systems.RenderSystem;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import me.flashyreese.mods.sodiumextra.client.render.PaniniProjection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import org.embeddedt.embeddium.impl.extra.compat.*;
import org.lwjgl.opengl.GL33C;

import java.nio.file.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Real play lifecycle: weather, F3/permissions, dimension transfer, disconnect, second world and
 * resource reload.
 */
public final class GameplayWorkflow {
    private int stage, settled, ticks;
    private boolean busy;
    private final long deadline = System.nanoTime() + 360_000_000_000L;
    private final List<String> passed = new ArrayList<>(), failed = new ArrayList<>();
    private CompletableFuture<Void> reload;
    private final Map<Path, byte[]> originals = new LinkedHashMap<>();

    public GameplayWorkflow() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    private interface Check {
        void run() throws Exception;
    }

    private void check(String name, Check check) {
        try {
            check.run();
            passed.add(name);
            System.out.println("PASS gameplay: " + name);
        } catch (Throwable e) {
            failed.add(name + ": " + e);
            System.out.println("REPRO gameplay: " + name + ": " + e);
            e.printStackTrace();
        }
    }

    private static Object field(Class<?> type, String name) throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private void create(Minecraft mc) {
        settled = 0;
        String name = "gameplay-review-" + UUID.randomUUID();
        mc.createWorldOpenFlows()
                .createFreshLevel(
                        name,
                        new LevelSettings(
                                name,
                                GameType.CREATIVE,
                                false,
                                Difficulty.PEACEFUL,
                                true,
                                new GameRules(),
                                WorldDataConfiguration.DEFAULT),
                        new WorldOptions(1024, false, false),
                        r ->
                                r.registryOrThrow(Registries.WORLD_PRESET)
                                        .getHolderOrThrow(WorldPresets.FLAT)
                                        .value()
                                        .createWorldDimensions());
    }

    private boolean worldReady(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.screen == null && ++settled >= 60;
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || busy) return;
        busy = true;
        try {
            ExtraValidation.require(
                    System.nanoTime() < deadline, "gameplay workflow timed out at " + stage);
            var mc = Minecraft.getInstance();
            if (++ticks % 100 == 0)
                System.out.println(
                        "Gameplay stage="
                                + stage
                                + " screen="
                                + (mc.screen == null ? "null" : mc.screen.getClass().getName())
                                + " world="
                                + (mc.level == null ? "null" : mc.level.dimension().location()));
            if (stage == 0
                    && (mc.screen instanceof TitleScreen
                            || mc.screen instanceof AccessibilityOnboardingScreen)) {
                for (var path :
                        List.of(
                                Path.of("debug-profile.json"),
                                Path.of("config/sodium-extra-options.json")))
                    originals.put(path, Files.exists(path) ? Files.readAllBytes(path) : null);
                ShaderPackFixture.prepare();
                mc.options.renderDistance().set(3);
                mc.options.simulationDistance().set(5);
                mc.options.pauseOnLostFocus = false;
                stage = 1;
                create(mc);
            } else if (stage == 1 && worldReady(mc)) {
                check(
                        "instanced weather receives protected fog",
                        () -> WeatherFogParity.verify(mc));
                check(
                        "Greenlight fingerprints do not retain the connection",
                        () -> {
                            me.flashyreese.mods.greenlight.feature.Greenlight.getGrantedFeatures();
                            var manager =
                                    Class.forName(
                                            "me.flashyreese.mods.greenlight.feature.ClientFeaturePolicyManager");
                            for (var state : (List<?>) field(manager, "SOURCES")) {
                                var f = state.getClass().getDeclaredField("fingerprint");
                                f.setAccessible(true);
                                Object fingerprint = f.get(state);
                                if (fingerprint != null
                                        && fingerprint
                                                .getClass()
                                                .getSimpleName()
                                                .equals("NativeFingerprint")) {
                                    var c = fingerprint.getClass().getDeclaredField("connection");
                                    c.setAccessible(true);
                                    ExtraValidation.require(
                                            c.get(fingerprint) != mc.getConnection(),
                                            "policy cache still owns the live connection");
                                    return;
                                }
                            }
                            throw new AssertionError(
                                    "Greenlight native policy source did not refresh");
                        });
                check(
                        "shader pack fog uniforms retain environment distances",
                        () -> {
                            RenderSystem.setShaderFogStart(57.6F);
                            RenderSystem.setShaderFogEnd(64);
                            ExtraValidation.require(
                                    net.irisshaders.iris.uniforms.VanillaEnvironment.fogStart() == 0
                                            && net.irisshaders.iris.uniforms.VanillaEnvironment
                                                            .fogEnd()
                                                    == 1,
                                    "shader packs receive render fog instead of protected"
                                            + " environment fog");
                        });
                check(
                        "HUD accepts fractional wheel input",
                        () -> {
                            var screen = new ExtraDebugOptionsScreen();
                            mc.setScreen(screen);
                            try {
                                for (int i = 0; i < 4; i++)
                                    screen.mouseScrolled(screen.width / 2.0, 100, -.25);
                                var f = ExtraDebugOptionsScreen.class.getDeclaredField("scroll");
                                f.setAccessible(true);
                                ExtraValidation.require(
                                        f.getInt(screen) == 1,
                                        "fractional scrolling was discarded");
                            } finally {
                                mc.setScreen(null);
                            }
                        });
                check(
                        "HUD visibility changes preserve keyboard focus",
                        () -> {
                            var screen = new ExtraDebugOptionsScreen();
                            mc.setScreen(screen);
                            try {
                                var button =
                                        screen.children().stream()
                                                .filter(
                                                        w ->
                                                                w
                                                                                instanceof
                                                                                net.minecraft.client
                                                                                                .gui
                                                                                                .components
                                                                                                .Button
                                                                                        b
                                                                        && b.getTooltip() != null)
                                                .map(
                                                        w ->
                                                                (net.minecraft.client.gui.components
                                                                                .Button)
                                                                        w)
                                                .findFirst()
                                                .orElseThrow();
                                screen.setFocused(button);
                                button.onPress();
                                ExtraValidation.require(
                                        screen.children().contains(screen.getFocused()),
                                        "focused control was removed while activating it");
                            } finally {
                                mc.setScreen(null);
                                DebugOverlayManager.state()
                                        .loadProfile(DebugOverlayState.Profile.DEFAULT);
                                DebugOverlayManager.invalidate();
                            }
                        });
                check(
                        "HUD updates permissions while the screen stays open",
                        () -> {
                            var screen = new ExtraDebugOptionsScreen();
                            boolean reduced = mc.options.reducedDebugInfo().get();
                            mc.options.reducedDebugInfo().set(false);
                            mc.setScreen(screen);
                            try {
                                var search =
                                        screen.children().stream()
                                                .filter(
                                                        w ->
                                                                w
                                                                        instanceof
                                                                        net.minecraft.client.gui
                                                                                .components.EditBox)
                                                .map(
                                                        w ->
                                                                (net.minecraft.client.gui.components
                                                                                .EditBox)
                                                                        w)
                                                .findFirst()
                                                .orElseThrow();
                                search.setValue("minecraft:player_position");
                                mc.options.reducedDebugInfo().set(true);
                                screen.tick();
                                ExtraValidation.require(
                                        screen.children().stream()
                                                .filter(
                                                        w ->
                                                                w
                                                                                instanceof
                                                                                net.minecraft.client
                                                                                                .gui
                                                                                                .components
                                                                                                .Button
                                                                                        b
                                                                        && b.getTooltip() != null)
                                                .noneMatch(
                                                        w ->
                                                                ((net.minecraft.client.gui
                                                                                        .components
                                                                                        .Button)
                                                                                w)
                                                                        .active),
                                        "open HUD screen kept forbidden controls enabled");
                            } finally {
                                mc.options.reducedDebugInfo().set(reduced);
                                mc.setScreen(null);
                            }
                        });
                check(
                        "F3 off immediately removes overlay-only text",
                        () -> {
                            var settings = SodiumExtraClientMod.options().extraSettings;
                            int interval = settings.steadyDebugHudRefreshInterval;
                            boolean steady = settings.steadyDebugHud;
                            try {
                                settings.steadyDebugHud = true;
                                settings.steadyDebugHudRefreshInterval = 3600;
                                DebugOverlayManager.state()
                                        .loadProfile(DebugOverlayState.Profile.DEFAULT);
                                DebugOverlayManager.invalidate();
                                mc.options.renderDebug = true;
                                ExtraValidation.require(
                                        !DebugOverlayManager.left().isEmpty(),
                                        "debug fixture empty");
                                mc.options.renderDebug = false;
                                ExtraValidation.require(
                                        DebugOverlayManager.left().isEmpty(),
                                        "overlay-only text leaked outside F3");
                            } finally {
                                settings.steadyDebugHud = steady;
                                settings.steadyDebugHudRefreshInterval = interval;
                                mc.options.renderDebug = false;
                                DebugOverlayManager.invalidate();
                            }
                        });
                check(
                        "reduced-debug permissions invalidate cached coordinates",
                        () -> {
                            var settings = SodiumExtraClientMod.options().extraSettings;
                            int interval = settings.steadyDebugHudRefreshInterval;
                            boolean reduced = mc.options.reducedDebugInfo().get();
                            try {
                                settings.steadyDebugHudRefreshInterval = 3600;
                                DebugOverlayManager.state()
                                        .set(
                                                "minecraft:player_position",
                                                DebugOverlayState.Visibility.ALWAYS);
                                DebugOverlayManager.invalidate();
                                ExtraValidation.require(
                                        !DebugOverlayManager.left().isEmpty(),
                                        "coordinate fixture empty");
                                mc.options.reducedDebugInfo().set(true);
                                ExtraValidation.require(
                                        DebugOverlayManager.left().isEmpty(),
                                        "cached coordinates bypassed reduced-debug permissions");
                            } finally {
                                settings.steadyDebugHudRefreshInterval = interval;
                                mc.options.reducedDebugInfo().set(reduced);
                                DebugOverlayManager.state()
                                        .loadProfile(DebugOverlayState.Profile.DEFAULT);
                                DebugOverlayManager.invalidate();
                            }
                        });
                DebugOverlayManager.entries().stream()
                        .filter(e -> e.id().getPath().equals("heightmap"))
                        .findFirst()
                        .orElseThrow()
                        .text()
                        .get();
                ExtraValidation.require(
                        field(DebugOverlayManager.class, "chunkLevel") != null,
                        "world retention fixture not primed");
                stage = 2;
                settled = 0;
                var server = mc.getSingleplayerServer();
                var id = mc.player.getUUID();
                server.execute(
                        () -> {
                            var p = server.getPlayerList().getPlayer(id);
                            p.teleportTo(server.getLevel(Level.NETHER), 0, 90, 0, Set.of(), 0, 0);
                        });
            } else if (stage == 2
                    && mc.level != null
                    && mc.level.dimension() == Level.NETHER
                    && worldReady(mc)) {
                check(
                        "dimension transfer releases previous server chunk",
                        () ->
                                ExtraValidation.require(
                                        field(DebugOverlayManager.class, "chunkLevel") == null
                                                && field(DebugOverlayManager.class, "serverChunk")
                                                        == null,
                                        "old dimension remains strongly referenced"));
                stage = 3;
                settled = 0;
                mc.level.disconnect();
                mc.clearLevel(new TitleScreen());
            } else if (stage == 3
                    && mc.level == null
                    && (mc.screen instanceof TitleScreen
                            || mc.screen
                                    instanceof
                                    net.minecraft.client.gui.screens.worldselection
                                            .SelectWorldScreen)
                    && ++settled >= 20) {
                check(
                        "disconnect releases world and visibility state",
                        () ->
                                ExtraValidation.require(
                                        field(DebugOverlayManager.class, "chunkLevel") == null
                                                && field(DebugOverlayManager.class, "serverChunk")
                                                        == null
                                                && !DebugVisuals.captureRequested,
                                        "disconnected world remains in debug caches"));
                stage = 4;
                create(mc);
            } else if (stage == 4 && worldReady(mc)) {
                check(
                        "second world renders terrain and native weather",
                        () -> {
                            ExtraValidation.require(
                                    mc.levelRenderer.isChunkCompiled(
                                            mc.player.blockPosition().below()),
                                    "second world terrain absent");
                            WeatherFogParity.verify(mc);
                        });
                ResourceGraphParity.begin(mc);
                stage = 5;
            } else if (stage == 5 && ResourceGraphParity.ready()) {
                var path =
                        mc.gameDirectory
                                .toPath()
                                .resolve(
                                        "resourcepacks/nocturna-extra-parity/assets/sodium-extra/post_effect/panini.json");
                var json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
                json.getAsJsonArray("passes")
                        .get(0)
                        .getAsJsonObject()
                        .addProperty("output", "nocturna:missing-target");
                Files.writeString(path, json.toString());
                reload = mc.reloadResourcePacks();
                stage = 6;
            } else if (stage == 6 && reload.isDone() && mc.getOverlay() == null) {
                check(
                        "invalid Panini graph safely bypasses rendering",
                        () -> {
                            var settings = SodiumExtraClientMod.options().extraSettings;
                            settings.paniniProjection = true;
                            settings.paniniProjectionStrength = 25;
                            try {
                                PaniniProjection.INSTANCE.begin(
                                        mc,
                                        mc.getMainRenderTarget(),
                                        mc.gameRenderer.getProjectionMatrix(70));
                                PaniniProjection.INSTANCE.finish(mc, 0);
                                ExtraValidation.require(
                                        !PaniniProjection.INSTANCE.isRenderingWorld(),
                                        "invalid graph started world redirection");
                                var flag =
                                        PaniniProjection.class.getDeclaredField(
                                                "shaderUnavailable");
                                flag.setAccessible(true);
                                ExtraValidation.require(
                                        flag.getBoolean(PaniniProjection.INSTANCE),
                                        "bad shader was not negatively cached");
                                PaniniProjection.INSTANCE.begin(
                                        mc,
                                        mc.getMainRenderTarget(),
                                        mc.gameRenderer.getProjectionMatrix(70));
                                ExtraValidation.require(
                                        !PaniniProjection.INSTANCE.isRenderingWorld(),
                                        "bad shader was retried before reload");
                            } finally {
                                PaniniProjection.INSTANCE.abort();
                                mc.getMainRenderTarget().bindWrite(true);
                            }
                        });
                ResourceGraphParity.restore(mc);
                stage = 7;
            } else if (stage == 7 && ResourceGraphParity.ready() && mc.getOverlay() == null) {
                check(
                        "valid resource reload recovers Panini",
                        () -> {
                            var settings = SodiumExtraClientMod.options().extraSettings;
                            settings.paniniProjection = true;
                            settings.paniniProjectionStrength = 25;
                            try {
                                PaniniProjection.INSTANCE.begin(
                                        mc,
                                        mc.getMainRenderTarget(),
                                        mc.gameRenderer.getProjectionMatrix(70));
                                ExtraValidation.require(
                                        PaniniProjection.INSTANCE.isRenderingWorld(),
                                        "valid shader remained disabled after reload");
                                PaniniProjection.INSTANCE.finish(mc, 0);
                                ExtraValidation.require(
                                        GL33C.glGetError() == GL33C.GL_NO_ERROR,
                                        "recovered Panini generated a GL error");
                            } finally {
                                settings.paniniProjection = false;
                                PaniniProjection.INSTANCE.close();
                                mc.getMainRenderTarget().bindWrite(true);
                            }
                        });
                FogShaderParity.verify();
                finish(mc);
            }
        } catch (Throwable e) {
            failed.add("workflow: " + e);
            e.printStackTrace();
            try {
                finish(Minecraft.getInstance());
            } catch (Exception ignored) {
            }
            Runtime.getRuntime().halt(1);
        } finally {
            busy = false;
        }
    }

    private void finish(Minecraft mc) throws Exception {
        stage = 99;
        ShaderPackFixture.restore();
        for (var entry : originals.entrySet())
            if (entry.getValue() == null) Files.deleteIfExists(entry.getKey());
            else Files.write(entry.getKey(), entry.getValue());
        Files.writeString(
                Path.of("gameplay-review.json"),
                new GsonBuilder()
                        .setPrettyPrinting()
                        .create()
                        .toJson(Map.of("passed", passed, "failed", failed)));
        if (!failed.isEmpty()) {
            System.out.println("FAIL gameplay workflow: " + failed.size() + " cases");
            Runtime.getRuntime().halt(1);
            return;
        }
        System.out.println(
                "PASS gameplay workflow: "
                        + passed.size()
                        + " cases; Java "
                        + System.getProperty("java.specification.version"));
        mc.stop();
    }
}
