package org.embeddedt.embeddium.impl.render.chunk.lists;

import org.embeddedt.embeddium.impl.gl.arena.staging.StagingBuffer;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.AsyncOcclusionMode;
import org.embeddedt.embeddium.impl.render.chunk.occlusion.SectionLattice;
import org.embeddedt.embeddium.impl.render.chunk.region.RenderRegion;
import org.embeddedt.embeddium.impl.render.viewport.Viewport;
import org.joml.Vector3d;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(10)
class RenderListPoolTest {
    private static volatile ChunkRenderList allocationSink;

    @Test
    void pooledCollectorsMatchFreshOutputAcrossEmptyAndChangingGenerations() throws Exception {
        var pool = new RenderListPool();
        var random = new java.util.Random(2193);
        var regions = new RenderRegion[]{region(0), region(1), region(2)};
        for (int generation = 0; generation < 80; generation++) {
            var fresh = new VisibleChunkCollector(null, generation, regions.length, 32, new org.joml.Vector3i());
            var recycled = new VisibleChunkCollector(null, generation, regions.length, 32, new org.joml.Vector3i());
            recycled.setRecycled(pool.beginSearch(regions.length));
            for (var region : regions) {
                if (random.nextBoolean()) continue;
                var a = createList(fresh, region);
                var b = createList(recycled, region);
                int count = random.nextInt(RenderRegion.REGION_SIZE + 1);
                for (int i = 0; i < count; i++) {
                    int flags = random.nextInt(16);
                    a.add(i, flags);
                    b.add(i, flags);
                }
            }
            for (boolean reverse : new boolean[]{false, true}) {
                var expected = fresh.createRenderLists().iterator(reverse);
                var actual = recycled.createRenderLists().iterator(reverse);
                while (expected.hasNext()) {
                    assertTrue(actual.hasNext());
                    var a = expected.next();
                    var b = actual.next();
                    assertSame(a.getRegion(), b.getRegion());
                    assertEquals(a.size(), b.size());
                    assertEquals(indices(a.sectionsWithGeometryIterator()), indices(b.sectionsWithGeometryIterator()));
                    assertEquals(indices(a.sectionsWithSpritesIterator()), indices(b.sectionsWithSpritesIterator()));
                    assertEquals(indices(a.sectionsWithEntitiesIterator()), indices(b.sectionsWithEntitiesIterator()));
                    assertEquals(indices(a.sectionsNeedingDynamicSortIterator()), indices(b.sectionsNeedingDynamicSortIterator()));
                }
                assertFalse(actual.hasNext());
            }
        }
    }

    private static java.util.List<Integer> indices(org.embeddedt.embeddium.impl.util.iterator.ByteIterator iterator) {
        var result = new java.util.ArrayList<Integer>();
        if (iterator != null) while (iterator.hasNext()) result.add(iterator.nextByteAsInt());
        return result;
    }

    @Test
    void steadyStateReuseReducesAllocations() throws Exception {
        var bean = (com.sun.management.ThreadMXBean) java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.jupiter.api.Assumptions.assumeTrue(bean.isThreadAllocatedMemorySupported());
        bean.setThreadAllocatedMemoryEnabled(true);
        var regions = new RenderRegion[16];
        for (int i = 0; i < regions.length; i++) regions[i] = region(i);
        var pool = new RenderListPool();
        allocateSearches(regions, pool, 2000);
        allocateSearches(regions, null, 2000);
        long thread = Thread.currentThread().getId();
        long before = bean.getThreadAllocatedBytes(thread);
        allocateSearches(regions, null, 5000);
        long fresh = bean.getThreadAllocatedBytes(thread) - before;
        before = bean.getThreadAllocatedBytes(thread);
        allocateSearches(regions, pool, 5000);
        long reused = bean.getThreadAllocatedBytes(thread) - before;
        assertTrue(reused < fresh / 10, "Steady-state reuse should avoid region list/array allocations");
        System.out.printf("Render-list allocation: fresh=%.1f B/search, reused=%.1f B/search (16 regions)%n",
                fresh / 5000.0, reused / 5000.0);
    }

    private static void allocateSearches(RenderRegion[] regions, RenderListPool pool, int iterations) {
        for (int i = 0; i < iterations; i++) {
            var slot = pool == null ? null : pool.beginSearch(regions.length);
            for (var region : regions) {
                var list = pool == null ? new ChunkRenderList(region) : RenderListPool.acquire(slot, region);
                list.add(i & 255, 15);
                allocationSink = list;
            }
        }
    }
    @Test
    void alternatesGenerationsAndGrowsWithoutLosingCachedLists() throws Exception {
        var pool = new RenderListPool();
        var first = pool.beginSearch(2);
        var list = RenderListPool.acquire(first, region(1));
        assertNotSame(first, pool.beginSearch(2));
        var grown = pool.beginSearch(16);
        assertTrue(grown.length >= 16);
        assertSame(list, grown[1]);
        pool.beginSearch(1);
        assertSame(grown, pool.beginSearch(1));
    }

    @Test
    void resetsEveryCounterAndNeverExposesStaleSectionIndices() throws Exception {
        var slot = new ChunkRenderList[2];
        var region = region(1);
        var list = RenderListPool.acquire(slot, region);
        for (int i = 0; i < RenderRegion.REGION_SIZE; i++) list.add(i, 15);
        assertEquals(RenderRegion.REGION_SIZE, list.size());
        var array = list.getSectionsWithGeometry();
        assertSame(list, RenderListPool.acquire(slot, region));
        assertSame(array, list.getSectionsWithGeometry());
        assertEquals(0, list.size());
        assertNull(list.sectionsWithGeometryIterator());
        assertNull(list.sectionsWithSpritesIterator());
        assertNull(list.sectionsWithEntitiesIterator());
        assertNull(list.sectionsNeedingDynamicSortIterator());
        list.add(217, 15);
        assertEquals(1, list.size());
        for (var iterator : new org.embeddedt.embeddium.impl.util.iterator.ByteIterator[]{
                list.sectionsWithGeometryIterator(), list.sectionsWithSpritesIterator(),
                list.sectionsWithEntitiesIterator(), list.sectionsNeedingDynamicSortIterator()}) {
            assertEquals(217, iterator.nextByteAsInt());
            assertFalse(iterator.hasNext());
        }
        var mutableCounters = Arrays.stream(ChunkRenderList.class.getDeclaredFields())
                .filter(field -> field.getType() == int.class && !Modifier.isStatic(field.getModifiers())
                        && !Modifier.isFinal(field.getModifiers())).map(java.lang.reflect.Field::getName)
                .collect(Collectors.toSet());
        assertEquals(Set.of("sectionsWithGeometryCount", "sectionsWithSpritesCount", "sectionsWithEntitiesCount",
                "sectionsNeedingDynamicSortCount", "size"), mutableCounters);
    }

    @Test
    void replacesARecycledRegionIdAndKeepsTerrainAndShadowPoolsSeparate() throws Exception {
        var terrain = new RenderListPool();
        var shadow = new RenderListPool();
        var slot = terrain.beginSearch(3);
        var original = RenderListPool.acquire(slot, region(2));
        var replacementRegion = region(2);
        var replacement = RenderListPool.acquire(slot, replacementRegion);
        assertNotSame(original, replacement);
        assertSame(replacementRegion, replacement.getRegion());
        assertNotSame(replacement, RenderListPool.acquire(shadow.beginSearch(3), replacementRegion));
    }

    @Test
    void publishedListSurvivesTheNextAsyncSearchAndRejectedSubmissionDoesNotAdvancePool() throws Exception {
        var graph = new SectionGraph(0, 1, AsyncOcclusionMode.EVERYTHING, false, false);
        var manager = new RenderListManager(graph, false, AsyncOcclusionMode.EVERYTHING, null);
        var region = region(0);
        var release = new CountDownLatch(1);
        var started = new CountDownLatch(1);
        try {
            submit(manager, 1, collector -> populate(collector, region, 11));
            manager.finishPreviousGraphUpdate();
            var published = manager.getRenderLists().iterator().next();
            submit(manager, 2, collector -> {
                populate(collector, region, 22);
                started.countDown();
                try {
                    if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Search was not released");
                } catch (InterruptedException e) {
                    throw new AssertionError(e);
                }
                return SectionLattice.VisibilitySnapshot.EMPTY;
            });
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertSame(published, manager.getRenderLists().iterator().next());
            assertEquals(11, published.sectionsWithGeometryIterator().nextByteAsInt());
            assertThrows(IllegalStateException.class,
                    () -> submit(manager, 99, collector -> populate(collector, region, 99)));
            release.countDown();
            manager.finishPreviousGraphUpdate();
            var second = manager.getRenderLists().iterator().next();
            assertNotSame(published, second);
            assertEquals(22, second.sectionsWithGeometryIterator().nextByteAsInt());
            submit(manager, 3, collector -> populate(collector, region, 33));
            manager.finishPreviousGraphUpdate();
            assertSame(published, manager.getRenderLists().iterator().next());
            assertEquals(33, published.sectionsWithGeometryIterator().nextByteAsInt());
            assertEquals(22, second.sectionsWithGeometryIterator().nextByteAsInt());
        } finally {
            release.countDown();
            manager.destroy();
            graph.destroy();
        }
    }

    private static SectionLattice.VisibilitySnapshot populate(VisibleChunkCollector collector,
                                                              RenderRegion region, int section) {
        try {
            createList(collector, region).add(section, 15);
            return SectionLattice.VisibilitySnapshot.EMPTY;
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static ChunkRenderList createList(VisibleChunkCollector collector, RenderRegion region)
            throws ReflectiveOperationException {
        var create = VisibleChunkCollector.class.getDeclaredMethod("createRenderList", RenderRegion.class);
        create.setAccessible(true);
        return (ChunkRenderList) create.invoke(collector, region);
    }

    private static void submit(RenderListManager manager, int frame,
                               Function<VisibleChunkCollector, SectionLattice.VisibilitySnapshot> search) throws Exception {
        var submit = RenderListManager.class.getDeclaredMethod("submitSearch", int.class, int.class, int.class,
                Viewport.class, Function.class);
        submit.setAccessible(true);
        try {
            submit.invoke(manager, frame, 1, 32, new Viewport((a, b, c, d, e, f) -> true, new Vector3d()), search);
        } catch (InvocationTargetException e) {
            if (e.getCause() instanceof RuntimeException failure) throw failure;
            throw e;
        }
    }

    private static RenderRegion region(int id) throws Exception {
        var constructor = RenderRegion.class.getDeclaredConstructor(int.class, int.class, int.class, int.class,
                StagingBuffer.class, int.class);
        constructor.setAccessible(true);
        return constructor.newInstance(0, 0, 0, id, null, 32);
    }
}
