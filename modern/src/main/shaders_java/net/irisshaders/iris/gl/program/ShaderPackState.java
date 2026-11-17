// Ported from Angelica (LGPL-3.0), 996fb54cb05e256ea6039f4e2c8457a221a179b7.
package net.irisshaders.iris.gl.program;
import net.irisshaders.iris.cache.ShaderCacheWorkers;
import net.irisshaders.iris.helpers.StringPair;
import org.jetbrains.annotations.Nullable;
import java.io.*;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Full pack/settings/environment identity used for live-handle invalidation. */
public final class ShaderPackState {
    private ShaderPackState() {}
    private record HashedFile(long size, long modified, String hash) {}

    private static final Map<Path, HashedFile> contentHashes = new ConcurrentHashMap<>();

    private static String contentHash(Path file, BasicFileAttributes attributes) throws IOException {
        final long size = attributes.size();
        final long modified = attributes.lastModifiedTime().toMillis();
        final HashedFile known = contentHashes.get(file);
        if (known != null && known.size() == size && known.modified() == modified) return known.hash();
        final String hash;
        try {
            hash = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
        contentHashes.put(file, new HashedFile(size, modified, hash));
        return hash;
    }

    private record VisitedFolder(Path path, @Nullable Object key) {}

    private static Map<String, String> describeFolder(Path root, VisitedFolder folder, List<VisitedFolder> ancestors, boolean byContent) {
        final Map<String, String> files = new HashMap<>();
        final List<Supplier<Map<String, String>>> subfolders = new ArrayList<>();
        final List<VisitedFolder> lineage = new ArrayList<>(ancestors);
        lineage.add(folder);
        try (DirectoryStream<Path> entries = Files.newDirectoryStream(folder.path())) {
            for (Path entry : entries) {
                BasicFileAttributes attributes;
                try {
                    attributes = Files.readAttributes(entry, BasicFileAttributes.class);
                } catch (IOException e) {
                    attributes = Files.readAttributes(entry, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
                }
                if (attributes.isDirectory()) {
                    final VisitedFolder subfolder = new VisitedFolder(entry, attributes.fileKey());
                    for (VisitedFolder ancestor : lineage) {
                        final boolean same = subfolder.key() != null && ancestor.key() != null
                            ? subfolder.key().equals(ancestor.key())
                            : Files.isSameFile(ancestor.path(), entry);
                        if (same) throw new FileSystemLoopException(entry.toString());
                    }
                    subfolders.add(() -> describeFolder(root, subfolder, lineage, byContent));
                } else if (attributes.isRegularFile()) {
                    files.put(root.relativize(entry).toString(),
                        byContent ? contentHash(entry, attributes) : attributes.size() + "|" + attributes.lastModifiedTime().toMillis());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        for (Map<String, String> nested : ShaderCacheWorkers.invokeAll(subfolders)) {
            files.putAll(nested);
        }
        return files;
    }

    @Nullable
    public static String describeSettings(String packName, Path packRoot, Properties options, Iterable<StringPair> environmentDefines, boolean byContent) {
        final StringBuilder settings = new StringBuilder(packName).append('\n');
        if (Files.isRegularFile(packRoot)) {
            try {
                settings.append(Files.size(packRoot)).append('|').append(Files.getLastModifiedTime(packRoot).toMillis());
            } catch (IOException e) {
                return null;
            }
        } else if (Files.isDirectory(packRoot)) {
            final TreeMap<String, String> files = new TreeMap<>();
            try {
                // One stat per file; this runs on the render thread when shaders turn on, so folders are read in parallel
                final Object rootKey = Files.readAttributes(packRoot, BasicFileAttributes.class).fileKey();
                files.putAll(describeFolder(packRoot, new VisitedFolder(packRoot, rootKey), List.of(), byContent));
            } catch (IOException | UncheckedIOException e) {
                return null;
            }
            files.forEach((file, state) -> settings.append(file).append('|').append(state).append('\n'));
        } else {
            return null;
        }
        settings.append('\n');
        new TreeMap<>(options).forEach((key, value) -> settings.append(key).append('=').append(value).append('\n'));
        for (StringPair define : environmentDefines) {
            settings.append('#').append(define.key()).append(' ').append(define.value()).append('\n');
        }
        // Minecraft 1.20.1 uses native registry IDs; Angelica's legacy-ID fallback option has no counterpart.
        settings.append("nativeMcVersion=1.20.1").append('\n');
        return settings.toString();
    }

}
