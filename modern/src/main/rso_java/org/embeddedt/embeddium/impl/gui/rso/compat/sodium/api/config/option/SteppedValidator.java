package org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option;

public record SteppedValidator(int min, int max, int step) {
    public SteppedValidator {
        if (max < min || step <= 0) throw new IllegalArgumentException("Invalid integer range");
    }

    public boolean isValueValid(int value) {
        return value >= min && value <= max && (value - min) % step == 0;
    }

    public Integer getValidatedValue(
            Integer value, java.util.function.Supplier<Integer> defaultValue) {
        return isValueValid(value) ? value : defaultValue.get();
    }
}
