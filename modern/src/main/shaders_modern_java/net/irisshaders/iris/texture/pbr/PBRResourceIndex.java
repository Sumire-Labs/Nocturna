// Ported from Angelica (LGPL-3.0), 996fb54cb05e256ea6039f4e2c8457a221a179b7.
package net.irisshaders.iris.texture.pbr;

import net.irisshaders.iris.cache.ShaderCacheWorkers;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/** Negative lookup accelerator only: resource selection, metadata and image loading stay with Minecraft. */
public final class PBRResourceIndex {
    private static final Logger LOGGER = LogManager.getLogger("PBRResourceIndex");
    private static final String ASSETS = "assets";
    private static final String NORMAL_NAME = PBRType.NORMAL.getSuffix() + ".png";
    private static final String SPECULAR_NAME = PBRType.SPECULAR.getSuffix() + ".png";

    private static MultiPackResourceManager owner;
    private static CompletableFuture<Map<PackResources, PackIndex>> pending;
    private static Map<PackResources, PackIndex> built;
    private static final Map<ResourceLocation, Boolean> answers = new HashMap<>();
    private static boolean disabled;
    private static boolean warned;

    private PBRResourceIndex() {
    }

    private record PackIndex(Set<String> names, boolean lowerCase) {}

    private record ScanTarget(PackResources pack, ZipFile zip, Path folder) {}

    // Do not infer the lookup semantics of mod-provided ResourceManager subclasses.
    private static MultiPackResourceManager resolve(ResourceManager resourceManager) {
        if (resourceManager.getClass() == ReloadableResourceManager.class) {
            resourceManager = ((ReloadableResourceManager) resourceManager).resources;
        }
        return resourceManager.getClass() == MultiPackResourceManager.class
            ? (MultiPackResourceManager) resourceManager : null;
    }

    public static synchronized void start(ResourceManager resourceManager) {
        drop();
        owner = resolve(resourceManager);
        disabled = false;
        if (owner == null) {
            return;
        }
        try {
            final List<ScanTarget> targets = collectTargets(owner);
            pending = ShaderCacheWorkers.submit(() -> scan(targets));
        } catch (RuntimeException e) {
            disable(e);
        }
    }

    private static synchronized void drop() {
        // 1.20.1 closes the previous pack stack before constructing its replacement. Finish reading
        // borrowed ZipFiles before close; the index never owns or closes these handles itself.
        if (pending != null) {
            pending.handle((result, error) -> null).join();
        }
        owner = null;
        pending = null;
        built = null;
        answers.clear();
    }

    public static synchronized void release(ResourceManager resourceManager) {
        if (owner == resolve(resourceManager)) {
            drop();
        }
    }

    public static synchronized boolean mayExist(ResourceManager resourceManager, ResourceLocation location) {
        final MultiPackResourceManager manager = resolve(resourceManager);
        if (manager == null) {
            return true;
        }
        final String path = location.getPath();
        if (!path.endsWith(NORMAL_NAME) && !path.endsWith(SPECULAR_NAME)) {
            return true;
        }
        if (owner != manager) {
            start(manager);
        }
        if (disabled) {
            return true;
        }
        if (built == null) {
            try {
                built = pending.join();
            } catch (RuntimeException e) {
                disable(e);
                return true;
            }
        }
        final Boolean known = answers.get(location);
        if (known != null) {
            return known;
        }
        final boolean present = lookUp(manager, location, path);
        answers.put(location, present);
        return present;
    }

    private static boolean lookUp(MultiPackResourceManager manager, ResourceLocation location, String path) {
        final FallbackResourceManager fallback = manager.namespacedManagers.get(location.getNamespace());
        if (fallback == null) {
            return false;
        }
        if (fallback.getClass() != FallbackResourceManager.class) {
            return true;
        }
        final String name = ASSETS + "/" + location.getNamespace() + "/" + path;
        String lower = null;
        final List<FallbackResourceManager.PackEntry> packs = fallback.fallbacks;
        for (int i = packs.size() - 1; i >= 0; i--) {
            final FallbackResourceManager.PackEntry entry = packs.get(i);
            final PackResources pack = entry.resources();
            if (pack != null) {
                final PackIndex index = built.get(pack);
                final boolean present;
                if (index == null) {
                    present = pack.getResource(PackType.CLIENT_RESOURCES, location) != null;
                } else if (index.lowerCase()) {
                    if (lower == null) lower = name.toLowerCase(Locale.ROOT);
                    present = index.names().contains(lower);
                } else {
                    present = index.names().contains(name);
                }
                if (present) {
                    return true;
                }
            }
            // Native getResource checks the current pack before its filter blocks lower packs.
            if (entry.isFiltered(location)) {
                return false;
            }
        }
        return false;
    }

    private static void disable(RuntimeException e) {
        disabled = true;
        built = null;
        pending = null;
        if (!warned) {
            warned = true;
            LOGGER.warn("PBR resource index disabled, falling back to direct lookups", e);
        }
    }

    private static List<ScanTarget> collectTargets(MultiPackResourceManager manager) {
        final Set<PackResources> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        final List<ScanTarget> targets = new ArrayList<>();
        for (FallbackResourceManager fallback : manager.namespacedManagers.values()) {
            if (fallback.getClass() == FallbackResourceManager.class) {
                for (FallbackResourceManager.PackEntry entry : fallback.fallbacks) {
                    final PackResources pack = entry.resources();
                    if (pack != null && seen.add(pack)) {
                        final ScanTarget target = target(pack);
                        if (target != null) {
                            targets.add(target);
                        }
                    }
                }
            }
        }
        return targets;
    }

    private static ScanTarget target(PackResources pack) {
        final Class<?> type = pack.getClass();
        if (type == FilePackResources.class) {
            final ZipFile zip = ((FilePackResources) pack).getOrCreateZipFile();
            if (zip == null) {
                throw new IllegalStateException("Unable to index " + pack.packId());
            }
            return new ScanTarget(pack, zip, null);
        }
        if (type == PathPackResources.class) {
            return new ScanTarget(pack, null, ((PathPackResources) pack).root);
        }
        if (type == net.minecraftforge.resource.PathPackResources.class) {
            return new ScanTarget(pack, null, ((net.minecraftforge.resource.PathPackResources) pack).getSource());
        }
        return null;
    }

    private static Map<PackResources, PackIndex> scan(List<ScanTarget> targets) {
        final Map<PackResources, PackIndex> result = new IdentityHashMap<>();
        for (ScanTarget t : targets) {
            result.put(t.pack(), t.zip() != null ? scanZip(t.zip()) : scanFolder(t.folder()));
        }
        return result;
    }

    private static boolean isPbrName(String name) {
        return name.endsWith(NORMAL_NAME) || name.endsWith(SPECULAR_NAME);
    }

    private static PackIndex scanZip(ZipFile zip) {
        final Set<String> names = new HashSet<>();
        final Enumeration<? extends ZipEntry> entries = zip.entries();
        while (entries.hasMoreElements()) {
            final String name = entries.nextElement().getName();
            final String key = name.endsWith("/") ? name.substring(0, name.length() - 1) : name;
            if (isPbrName(key)) {
                names.add(key);
            }
        }
        return new PackIndex(names, false);
    }

    private static PackIndex scanFolder(Path root) {
        final Set<String> names = new HashSet<>();
        final Path assets = root.resolve(ASSETS);
        final boolean caseInsensitive = Files.exists(root.resolve(ASSETS.toUpperCase(Locale.ROOT)));
        if (Files.isDirectory(assets)) {
            try {
                Files.walkFileTree(assets, EnumSet.of(FileVisitOption.FOLLOW_LINKS), Integer.MAX_VALUE, new SimpleFileVisitor<>() {
                    private void add(Path path) {
                        final String relative = root.relativize(path).toString().replace(File.separatorChar, '/');
                        final String name = caseInsensitive ? relative.toLowerCase(Locale.ROOT) : relative;
                        if (isPbrName(name)) {
                            names.add(name);
                        }
                    }

                    @Override
                    public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes attrs) {
                        // PathPackResources.getResource tests existence, including directories.
                        add(path);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFile(Path path, BasicFileAttributes attrs) {
                        add(path);
                        return FileVisitResult.CONTINUE;
                    }

                    @Override
                    public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
                        if (e instanceof NoSuchFileException) {
                            return FileVisitResult.CONTINUE;
                        }
                        throw e;
                    }
                });
            } catch (IOException e) {
                throw new IllegalStateException("Unable to index " + root, e);
            }
        }
        return new PackIndex(names, caseInsensitive);
    }
}
