package com.moma.cache;

/**
 * 缓存管理器接口。策略模式。
 * 支持 Redis 和本地两种实现，运行时自动降级。
 */
public interface CacheManager {

    /** 获取缓存值 */
    <T> T get(String key, Class<T> type);

    /** 设置缓存 */
    void set(String key, Object value, long ttlSeconds);

    /** 删除缓存 */
    void delete(String key);

    /** 清空所有缓存 */
    void clear();

    /** 缓存是否可用 */
    boolean isAvailable();

    /** 获取缓存统计 */
    CacheStats getStats();

    /** 缓存统计值 */
    record CacheStats(long hitCount, long missCount, long size) {}
}
