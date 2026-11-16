// Ported from Angelica (LGPL-3.0), 996fb54cb05e256ea6039f4e2c8457a221a179b7.
package net.irisshaders.iris.shaderpack.preprocessor;

import net.irisshaders.iris.cache.ShaderDiskCache;
import net.irisshaders.iris.helpers.StringPair;
import org.jetbrains.annotations.Nullable;

/** Disk cache of preprocessor output, keyed on the input and every macro handed to the preprocessor. */
final class PreprocessedSourceCache {
    private PreprocessedSourceCache() {}

    @Nullable
    static ShaderDiskCache.Key key(String kind, String source, Iterable<StringPair> defines) {
        return ShaderDiskCache.isEnabled() ? withDefines(ShaderDiskCache.key("iris-preprocess").str(kind).str(source), defines) : null;
    }

    @Nullable
    static ShaderDiskCache.Key key(String kind, byte[] contentHash, Iterable<StringPair> defines) {
        return ShaderDiskCache.isEnabled() ? withDefines(ShaderDiskCache.key("iris-preprocess").str(kind).bytes(contentHash), defines) : null;
    }

    private static ShaderDiskCache.Key withDefines(ShaderDiskCache.Key key, Iterable<StringPair> defines) {
        for (StringPair define : defines) {
            key.str(define.key()).str(define.value());
        }
        return key;
    }

    @Nullable
    static String get(@Nullable ShaderDiskCache.Key key) {
        return key == null ? null : ShaderDiskCache.getString(key);
    }

    static void put(@Nullable ShaderDiskCache.Key key, String preprocessed) {
        if (key != null) ShaderDiskCache.putString(key, preprocessed);
    }
}
