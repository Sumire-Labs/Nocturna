package net.irisshaders.iris.shaderpack.preprocessor;

import com.google.common.collect.ImmutableList;
import net.irisshaders.iris.cache.ShaderDiskCache;
import net.irisshaders.iris.helpers.StringPair;
import net.irisshaders.iris.shaderpack.include.AbsolutePackPath;
import net.irisshaders.iris.shaderpack.include.IncludeGraph;
import net.irisshaders.iris.shaderpack.include.IncludeProcessor;
import net.irisshaders.iris.shaderpack.option.ShaderPackOptions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PreprocessedSourceCacheTest {
    @TempDir Path dir;

    @AfterEach void reset() throws Exception {
        var field = ShaderDiskCache.class.getDeclaredField("root");
        field.setAccessible(true);
        field.set(null, null);
    }

    @Test void coldAndWarmOutputMatchesAndMacrosInvalidate() throws Exception {
        ShaderDiskCache.configure(dir, "tests");
        String source = "#version 150\n#define LOCAL 3\nconst int choice = CHOICE + LOCAL;\n";
        byte[] hash = java.security.MessageDigest.getInstance("SHA-256").digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var a = List.of(new StringPair("CHOICE", "7"));
        String expected = JcppProcessor.glslPreprocessSource(source, a);
        assertEquals(expected, JcppProcessor.glslPreprocessSource(hash, () -> source, a));
        assertEquals(expected, JcppProcessor.glslPreprocessSource(hash, () -> { throw new AssertionError("Warm hit expanded source"); }, a));
        var b = List.of(new StringPair("CHOICE", "9"));
        assertEquals(JcppProcessor.glslPreprocessSource(source, b), JcppProcessor.glslPreprocessSource(hash, () -> source, b));
        assertNotEquals(expected, JcppProcessor.glslPreprocessSource(hash, () -> source, b));
    }

    @Test void includeContentChangesInvalidateParentsAndHashesSurviveNewProcessors() throws Exception {
        var root = dir.resolve("pack"); Files.createDirectories(root);
        Files.writeString(root.resolve("main.vsh"), "#version 150\n#include \"shared.glsl\"\n");
        Files.writeString(root.resolve("shared.glsl"), "const float light = 1.0;\n");
        var path = AbsolutePackPath.fromAbsolutePath("/main.vsh");
        var first = new IncludeProcessor(new IncludeGraph(root, ImmutableList.of(path)));
        var identical = new IncludeProcessor(new IncludeGraph(root, ImmutableList.of(path)));
        assertArrayEquals(first.getIncludedFileHash(path), identical.getIncludedFileHash(path));
        Files.writeString(root.resolve("shared.glsl"), "const float light = 2.0;\n");
        var changed = new IncludeProcessor(new IncludeGraph(root, ImmutableList.of(path)));
        assertFalse(java.util.Arrays.equals(first.getIncludedFileHash(path), changed.getIncludedFileHash(path)));
    }

    @Test void propertiesKeyIncludesEffectiveOptionsAndEnvironment() throws Exception {
        var root = dir.resolve("pack"); Files.createDirectories(root);
        Files.writeString(root.resolve("main.vsh"), "#define QUALITY 1 // [1 2]\nvoid main() {}\n");
        var graph = new IncludeGraph(root, ImmutableList.of(AbsolutePackPath.fromAbsolutePath("/main.vsh")));
        var low = new ShaderPackOptions(graph, Map.of("QUALITY", "1"));
        var high = new ShaderPackOptions(graph, Map.of("QUALITY", "2"));
        String source = "#if QUALITY == 1\nquality=low\n#else\nquality=high\n#endif\n";
        String expected = PropertiesPreprocessor.preprocessSource(source, low, List.of());
        ShaderDiskCache.configure(dir.resolve("cache"), "tests");
        assertEquals(expected, PropertiesPreprocessor.preprocessSource(source, low, List.of()));
        assertEquals(expected, PropertiesPreprocessor.preprocessSource(source, low, List.of()));
        assertNotEquals(expected, PropertiesPreprocessor.preprocessSource(source, high, List.of()));
    }

    @Test void corruptionRegeneratesAndCacheDisabledStillUsesPreprocessor() throws Exception {
        String source = "#version 150\nvoid main() {}\n";
        byte[] hash = new byte[]{1, 2, 3};
        ShaderDiskCache.configure(dir, "tests");
        String expected = JcppProcessor.glslPreprocessSource(hash, () -> source, List.of());
        try (var files = Files.walk(dir)) {
            var file = files.filter(p -> p.toString().endsWith(".bin")).findFirst().orElseThrow();
            Files.write(file, new byte[]{1, 2});
        }
        assertEquals(expected, JcppProcessor.glslPreprocessSource(hash, () -> source, List.of()));
        reset();
        int[] called = {0};
        assertEquals(expected, JcppProcessor.glslPreprocessSource(hash, () -> {called[0]++; return source;}, List.of()));
        assertEquals(1, called[0]);
    }
}
