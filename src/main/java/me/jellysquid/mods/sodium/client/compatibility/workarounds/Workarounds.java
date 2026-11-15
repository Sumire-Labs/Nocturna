package me.jellysquid.mods.sodium.client.compatibility.workarounds;

import me.jellysquid.mods.sodium.client.compatibility.environment.GLContextInfo;
import me.jellysquid.mods.sodium.client.compatibility.environment.OSInfo;
import me.jellysquid.mods.sodium.client.compatibility.environment.OSInfo.OS;
import me.jellysquid.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterInfo;
import me.jellysquid.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterProbe;
import me.jellysquid.mods.sodium.client.compatibility.environment.probe.GraphicsAdapterVendor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static me.jellysquid.mods.sodium.client.SodiumClientMod.MODNAME;

public class Workarounds {
    private static final Logger LOGGER = LoggerFactory.getLogger(MODNAME + "-Workarounds");

    private static final AtomicReference<Set<Reference>> ACTIVE_WORKAROUNDS = new AtomicReference<>(EnumSet.noneOf(Reference.class));

    public static void init() {
        var workarounds = findNecessaryWorkarounds();

        if (!workarounds.isEmpty()) {
            LOGGER.warn("Embeddium has applied one or more workarounds to prevent crashes or other issues on your system: [{}]",
                    workarounds.stream()
                            .map(Enum::name)
                            .collect(Collectors.joining(", ")));
            LOGGER.warn("This is not necessarily an issue, but it may result in certain features or optimizations being " +
                    "disabled. You can sometimes fix these issues by upgrading your graphics driver.");
        }

        ACTIVE_WORKAROUNDS.set(workarounds);
    }

    private static Set<Reference> findNecessaryWorkarounds() {
        var workarounds = EnumSet.noneOf(Reference.class);
        var operatingSystem = OSInfo.getOS();

        var graphicsAdapters = GraphicsAdapterProbe.getAdapters();

        if (isUsingNvidiaGraphicsCard(operatingSystem, graphicsAdapters)) {
            workarounds.add(Reference.NVIDIA_THREADED_OPTIMIZATIONS);
        }

        if (operatingSystem == OSInfo.OS.LINUX) {
            var session = System.getenv("XDG_SESSION_TYPE");

            if (session == null) {
                LOGGER.warn("Unable to determine desktop session type because the environment variable XDG_SESSION_TYPE " +
                        "is not set! Your user session may not be configured correctly.");
            }

            if (Objects.equals(session, "wayland")) {
                // This will also apply under Xwayland, even though the problem does not happen there
                workarounds.add(Reference.NO_ERROR_CONTEXT_UNSUPPORTED);
            }
        }

        return Collections.unmodifiableSet(workarounds);
    }

    private static boolean isUsingNvidiaGraphicsCard(OSInfo.OS operatingSystem, Collection<GraphicsAdapterInfo> adapters) {
        return (operatingSystem == OSInfo.OS.WINDOWS || operatingSystem == OSInfo.OS.LINUX) &&
                adapters.stream().anyMatch(adapter -> adapter.vendor() == GraphicsAdapterVendor.NVIDIA);
    }

    public static boolean isWorkaroundEnabled(Reference id) {
        return ACTIVE_WORKAROUNDS.get()
                .contains(id);
    }

    public static void onContextInitialized(@Nullable GLContextInfo context) {
        onContextInitialized(OSInfo.getOS(), context);
    }

    static void onContextInitialized(OS operatingSystem, @Nullable GLContextInfo context) {
        // Adapter probing is insufficient on hybrid systems: only the active OpenGL implementation matters here.
        boolean enabled = isAmdWindowsContext(operatingSystem, context)
                && Boolean.parseBoolean(System.getProperty("embeddium.workaround.amdBufferMapping", "true"));
        var previous = ACTIVE_WORKAROUNDS.getAndUpdate(current -> {
            var updated = EnumSet.noneOf(Reference.class);
            updated.addAll(current);
            updated.remove(Reference.AMD_BUFFER_MAPPING);
            if (enabled) {
                updated.add(Reference.AMD_BUFFER_MAPPING);
            }
            return Collections.unmodifiableSet(updated);
        });

        if (enabled && !previous.contains(Reference.AMD_BUFFER_MAPPING)) {
            LOGGER.warn("Applying workaround: Avoid mapped chunk buffers on the AMD Windows OpenGL driver "
                    + "(AMD_BUFFER_MAPPING)");
        }
    }

    static boolean isAmdWindowsContext(OS operatingSystem, @Nullable GLContextInfo context) {
        if (operatingSystem != OS.WINDOWS || context == null || context.vendor() == null) {
            return false;
        }

        return switch (context.vendor().trim().toLowerCase(Locale.ROOT)) {
            case "ati technologies inc.", "advanced micro devices, inc.", "amd" -> true;
            default -> false;
        };
    }

    public enum Reference {
        /**
         * The NVIDIA driver applies "Threaded Optimizations" when Minecraft is detected, causing severe
         * performance issues and crashes.
         * <a href="https://github.com/CaffeineMC/sodium-fabric/issues/1816">GitHub Issue</a>
         */
        NVIDIA_THREADED_OPTIMIZATIONS,

        /**
         * Requesting a No Error Context causes a crash at startup when using a Wayland session.
         * <a href="https://github.com/CaffeineMC/sodium-fabric/issues/1624">GitHub Issue</a>
         */
        NO_ERROR_CONTEXT_UNSUPPORTED,

        /**
         * AMD's Windows Minecraft application profile can corrupt mapped buffers, including the shared quad indices.
         * Selected from the active context, so AMD adapters driving other displays do not affect other GPU vendors.
         * <a href="https://github.com/GPUOpen-Drivers/AMD-Gfx-Drivers/issues/74">Driver issue</a>
         */
        AMD_BUFFER_MAPPING,
    }
}
