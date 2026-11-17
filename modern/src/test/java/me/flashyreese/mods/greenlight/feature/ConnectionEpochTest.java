package me.flashyreese.mods.greenlight.feature;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class ConnectionEpochTest {
    @Test
    void connectionsAndDisconnectsHaveStableDistinctEpochs() {
        var epochs = new ResourcePackPolicySource.ConnectionEpoch();
        var first = new Object();
        var second = new Object();
        long empty = epochs.at(null), a = epochs.at(first);
        assertNotEquals(empty, a);
        assertEquals(a, epochs.at(first));
        long b = epochs.at(second);
        assertNotEquals(a, b);
        assertEquals(b, epochs.at(second));
        long disconnected = epochs.at(null);
        assertNotEquals(b, disconnected);
        assertEquals(disconnected, epochs.at(null));
        assertNotEquals(disconnected, epochs.at(first));
    }

    @Test
    void collectingAConnectionDoesNotHideTheDisconnectTransition() throws Exception {
        var epochs = new ResourcePackPolicySource.ConnectionEpoch();
        var connection = new Object();
        long connected = epochs.at(connection);
        var previous = ResourcePackPolicySource.ConnectionEpoch.class.getDeclaredField("previous");
        previous.setAccessible(true);
        var weak = (java.lang.ref.WeakReference<?>) previous.get(epochs);
        assertSame(connection, weak.get());
        weak.clear();
        assertNotEquals(connected, epochs.at(null));
    }
}
