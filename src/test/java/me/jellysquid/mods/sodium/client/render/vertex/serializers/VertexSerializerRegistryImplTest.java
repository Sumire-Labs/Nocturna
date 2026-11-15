package me.jellysquid.mods.sodium.client.render.vertex.serializers;

import it.unimi.dsi.fastutil.longs.Long2ReferenceMap;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.caffeinemc.mods.sodium.api.vertex.serializer.VertexSerializer;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.StampedLock;

import static org.junit.jupiter.api.Assertions.*;

class VertexSerializerRegistryImplTest {
    @Test
    void seesConstructorRegistrationsAndRefreshesAfterCacheGrowth() throws Exception {
        var registry = new VertexSerializerRegistryImpl();
        var first = serializer();
        var second = serializer();
        register(registry, -1, Integer.MIN_VALUE, first);
        assertSame(first, registry.get(format(-1), format(Integer.MIN_VALUE)));
        // Oculus registers serializers during construction. Those must be included in the first snapshot.
        register(registry, Integer.MIN_VALUE, -1, second);
        assertSame(second, registry.get(format(Integer.MIN_VALUE), format(-1)));
        assertSame(first, registry.get(format(-1), format(Integer.MIN_VALUE)));

        var find = VertexSerializerRegistryImpl.class.getDeclaredMethod("find", long.class);
        find.setAccessible(true);
        assertNull(find.invoke(registry, 42L));
    }

    @Test
    void retainsSerializerIdentityWhileOtherThreadsGrowTheCache() throws Exception {
        var registry = new VertexSerializerRegistryImpl();
        VertexSerializer[] serializers = new VertexSerializer[512];
        for (int i = 0; i < serializers.length; i++) {
            serializers[i] = serializer();
        }
        register(registry, 0, 0, serializers[0]);
        var published = new AtomicInteger();
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(5);
        try {
            var tasks = new ArrayList<Callable<Void>>();
            tasks.add(() -> {
                start.await();
                for (int i = 1; i < serializers.length; i++) {
                    register(registry, i, 0, serializers[i]);
                    published.set(i);
                }
                return null;
            });
            for (int thread = 0; thread < 4; thread++) {
                tasks.add(() -> {
                    start.await();
                    for (int iteration = 0; iteration < 10000; iteration++) {
                        int id = iteration % (published.get() + 1);
                        assertSame(serializers[id], registry.get(format(id), format(0)));
                    }
                    return null;
                });
            }
            var futures = new ArrayList<java.util.concurrent.Future<Void>>();
            for (var task : tasks) {
                futures.add(executor.submit(task));
            }
            start.countDown();
            for (var future : futures) {
                future.get();
            }
            for (int i = 0; i < serializers.length; i++) {
                assertSame(serializers[i], registry.get(format(i), format(0)));
            }
        } finally {
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private static void register(VertexSerializerRegistryImpl registry, int src, int dst,
                                 VertexSerializer serializer) throws Exception {
        var cacheField = VertexSerializerRegistryImpl.class.getDeclaredField("cache");
        cacheField.setAccessible(true);
        var cache = (Long2ReferenceMap<VertexSerializer>) cacheField.get(registry);
        var lockField = VertexSerializerRegistryImpl.class.getDeclaredField("lock");
        lockField.setAccessible(true);
        var lock = (StampedLock) lockField.get(registry);
        long stamp = lock.writeLock();
        try {
            cache.put(Integer.toUnsignedLong(src) | (Integer.toUnsignedLong(dst) << 32), serializer);
        } finally {
            lock.unlockWrite(stamp);
        }
    }

    private static VertexFormatDescription format(int id) {
        return (VertexFormatDescription) Proxy.newProxyInstance(VertexFormatDescription.class.getClassLoader(),
                new Class<?>[] {VertexFormatDescription.class}, (proxy, method, args) -> {
                    if (method.getName().equals("id")) {
                        return id;
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }

    private static VertexSerializer serializer() {
        return new VertexSerializer() {
            @Override
            public void serialize(long srcBuffer, long dstBuffer, int count) {
                fail("A cached serializer should not be invoked during lookup");
            }
        };
    }
}
