package net.irisshaders.iris.texture.pbr;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.resources.FallbackResourceManager;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.MultiPackResourceManager;
import net.minecraft.server.packs.resources.ReloadableResourceManager;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.Unit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/** Angelica's seven resource-index cases, exercised against the real Forge 1.20.1 pack stack. */
class PBRResourceIndexTest {
    @TempDir Path tmp;
    private final List<MultiPackResourceManager> managers = new ArrayList<>();
    private final List<PackResources> packs = new ArrayList<>();

    @AfterEach
    void cleanup() {
        for (var manager : managers) PBRResourceIndex.release(manager);
        for (var pack : packs) pack.close();
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(path.contains(":") ? path : "minecraft:textures/" + path);
    }

    private FilePackResources zip(String name, String... entries) throws IOException {
        Path file = tmp.resolve(name);
        try (var out = new ZipOutputStream(Files.newOutputStream(file))) {
            for (String entry : entries) {
                out.putNextEntry(new ZipEntry(entry));
                if (!entry.endsWith("/")) out.write(entry.getBytes(StandardCharsets.UTF_8));
                out.closeEntry();
            }
        }
        var pack = new FilePackResources(name, file.toFile(), false);
        packs.add(pack);
        return pack;
    }

    private PathPackResources folder(String name, String... files) throws IOException {
        Path root = Files.createDirectories(tmp.resolve(name));
        for (String file : files) {
            Path path = root.resolve(file);
            Files.createDirectories(path.getParent());
            Files.writeString(path, file);
        }
        var pack = new PathPackResources(name, root, false);
        packs.add(pack);
        return pack;
    }

    private MultiPackResourceManager manager(PackResources... entries) {
        var manager = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of(entries));
        managers.add(manager);
        return manager;
    }

    private MultiPackResourceManager mixed() throws IOException {
        return manager(zip("low.zip", "assets/minecraft/textures/low_n.png",
                "assets/minecraft/textures/low_s.png", "assets/minecraft/textures/both_n.png",
                "assets/minecraft/textures/dir_n.png/", "assets/minecraft/textures/plain.png",
                "assets/minecraft/textures/tail_n.png.bak", "assets/other/textures/other_n.png"),
            folder("mid", "assets/minecraft/textures/folder_s.png", "assets/minecraft/textures/both_n.png",
                "assets/minecraft/textures/Mixed_n.png", "assets/minecraft/textures/deep/nested_n.png",
                "assets/other/textures/otherfolder_s.png"),
            new StubPack(id("stub_n.png"), id("low_s.png"), id("stubonly:textures/x_n.png")));
    }

    private static void assertAgrees(ResourceManager manager, ResourceLocation location) throws IOException {
        var direct = manager.getResource(location);
        assertEquals(direct.isPresent(), PBRResourceIndex.mayExist(manager, location), location.toString());
        var indexed = PBRResourceIndex.mayExist(manager, location) ? manager.getResource(location)
            : java.util.Optional.<net.minecraft.server.packs.resources.Resource>empty();
        assertEquals(direct.map(r -> r.sourcePackId()), indexed.map(r -> r.sourcePackId()));
        // A directory ZipEntry is a valid lookup even though it is not a loadable image.
        if (direct.isPresent() && !location.getPath().endsWith("dir_n.png")) {
            try (var a = direct.get().open(); var b = indexed.orElseThrow().open()) {
                assertArrayEquals(a.readAllBytes(), b.readAllBytes());
            }
        }
    }

    @Test
    void agreesWithGetResourceAcrossPackTypesAndDomains() throws Exception {
        var manager = mixed();
        PBRResourceIndex.start(manager);
        for (String path : List.of("low_n.png", "low_s.png", "both_n.png", "both_s.png", "dir_n.png",
            "folder_s.png", "folder_n.png", "mixed_n.png", "deep/nested_n.png", "deep/missing_n.png",
            "stub_n.png", "stub_s.png", "absent_n.png", "absent_s.png", "tail_n.png",
            "other:textures/other_n.png", "other:textures/otherfolder_s.png", "other:textures/stub_n.png",
            "stubonly:textures/x_n.png", "stubonly:textures/y_n.png", "ghost:textures/low_n.png")) {
            assertAgrees(manager, id(path));
        }
        assertEquals("mid", manager.getResource(id("both_n.png")).orElseThrow().sourcePackId());
        assertEquals("stub", manager.getResource(id("low_s.png")).orElseThrow().sourcePackId());
    }

    @Test
    void presentLocationsAreReportedPresent() throws Exception {
        var manager = mixed();
        PBRResourceIndex.start(manager);
        for (String path : List.of("low_n.png", "folder_s.png", "stub_n.png", "other:textures/otherfolder_s.png")) {
            assertTrue(PBRResourceIndex.mayExist(manager, id(path)));
        }
        assertFalse(PBRResourceIndex.mayExist(manager, id("absent_n.png")));
    }

    @Test
    void nonPbrLocationsAreNeverRuledOut() throws Exception {
        var manager = mixed();
        for (String path : List.of("plain.png", "missing.png", "tail_n.png.bak", "notpbr_x.png")) {
            assertTrue(PBRResourceIndex.mayExist(manager, id(path)));
        }
    }

    @Test
    void lazyBuildWithoutStartAgrees() throws Exception {
        var manager = mixed();
        assertAgrees(manager, id("low_n.png"));
        assertAgrees(manager, id("absent_n.png"));
    }

    @Test
    void startAfterReloadSeesNewPacksAndClearsBothAnswers() throws Exception {
        var wrapper = new ReloadableResourceManager(PackType.CLIENT_RESOURCES);
        try {
            wrapper.createReload(Runnable::run, Runnable::run, CompletableFuture.completedFuture(Unit.INSTANCE),
                List.of(zip("before.zip", "assets/minecraft/textures/old_n.png"))).done().join();
            PBRResourceIndex.start(wrapper);
            assertTrue(PBRResourceIndex.mayExist(wrapper, id("old_n.png")));
            assertFalse(PBRResourceIndex.mayExist(wrapper, id("added_n.png")));
            // Unit tests do not load mixins: mirror the close/constructor hooks explicitly here.
            PBRResourceIndex.release(wrapper);
            wrapper.createReload(Runnable::run, Runnable::run, CompletableFuture.completedFuture(Unit.INSTANCE),
                List.of(zip("after.zip", "assets/minecraft/textures/added_n.png"))).done().join();
            PBRResourceIndex.start(wrapper);
            assertAgrees(wrapper, id("added_n.png"));
            assertAgrees(wrapper, id("old_n.png"));
        } finally {
            PBRResourceIndex.release(wrapper);
            wrapper.close();
        }
    }

    @Test
    void buildFailureDisablesIndex() {
        var manager = manager();
        var missing = new FilePackResources("missing", tmp.resolve("missing.zip").toFile(), false);
        var fallback = new FallbackResourceManager(PackType.CLIENT_RESOURCES, "minecraft");
        fallback.push(missing);
        manager.namespacedManagers.put("minecraft", fallback);
        PBRResourceIndex.start(manager);
        assertTrue(PBRResourceIndex.mayExist(manager, id("absent_n.png")));
        assertTrue(PBRResourceIndex.mayExist(manager, id("ghost:textures/absent_n.png")));
    }

    @Test
    void zipScanSharesThePacksOpenJar() throws Exception {
        var pack = zip("shared.zip", "assets/minecraft/textures/shared_n.png");
        var manager = manager(pack);
        var zip = pack.getOrCreateZipFile();
        PBRResourceIndex.start(manager);
        assertTrue(PBRResourceIndex.mayExist(manager, id("shared_n.png")));
        PBRResourceIndex.release(manager);
        assertSame(zip, pack.getOrCreateZipFile());
        assertNotNull(zip.getEntry("assets/minecraft/textures/shared_n.png"));
    }

    @Test
    void nativePackFiltersRespectPriorityOwnResourcesAndFilterOnlyNamespaces() throws Exception {
        var low = folder("low", "assets/minecraft/textures/blocked_n.png", "assets/other/textures/blocked_n.png");
        var high = folder("high", "assets/minecraft/textures/owned_s.png");
        Files.writeString(tmp.resolve("high/pack.mcmeta"), """
            {"filter":{"block":[{"namespace":"minecraft|other","path":"textures/.*"}]}}
            """);
        var manager = manager(low, high);
        PBRResourceIndex.start(manager);
        assertAgrees(manager, id("blocked_n.png"));
        assertAgrees(manager, id("other:textures/blocked_n.png"));
        assertAgrees(manager, id("owned_s.png"));
        assertFalse(PBRResourceIndex.mayExist(manager, id("blocked_n.png")));
        assertTrue(PBRResourceIndex.mayExist(manager, id("owned_s.png")));
    }

    @Test
    void filteringBelowAHigherOverrideDoesNotHideIt() throws Exception {
        var filter = folder("filter");
        Files.writeString(tmp.resolve("filter/pack.mcmeta"), """
            {"filter":{"block":[{"namespace":"minecraft","path":".*"}]}}
            """);
        var manager = manager(filter, zip("top.zip", "assets/minecraft/textures/top_s.png"));
        assertAgrees(manager, id("top_s.png"));
        assertTrue(PBRResourceIndex.mayExist(manager, id("top_s.png")));
    }

    @Test
    void nativeMetadataRemainsSelectedByTheResourceManager() throws Exception {
        var low = folder("metadata-low", "assets/minecraft/textures/animated_n.png");
        var high = folder("metadata-high", "assets/minecraft/textures/animated_n.png.mcmeta");
        Files.writeString(tmp.resolve("metadata-high/assets/minecraft/textures/animated_n.png.mcmeta"),
            "{\"animation\":{\"frametime\":7}}");
        var manager = manager(low, high);
        var direct = manager.getResource(id("animated_n.png")).orElseThrow();
        assertTrue(PBRResourceIndex.mayExist(manager, id("animated_n.png")));
        var indexed = manager.getResource(id("animated_n.png")).orElseThrow();
        var serializer = net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.SERIALIZER;
        assertEquals(7, direct.metadata().getSection(serializer).orElseThrow().getDefaultFrameTime());
        assertEquals(7, indexed.metadata().getSection(serializer).orElseThrow().getDefaultFrameTime());
    }

    @Test
    void metadataOnlyFilterDoesNotHideTheImage() throws Exception {
        var low = folder("filtered-meta-low", "assets/minecraft/textures/animated_n.png",
            "assets/minecraft/textures/animated_n.png.mcmeta");
        Files.writeString(tmp.resolve("filtered-meta-low/assets/minecraft/textures/animated_n.png.mcmeta"),
            "{\"animation\":{\"frametime\":7}}");
        var high = folder("filtered-meta-high");
        Files.writeString(tmp.resolve("filtered-meta-high/pack.mcmeta"), """
            {"filter":{"block":[{"namespace":"minecraft","path":".*[.]mcmeta"}]}}
            """);
        var manager = manager(low, high);
        assertAgrees(manager, id("animated_n.png"));
        assertTrue(PBRResourceIndex.mayExist(manager, id("animated_n.png")));
        assertTrue(manager.getResource(id("animated_n.png")).orElseThrow().metadata()
            .getSection(net.minecraft.client.resources.metadata.animation.AnimationMetadataSection.SERIALIZER).isEmpty());
    }

    @Test
    void unknownPackSubclassesUseTheirOverriddenLookup() throws Exception {
        var custom = new PathPackResources("custom", tmp, false) {
            @Override public Set<String> getNamespaces(PackType type) { return Set.of("minecraft"); }
            @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
                return location.equals(id("virtual_n.png")) ? () -> new ByteArrayInputStream(new byte[]{42}) : null;
            }
        };
        var manager = manager(custom);
        assertAgrees(manager, id("virtual_n.png"));
        assertAgrees(manager, id("absent_n.png"));
    }

    @Test
    void unknownManagersAndFallbackSubclassesCannotBeRuledOut() {
        var custom = new MultiPackResourceManager(PackType.CLIENT_RESOURCES, List.of()) {};
        managers.add(custom);
        assertTrue(PBRResourceIndex.mayExist(custom, id("virtual_n.png")));
        var manager = manager();
        manager.namespacedManagers.put("minecraft", new FallbackResourceManager(PackType.CLIENT_RESOURCES, "minecraft") {});
        assertTrue(PBRResourceIndex.mayExist(manager, id("virtual_n.png")));
    }

    @Test
    void directoryCaseAndNonTexturePbrPathsMatchNativeLookup() throws Exception {
        var pack = folder("paths", "assets/minecraft/textures/MIXED_N.PNG",
            "assets/minecraft/optifine/cit/item_n.png", "assets/other/nested/item_s.png");
        Files.createDirectories(tmp.resolve("paths/assets/minecraft/textures/dir_n.png"));
        var manager = manager(pack, zip("case.zip", "assets/minecraft/textures/UPPER_s.png",
            "data/minecraft/textures/server_n.png"));
        for (String path : List.of("mixed_n.png", "dir_n.png", "upper_s.png", "server_n.png",
            "minecraft:optifine/cit/item_n.png", "other:nested/item_s.png")) {
            assertAgrees(manager, id(path));
        }
    }

    @Test
    void forgePathPacksAreIndexedWithoutAssumingSubclassSemantics() throws Exception {
        folder("forge", "assets/minecraft/textures/forge_n.png");
        var pack = new net.minecraftforge.resource.PathPackResources("forge", false, tmp.resolve("forge"));
        var manager = manager(pack);
        assertAgrees(manager, id("forge_n.png"));
        assertAgrees(manager, id("missing_s.png"));
        var built = PBRResourceIndex.class.getDeclaredField("built");
        built.setAccessible(true);
        assertTrue(((java.util.Map<?, ?>) built.get(null)).containsKey(pack));
    }

    @Test
    void changingOwnerInvalidatesAnswersWithoutExplicitStart() throws Exception {
        var first = manager(folder("first", "assets/minecraft/textures/old_n.png"));
        var second = manager(folder("second", "assets/minecraft/textures/new_s.png"));
        assertTrue(PBRResourceIndex.mayExist(first, id("old_n.png")));
        assertFalse(PBRResourceIndex.mayExist(second, id("old_n.png")));
        assertTrue(PBRResourceIndex.mayExist(second, id("new_s.png")));
        assertTrue(PBRResourceIndex.mayExist(first, id("old_n.png")));
    }

    @Test
    void explicitStartRebuildsTheSamePackStackAfterTextureReload() throws Exception {
        var manager = manager(folder("same-stack", "assets/minecraft/textures/old_n.png"));
        assertTrue(PBRResourceIndex.mayExist(manager, id("old_n.png")));
        assertFalse(PBRResourceIndex.mayExist(manager, id("added_s.png")));
        Files.delete(tmp.resolve("same-stack/assets/minecraft/textures/old_n.png"));
        Files.writeString(tmp.resolve("same-stack/assets/minecraft/textures/added_s.png"), "added");
        PBRResourceIndex.start(manager);
        assertAgrees(manager, id("old_n.png"));
        assertAgrees(manager, id("added_s.png"));
    }

    @Test
    void scanFailureFallsBackAndNextStartRecovers() throws Exception {
        var pack = zip("badscan.zip", "assets/minecraft/textures/present_n.png");
        var manager = manager(pack);
        pack.close();
        try (var broken = new ZipFile(tmp.resolve("badscan.zip").toFile()) {
            @Override public Enumeration<? extends ZipEntry> entries() { throw new IllegalStateException("scan failure"); }
        }) {
            var field = FilePackResources.class.getDeclaredField("zipFile");
            field.setAccessible(true);
            field.set(pack, broken);
            PBRResourceIndex.start(manager);
            assertTrue(PBRResourceIndex.mayExist(manager, id("missing_n.png")));
            assertAgrees(manager, id("present_n.png"));
            field.set(pack, null);
        }
        PBRResourceIndex.start(manager);
        assertFalse(PBRResourceIndex.mayExist(manager, id("missing_n.png")));
        assertTrue(PBRResourceIndex.mayExist(manager, id("present_n.png")));
    }

    @Test
    void indexSkipsZipProbesForMissingPbrAndMemoizesUnknownPacks() throws Exception {
        var pack = zip("counted.zip", "assets/minecraft/textures/present_n.png");
        var custom = new StubPack(id("virtual_s.png"));
        var manager = manager(pack, custom);
        pack.close();
        var probes = new AtomicInteger();
        var counted = new ZipFile(tmp.resolve("counted.zip").toFile()) {
            @Override public ZipEntry getEntry(String name) { probes.incrementAndGet(); return super.getEntry(name); }
        };
        var field = FilePackResources.class.getDeclaredField("zipFile");
        field.setAccessible(true);
        field.set(pack, counted);
        PBRResourceIndex.start(manager);
        for (int i = 0; i < 1000; i++) assertFalse(PBRResourceIndex.mayExist(manager, id("absent_n.png")));
        assertEquals(0, probes.get(), "negative queries must not repeat ZipFile.getEntry");
        assertEquals(1, custom.probes.get(), "answers must also cache the custom-pack fallback");
        assertTrue(PBRResourceIndex.mayExist(manager, id("present_n.png")));
        assertTrue(PBRResourceIndex.mayExist(manager, id("virtual_s.png")));
        assertEquals(0, probes.get());
    }

    @Test
    void releaseWaitsForBorrowedZipScanBeforeItsOwnerCloses() throws Exception {
        var pack = zip("blocking.zip", "assets/minecraft/textures/present_n.png");
        var manager = manager(pack);
        pack.close();
        var scanning = new CountDownLatch(1);
        var resume = new CountDownLatch(1);
        var zip = new ZipFile(tmp.resolve("blocking.zip").toFile()) {
            @Override public Enumeration<? extends ZipEntry> entries() {
                scanning.countDown();
                try { if (!resume.await(5, TimeUnit.SECONDS)) throw new AssertionError("scan not resumed"); }
                catch (InterruptedException e) { throw new AssertionError(e); }
                return super.entries();
            }
        };
        var field = FilePackResources.class.getDeclaredField("zipFile");
        field.setAccessible(true);
        field.set(pack, zip);
        PBRResourceIndex.start(manager);
        assertTrue(scanning.await(5, TimeUnit.SECONDS));
        var releasing = new CountDownLatch(1);
        var released = CompletableFuture.runAsync(() -> {
            releasing.countDown();
            PBRResourceIndex.release(manager);
        });
        try {
            assertTrue(releasing.await(5, TimeUnit.SECONDS));
            assertFalse(released.isDone());
        } finally {
            resume.countDown();
        }
        released.get(5, TimeUnit.SECONDS);
        assertNotNull(zip.getEntry("assets/minecraft/textures/present_n.png"));
    }

    private static final class StubPack extends AbstractPackResources {
        private final Set<ResourceLocation> claims;
        final AtomicInteger probes = new AtomicInteger();

        StubPack(ResourceLocation... claims) {
            super("stub", false);
            this.claims = Set.of(claims);
        }

        @Override public IoSupplier<InputStream> getRootResource(String... elements) { return null; }
        @Override public IoSupplier<InputStream> getResource(PackType type, ResourceLocation location) {
            probes.incrementAndGet();
            return claims.contains(location) ? () -> new ByteArrayInputStream(new byte[]{1, 2, 3}) : null;
        }
        @Override public void listResources(PackType type, String ns, String path, ResourceOutput output) {
            throw new AssertionError("Do not enumerate unknown packs");
        }
        @Override public Set<String> getNamespaces(PackType type) {
            var result = new HashSet<String>();
            for (var claim : claims) result.add(claim.getNamespace());
            return result;
        }
        @Override public void close() {}
    }
}
