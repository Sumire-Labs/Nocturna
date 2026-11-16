package org.embeddedt.embeddium.impl.gui.rso;

import static org.junit.jupiter.api.Assertions.*;

import org.embeddedt.embeddium.impl.gui.rso.compat.sodium.api.config.option.SteppedValidator;
import org.junit.jupiter.api.Test;

class SteppedValidatorTest {
    @Test
    void matchesRsoRuntimeValidationForOffGridAndOutOfRangeValues() {
        // Recorded by running Sodium 0.9.2's public validator API used by the pinned RSO release.
        var validator = new SteppedValidator(2, 12, 5);
        int[] inputs = {
            -1, 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, Integer.MAX_VALUE, Integer.MIN_VALUE
        };
        int[] expected = {7, 7, 7, 2, 7, 7, 7, 7, 7, 7, 7, 7, 7, 12, 7, 7, 7};
        for (int i = 0; i < inputs.length; i++) {
            assertEquals(
                    inputs[i] == 2 || inputs[i] == 7 || inputs[i] == 12,
                    validator.isValueValid(inputs[i]));
            assertEquals(expected[i], validator.getValidatedValue(inputs[i], () -> 7));
        }
    }
}
