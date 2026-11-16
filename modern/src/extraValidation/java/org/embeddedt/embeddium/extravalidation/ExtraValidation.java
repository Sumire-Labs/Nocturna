package org.embeddedt.embeddium.extravalidation;

import me.flashyreese.mods.sodiumextra.client.SodiumExtraClientMod;
import me.flashyreese.mods.sodiumextra.client.render.PaniniProjection;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.*;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import org.embeddedt.embeddium.impl.extra.compat.config.structure.ExtraOption;
import org.lwjgl.opengl.GL33C;

import java.nio.file.*;
import java.util.*;

@Mod("nocturna_extra_validation")
public class ExtraValidation {
    private int stage, ticks, settled;
    private final long deadline = System.nanoTime() + 240_000_000_000L;
    private final Path config =
            net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR
                    .get()
                    .resolve("sodium-extra-options.json");
    private byte[] originalConfig;
    private boolean hadConfig;
    private final List<ExtraOption<?>> options = new ArrayList<>();
    private int projectedFrames;

    public ExtraValidation() {
        MinecraftForge.EVENT_BUS.register(this);
    }

    static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private void gui(Minecraft mc) {
        var config =
                me.flashyreese.mods.reeses_sodium_options.client.config.ReeseSodiumOptionsConfig
                        .config();
        boolean enabled = config.isEnabled();
        config.setEnabled(false);
        try {
            classicGui(mc);
        } finally {
            config.setEnabled(enabled);
        }
    }

    private void classicGui(Minecraft mc) {
        org.embeddedt.embeddium.api.OptionGUIConstructionEvent.BUS.addListener(
                e -> {
                    options.clear();
                    for (var page : e.getPages())
                        for (var option : page.getOptions())
                            if (option instanceof ExtraOption<?> extra) options.add(extra);
                });
        mc.setScreen(
                new org.embeddedt.embeddium.impl.gui.modern.EmbeddiumVideoOptionsScreen(mc.screen));
        require(options.size() >= 75, "Extra pages missing: " + options.size());
        long particleControls =
                options.stream().filter(o -> o.spec.id.getPath().startsWith("particle.")).count();
        require(
                particleControls
                        == net.minecraft.core.registries.BuiltInRegistries.PARTICLE_TYPE.size(),
                "registered particle controls missing: " + particleControls);
        var advanced = find("advanced_fog_settings");
        var multi = find("multi_dimension_fog");
        var owner = advanced.spec.owner;
        setBoolean(advanced, false);
        require(!multi.isAvailable(), "per-dimension fog incorrectly enabled before Advanced");
        setBoolean(advanced, true);
        require(multi.isAvailable(), "pending Advanced toggle did not enable per-dimension fog");
        require(
                !SodiumExtraClientMod.options().renderSettings.fogSettings.advanced,
                "GUI pending value leaked to live settings");
        advanced.reset();
        for (var option : options) {
            require(option.getDefaultValue() != null, "missing default for " + option.getId());
            require(
                    option.getControl()
                                    .createElement(
                                            new me.jellysquid.mods.sodium.client.util.Dim2i(
                                                    0, 0, 300, 20))
                            != null,
                    "missing control");
        }
        var button =
                find("more_hud_overlays")
                        .getControl()
                        .createElement(
                                new me.jellysquid.mods.sodium.client.util.Dim2i(0, 0, 300, 20));
        require(button.mouseClicked(290, 10, 0), "debug settings button did not open");
        require(
                mc.screen
                        instanceof
                        org.embeddedt.embeddium.impl.extra.compat.ExtraDebugOptionsScreen,
                "wrong debug settings screen");
        mc.setScreen(null);
    }

    private ExtraOption<?> find(String id) {
        return options.stream()
                .filter(o -> o.spec.id.getPath().equals(id))
                .findFirst()
                .orElseThrow();
    }

    @SuppressWarnings("unchecked")
    private static void setBoolean(ExtraOption<?> option, boolean value) {
        ((ExtraOption<Boolean>) option).setValue(value);
    }

    @SubscribeEvent
    public void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        try {
            require(System.nanoTime() < deadline, "Extra validation timeout at stage " + stage);
            Minecraft mc = Minecraft.getInstance();
            ticks++;
            if (ticks % 100 == 0)
                System.out.println(
                        "Extra stage="
                                + stage
                                + " screen="
                                + (mc.screen == null ? "null" : mc.screen.getClass().getName())
                                + " overlay="
                                + (mc.getOverlay() == null
                                        ? "null"
                                        : mc.getOverlay().getClass().getName()));
            if (stage == 0
                    && (mc.screen instanceof TitleScreen
                            || mc.screen instanceof AccessibilityOnboardingScreen)) {
                hadConfig = Files.exists(config);
                if (hadConfig) originalConfig = Files.readAllBytes(config);
                ShaderPackFixture.prepare();
                PaniniPixels.verify();
                AnimationValidation.verify(mc);
                gui(mc);
                RsoIntegration.verify(mc);
                mc.options.renderDistance().set(3);
                mc.options.simulationDistance().set(5);
                mc.options.pauseOnLostFocus = false;
                String name = "extra-validation-" + UUID.randomUUID();
                var settings =
                        new LevelSettings(
                                name,
                                GameType.CREATIVE,
                                false,
                                Difficulty.PEACEFUL,
                                true,
                                new GameRules(),
                                WorldDataConfiguration.DEFAULT);
                mc.forceSetScreen(
                        new GenericDirtMessageScreen(Component.literal("Validating Sodium Extra")));
                stage = 1;
                mc.createWorldOpenFlows()
                        .createFreshLevel(
                                name,
                                settings,
                                new WorldOptions(1024, false, false),
                                registry ->
                                        registry.registryOrThrow(Registries.WORLD_PRESET)
                                                .getHolderOrThrow(WorldPresets.FLAT)
                                                .value()
                                                .createWorldDimensions());
            } else if (stage >= 1 && mc.level != null && mc.player != null && mc.screen == null) {
                if (++settled < 60) return;
                var settings = SodiumExtraClientMod.options();
                if (stage == 1) {
                    BasicFeatures.verify(mc);
                    settings.extraSettings.paniniProjection = true;
                    settings.extraSettings.paniniProjectionQuality = 150;
                    settings.renderSettings.fogSettings.atmospheric.shapeMode =
                            me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions
                                    .FogShapeMode.CYLINDRICAL;
                    settings.renderSettings.fogSettings.atmospheric.distanceChunks = 4;
                    settings.extraSettings.cloudHeightOverride = true;
                    settings.extraSettings.cloudHeight = 65;
                    settings.detailSettings.sun = false;
                    settings.detailSettings.moon = false;
                    settings.detailSettings.stars = false;
                    settings.detailSettings.skyColors = false;
                    settings.detailSettings.biomeColors = false;
                    mc.levelRenderer.allChanged();
                    stage = 2;
                    settled = 0;
                } else if (stage == 2) {
                    var projection = PaniniProjection.INSTANCE;
                    var field = projection.getClass().getDeclaredField("worldTarget");
                    field.setAccessible(true);
                    var target = (com.mojang.blaze3d.pipeline.RenderTarget) field.get(projection);
                    require(
                            target != null
                                    && target.width
                                            == (int)
                                                    Math.ceil(
                                                            mc.getMainRenderTarget().width * 1.5F),
                            "world was not supersampled");
                    require(
                            mc.levelRenderer.isChunkCompiled(mc.player.blockPosition().below()),
                            "shaped fog terrain missing");
                    mc.options.graphicsMode().set(net.minecraft.client.GraphicsStatus.FABULOUS);
                    mc.levelRenderer.allChanged();
                    stage = 3;
                    settled = 0;
                } else if (stage == 3) {
                    require(
                            mc.options.graphicsMode().get()
                                    == net.minecraft.client.GraphicsStatus.FABULOUS,
                            "Fabulous was overridden instead of tested");
                    require(
                            ((me.flashyreese.mods.sodiumextra.mixin.panini_projection
                                                            .AccessorLevelRenderer)
                                                    mc.levelRenderer)
                                            .sodiumExtra$getTransparencyChain()
                                    != null,
                            "Fabulous targets were not active");
                    require(
                            PaniniProjection.INSTANCE.isRenderingWorld() == false,
                            "world state leaked outside rendering");
                    require(
                            GL33C.glGetError() == GL33C.GL_NO_ERROR,
                            "GL error during Fabulous/Panini frame");
                    settings.extraSettings.paniniProjectionQuality = 100;
                    settings.extraSettings.paniniProjectionStrength = 100;
                    settings.renderSettings.fogSettings.atmospheric.shapeMode =
                            me.flashyreese.mods.sodiumextra.client.config.SodiumExtraGameOptions
                                    .FogShapeMode.PLANAR;
                    PaniniProjection.INSTANCE.resize();
                    stage = 4;
                    settled = 0;
                } else if (stage == 4) {
                    var field = PaniniProjection.class.getDeclaredField("worldTarget");
                    field.setAccessible(true);
                    require(
                            field.get(PaniniProjection.INSTANCE) == null,
                            "100% quality allocated an unnecessary supersampling target");
                    settings.extraSettings.paniniProjection = false;
                    stage = 5;
                    settled = 0;
                } else if (stage == 5) {
                    settings.extraSettings.paniniProjection = true;
                    mc.gameRenderer.loadEffect(
                            new net.minecraft.resources.ResourceLocation(
                                    "shaders/post/invert.json"));
                    require(
                            mc.gameRenderer.currentEffect() != null,
                            "vanilla post effect did not load");
                    stage = 6;
                    settled = 0;
                } else if (stage == 6) {
                    var world = PaniniProjection.class.getDeclaredField("worldTarget");
                    world.setAccessible(true);
                    var hand = PaniniProjection.class.getDeclaredField("handTarget");
                    hand.setAccessible(true);
                    require(
                            world.get(PaniniProjection.INSTANCE) != null
                                    && hand.get(PaniniProjection.INSTANCE) != null,
                            "depth post effect did not preserve separate world/hand targets");
                    require(
                            GL33C.glGetError() == GL33C.GL_NO_ERROR,
                            "depth/hand integration produced a GL error");
                    mc.gameRenderer.shutdownEffect();
                    settings.extraSettings.preventShaders = true;
                    mc.gameRenderer.checkEntityPostEffect(
                            new net.minecraft.world.entity.monster.Creeper(
                                    net.minecraft.world.entity.EntityType.CREEPER, mc.level));
                    require(
                            mc.gameRenderer.currentEffect() == null,
                            "spectator post effect bypassed prevention");
                    mc.gameRenderer.loadEffect(
                            new net.minecraft.resources.ResourceLocation(
                                    "shaders/post/invert.json"));
                    require(
                            mc.gameRenderer.currentEffect() != null,
                            "spectator prevention blocked an independent manual post effect");
                    mc.gameRenderer.shutdownEffect();
                    settings.extraSettings.preventShaders = false;
                    ShaderPackFixture.enable();
                    stage = 7;
                    settled = 0;
                } else {
                    require(
                            !PaniniProjection.isEnabled(),
                            "Panini stayed enabled with an Iris shader pack");
                    require(
                            System.getProperty("java.specification.version").equals("17"),
                            "wrong Java runtime");
                    require(
                            GL33C.glGetError() == GL33C.GL_NO_ERROR,
                            "GL error after disabling projection");
                    String report =
                            "PASS Java17; "
                                    + options.size()
                                    + " Extra controls; pending dependency state; original Panini"
                                    + " color/mask/depth GPU oracle; 100/150% world targets;"
                                    + " planar/cylindrical fog; Fabulous pipeline; resize/disable"
                                    + " cleanup";
                    Files.writeString(Path.of("extra-validation.txt"), report);
                    System.out.println(report);
                    restore();
                    mc.stop();
                    stage = 6;
                }
            }
        } catch (Throwable failure) {
            failure.printStackTrace();
            try {
                Files.writeString(Path.of("extra-validation.txt"), "FAIL " + failure);
                restore();
            } catch (Exception ignored) {
            }
            Runtime.getRuntime().halt(1);
        }
    }

    private void restore() throws Exception {
        ShaderPackFixture.restore();
        if (hadConfig) Files.write(config, originalConfig);
        else Files.deleteIfExists(config);
    }
}
