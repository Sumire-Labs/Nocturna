package net.irisshaders.iris.gl.program;

import org.embeddedt.embeddium.impl.gl.shader.ShaderType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Pure lookup/ownership cases; real GL eviction and deletion are checked by the validation client. */
class RetainedProgramsTest {
    @AfterEach
    void clearFakeHandles() throws Exception {
        for (String name : new String[]{"recent", "older"}) {
            var field = RetainedPrograms.class.getDeclaredField(name);
            field.setAccessible(true);
            ((Map<?, ?>) field.get(null)).clear();
        }
    }

    private static RetainedPrograms.Ownership owner(String vertex, Map<String, Integer> attributes) {
        return RetainedPrograms.ownership(Map.of(ShaderType.VERTEX, vertex, ShaderType.FRAGMENT, "fragment"), attributes);
    }

    @Test
    void takeConsumesEachHandleOnceInOriginalFifoOrder() {
        var owner = owner("vertex", Map.of());
        RetainedPrograms.retain(owner.sources(), 11);
        RetainedPrograms.retain(owner.sources(), 12);
        assertEquals(11, RetainedPrograms.take(owner));
        assertEquals(12, RetainedPrograms.take(owner));
        assertEquals(0, RetainedPrograms.take(owner));
    }

    @Test
    void recentHandlesArePreferredOverTheOlderGeneration() {
        var owner = owner("vertex", Map.of());
        RetainedPrograms.retain(owner.sources(), 11);
        RetainedPrograms.afterPipelineBuilt();
        RetainedPrograms.retain(owner.sources(), 12);
        assertEquals(12, RetainedPrograms.take(owner));
        assertEquals(11, RetainedPrograms.take(owner));
        assertEquals(0, RetainedPrograms.take(owner));
    }

    @Test
    void nativeAttributeBindingsArePartOfIdentityAndCannotMutate() {
        var attributes = new HashMap<>(Map.of("Position", 0));
        var original = owner("vertex", attributes);
        attributes.put("Position", 3);
        assertEquals(Map.of("Position", 0), original.sources().attributes());
        assertThrows(UnsupportedOperationException.class, () -> original.sources().attributes().put("x", 2));
        RetainedPrograms.retain(original.sources(), 11);
        assertEquals(0, RetainedPrograms.take(owner("vertex", attributes)));
        assertEquals(11, RetainedPrograms.take(owner("vertex", Map.of("Position", 0))));
    }

    @Test
    void everyShaderStageParticipatesInIdentity() {
        var stages = new HashMap<ShaderType, String>();
        for (var type : new ShaderType[]{ShaderType.VERTEX, ShaderType.FRAGMENT, ShaderType.GEOM,
            ShaderType.TESS_CTRL, ShaderType.TESS_EVALUATE, ShaderType.COMPUTE}) stages.put(type, type.name());
        var original = RetainedPrograms.ownership(stages, Map.of());
        RetainedPrograms.retain(original.sources(), 11);
        for (var type : stages.keySet()) {
            var changed = new HashMap<>(stages);
            changed.put(type, "changed");
            assertEquals(0, RetainedPrograms.take(RetainedPrograms.ownership(changed, Map.of())), type.name());
        }
        assertEquals(11, RetainedPrograms.take(original));
    }

    @Test
    void anOwnershipFromAnInvalidatedConfigurationCannotTakeNewHandles() {
        RetainedPrograms.useConfiguration("before");
        var old = owner("vertex", Map.of());
        RetainedPrograms.useConfiguration("after");
        var current = owner("vertex", Map.of());
        RetainedPrograms.retain(current.sources(), 11);
        assertEquals(0, RetainedPrograms.take(old));
        assertEquals(11, RetainedPrograms.take(current));
    }

    @Test
    void unchangedConfigurationKeepsOwnershipValid() {
        RetainedPrograms.useConfiguration("same");
        var original = owner("vertex", Map.of());
        RetainedPrograms.useConfiguration("same");
        RetainedPrograms.retain(original.sources(), 11);
        assertEquals(11, RetainedPrograms.take(original));
    }
}
