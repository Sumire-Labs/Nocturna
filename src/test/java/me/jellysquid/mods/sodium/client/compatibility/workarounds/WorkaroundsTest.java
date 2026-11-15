package me.jellysquid.mods.sodium.client.compatibility.workarounds;

import me.jellysquid.mods.sodium.client.compatibility.environment.GLContextInfo;
import me.jellysquid.mods.sodium.client.compatibility.environment.OSInfo.OS;
import me.jellysquid.mods.sodium.client.gl.arena.staging.MappedStagingBuffer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class WorkaroundsTest {
    private static final String PROPERTY = "embeddium.workaround.amdBufferMapping";
    private String oldProperty;
    private Set<Workarounds.Reference> oldWorkarounds;

    @BeforeEach
    void saveState() throws Exception {
        this.oldProperty = System.getProperty(PROPERTY);
        System.clearProperty(PROPERTY);
        this.oldWorkarounds = activeWorkarounds().get();
    }

    @AfterEach
    void restoreState() throws Exception {
        activeWorkarounds().set(this.oldWorkarounds);
        if (this.oldProperty == null) {
            System.clearProperty(PROPERTY);
        } else {
            System.setProperty(PROPERTY, this.oldProperty);
        }
    }

    @Test
    void onlyMatchesNativeAmdContextsOnWindows() {
        for (String vendor : new String[] {"ATI Technologies Inc.", "Advanced Micro Devices, Inc.", "AMD", " amd "}) {
            var context = new GLContextInfo(vendor, "Radeon", "4.6");
            assertTrue(Workarounds.isAmdWindowsContext(OS.WINDOWS, context), vendor);
            assertFalse(Workarounds.isAmdWindowsContext(OS.LINUX, context), vendor);
            assertFalse(Workarounds.isAmdWindowsContext(OS.UNKNOWN, context), vendor);
        }
        for (String vendor : new String[] {"NVIDIA Corporation", "Intel", "Mesa", "Google Inc. (AMD)", "", "ATI"}) {
            assertFalse(Workarounds.isAmdWindowsContext(OS.WINDOWS, new GLContextInfo(vendor, "AMD Radeon", "4.6")));
        }
        assertFalse(Workarounds.isAmdWindowsContext(OS.WINDOWS, null));
        assertFalse(Workarounds.isAmdWindowsContext(OS.WINDOWS, new GLContextInfo(null, "Radeon", "4.6")));
    }

    @Test
    void contextChangesRemoveOnlyTheAmdFlag() throws Exception {
        var original = EnumSet.of(Workarounds.Reference.NVIDIA_THREADED_OPTIMIZATIONS,
                Workarounds.Reference.NO_ERROR_CONTEXT_UNSUPPORTED);
        activeWorkarounds().set(original);
        Workarounds.onContextInitialized(OS.WINDOWS, new GLContextInfo("ATI Technologies Inc.", "Radeon", "4.6"));
        assertTrue(Workarounds.isWorkaroundEnabled(Workarounds.Reference.AMD_BUFFER_MAPPING));
        // An AMD adapter on a hybrid machine must not disable the NVIDIA context's buffer optimizations.
        Workarounds.onContextInitialized(OS.WINDOWS, new GLContextInfo("NVIDIA Corporation", "GeForce", "4.6"));
        assertEquals(original, activeWorkarounds().get());
        Workarounds.onContextInitialized(OS.WINDOWS, null);
        assertEquals(original, activeWorkarounds().get());
    }

    @Test
    void allowsVerifiedFixedDriversToOptOut() {
        var context = new GLContextInfo("ATI Technologies Inc.", "Radeon", "4.6");
        Workarounds.onContextInitialized(OS.WINDOWS, context);
        assertTrue(Workarounds.isWorkaroundEnabled(Workarounds.Reference.AMD_BUFFER_MAPPING));
        System.setProperty(PROPERTY, "false");
        Workarounds.onContextInitialized(OS.WINDOWS, context);
        assertFalse(Workarounds.isWorkaroundEnabled(Workarounds.Reference.AMD_BUFFER_MAPPING));
        System.setProperty(PROPERTY, "true");
        Workarounds.onContextInitialized(OS.LINUX, context);
        assertFalse(Workarounds.isWorkaroundEnabled(Workarounds.Reference.AMD_BUFFER_MAPPING));
    }

    @Test
    void doesNotQueryPersistentMappingCapabilitiesOnAffectedContexts() {
        Workarounds.onContextInitialized(OS.WINDOWS, new GLContextInfo("ATI Technologies Inc.", "Radeon", "4.6"));
        assertFalse(MappedStagingBuffer.isSupported(null));
    }

    @SuppressWarnings("unchecked")
    private static AtomicReference<Set<Workarounds.Reference>> activeWorkarounds() throws Exception {
        var field = Workarounds.class.getDeclaredField("ACTIVE_WORKAROUNDS");
        field.setAccessible(true);
        return (AtomicReference<Set<Workarounds.Reference>>) field.get(null);
    }
}
