package org.embeddedt.embeddium.impl.extra.compat.config.option;

public record Range(int min, int max, int step) {
    public Range {
        if (min > max || step <= 0) throw new IllegalArgumentException("Invalid option range");
    }
}
