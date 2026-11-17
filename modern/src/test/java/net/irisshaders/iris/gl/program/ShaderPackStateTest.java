package net.irisshaders.iris.gl.program;

import net.irisshaders.iris.helpers.StringPair;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ShaderPackStateTest {
    @TempDir Path root;

    private String state(String name, Properties options, String define, boolean content) {
        return ShaderPackState.describeSettings(name, root, options, List.of(new StringPair("FEATURE", define)), content);
    }

    @Test
    void unchangedStateIsStableAndPropertyOrderDoesNotMatter() throws Exception {
        Files.writeString(root.resolve("shader.vsh"), "vertex");
        var a = new Properties(); a.setProperty("B", "2"); a.setProperty("A", "1");
        var b = new Properties(); b.setProperty("A", "1"); b.setProperty("B", "2");
        assertEquals(state("pack", a, "1", false), state("pack", b, "1", false));
    }

    @Test
    void nameOptionsDefinesFilesAndRootResourcesInvalidateTheState() throws Exception {
        var file = root.resolve("shader.vsh");
        Files.writeString(file, "vertex");
        String before = state("pack", new Properties(), "1", false);
        assertNotEquals(before, state("other", new Properties(), "1", false));
        var options = new Properties(); options.setProperty("QUALITY", "high");
        assertNotEquals(before, state("pack", options, "1", false));
        assertNotEquals(before, state("pack", new Properties(), "2", false));
        var oldTime = Files.getLastModifiedTime(file);
        Files.setLastModifiedTime(file, FileTime.fromMillis(oldTime.toMillis() + 5000));
        assertNotEquals(before, state("pack", new Properties(), "1", false));
        Files.setLastModifiedTime(file, oldTime);
        Files.writeString(root.resolve("pack.properties"), "changed");
        assertNotEquals(before, state("pack", new Properties(), "1", false));
    }

    @Test
    void missingPackFallsBackAndContentModeIncludesTheFullTree() throws Exception {
        assertNull(ShaderPackState.describeSettings("missing", root.resolve("missing"), new Properties(), List.of(), false));
        Files.createDirectories(root.resolve("nested"));
        Files.writeString(root.resolve("nested/shader.fsh"), "fragment");
        String before = state("pack", new Properties(), "1", true);
        Files.writeString(root.resolve("nested/shader.fsh"), "new fragment");
        assertNotEquals(before, state("pack", new Properties(), "1", true));
    }
}
