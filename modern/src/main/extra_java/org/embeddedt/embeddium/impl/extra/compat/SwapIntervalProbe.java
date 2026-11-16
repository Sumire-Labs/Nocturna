package org.embeddedt.embeddium.impl.extra.compat;

import java.util.OptionalInt;

/** Capability probing restores the queried interval, including a failed adaptive request. */
public final class SwapIntervalProbe {
    public interface Backend {
        long context();

        OptionalInt current();

        boolean set(int interval);
    }

    private long context;
    private boolean supported;

    public boolean supported(Backend backend) {
        long currentContext = backend.context();
        if (currentContext == 0) return false;
        if (currentContext == context) return supported;
        var previous = backend.current();
        if (previous.isEmpty()) return false;
        boolean result = false;
        try {
            result = backend.set(-1);
        } finally {
            if (!backend.set(previous.getAsInt())) result = false;
        }
        context = currentContext;
        supported = result;
        return result;
    }
}
