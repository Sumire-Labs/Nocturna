package me.jellysquid.mods.sodium.client;

import me.jellysquid.mods.sodium.client.gui.SodiumGameOptions;
import org.embeddedt.embeddium.impl.Celeritas;
import org.embeddedt.embeddium.impl.compat.legacy.LegacyOptions;
import org.slf4j.Logger;

/** Compatibility entry point for the public Embeddium 0.3 configuration API. */
public final class SodiumClientMod {
    public static final String MODID = "embeddium";
    public static final String MODNAME = "Embeddium Continuous";
    public static SodiumGameOptions options() { return LegacyOptions.view(Celeritas.options()); }
    public static Logger logger() { return Celeritas.logger(); }
    public static String getVersion() { return Celeritas.getVersion(); }
    public static void restoreDefaultOptions() { Celeritas.restoreDefaultOptions(); }
    public static boolean canUseVanillaVertices() { return Celeritas.canUseVanillaVertices(); }
    public static boolean canApplyTranslucencySorting() { return Celeritas.canApplyTranslucencySorting(); }
}
