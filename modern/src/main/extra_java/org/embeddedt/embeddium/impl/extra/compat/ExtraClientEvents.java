package org.embeddedt.embeddium.impl.extra.compat;

import me.flashyreese.mods.sodiumextra.client.config.SodiumExtraConfig;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

import org.embeddedt.embeddium.api.OptionGUIConstructionEvent;
import org.embeddedt.embeddium.impl.extra.compat.config.structure.ConfigBuilder;

@Mod.EventBusSubscriber(
        modid = "embeddium",
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ExtraClientEvents {
    @SubscribeEvent
    public static void reload(
            net.minecraftforge.client.event.RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(
                (net.minecraft.server.packs.resources.ResourceManagerReloadListener)
                        manager ->
                                me.flashyreese.mods.greenlight.feature.GreenlightRuntimeProvider
                                        .resourcesReloaded());
    }

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(
                () -> {
                    OptionGUIConstructionEvent.BUS.addListener(
                            gui -> {
                                var builder = new ConfigBuilder();
                                new SodiumExtraConfig().registerConfigLate(builder);
                                builder.appendTo(gui.getPages());
                            });
                });
    }
}
