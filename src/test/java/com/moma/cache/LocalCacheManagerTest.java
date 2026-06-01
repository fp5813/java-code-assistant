package com.moma.cache;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 本地缓存管理器单元测试。验证缓存的基本 CRUD、过期、统计等功能。
 */
class LocalCacheManagerTest {

    @Test
    void testSetAndGet() {
        LocalCacheManager cache = new LocalCacheManager();
        cache.set("key1", "value1", 300);
        String result = cache.get("key1", String.class);
        assertEquals("value1", result);
    }

    @Test
    void testGetNonExistent() {
        LocalCacheManager cache = new LocalCacheManager();
        String result = cache.get("nonExistent", String.class);
        assertNull(result);
    }

    @Test
    void testGetDeleted() {
        LocalCacheManager cache = new LocalCacheManager();
        cache.set("key1", "value1", 300);
        cache.delete("key1");
        assertNull(cache.get("key1", String.class));
    }

    @Test
    void testClear() {
        LocalCacheManager cache = new LocalCacheManager();
        cache.set("key1", "value1", 300);
        cache.set("key2", "value2", 300);
        cache.clear();
        assertNull(cache.get("key1", String.class));
        assertNull(cache.get("key2", String.class));
    }

    @Test
    void testIsAvailable() {
        LocalCacheManager cache = new LocalCacheManager();
        assertTrue(cache.isAvailable(), "本地缓存始终可用");
    }

    @Test
    void testCacheStats() {
        LocalCacheManager cache = new LocalCacheManager();
        CacheManager.CacheStats stats = cache.getStats();
        assertEquals(0, stats.hitCount());
        assertEquals(0, stats.missCount());
        assertEquals(0, stats.size());

        cache.set("k", "v", 300);
        stats = cache.getStats();
        assertEquals(1, stats.size());
    }

    @Test
    void testHitAndMissStats() {
        LocalCacheManager cache = new LocalCacheManager();
        cache.set("k", "v", 300);

        cache.get("k", String.class); // hit
        cache.get("k", String.class); // hit
        cache.get("nonexistent", String.class); // miss

        CacheManager.CacheStats stats = cache.getStats();
        assertEquals(2, stats.hitCount());
        assertEquals(1, stats.missCount());
    }

    @Test
    void testIntegerValue() {
        LocalCacheManager cache = new LocalCacheManager();
        cache.set("age", 25, 300);
        Integer result = cache.get("age", Integer.class);
        assertEquals(25, result);
    }

    @Test
    void testObjectValue() {
        LocalCacheManager cache = new LocalCacheManager();
        cache.set("person", new Person("Alice", 30), 300);
        Person result = cache.get("person", Person.class);
        assertNotNull(result);
        assertEquals("Alice", result.name());
        assertEquals(30, result.age());
    }

    @Test
    void testExpiration() throws InterruptedException {
        LocalCacheManager cache = new LocalCacheManager();
        cache.set("ephemeral", "gone", 1); // 1 秒 TTL
        assertNotNull(cache.get("ephemeral", String.class));

        Thread.sleep(1100); // 等待过期
        assertNull(cache.get("ephemeral", String.class));
    }

    record Person(String name, int age) {}
}
