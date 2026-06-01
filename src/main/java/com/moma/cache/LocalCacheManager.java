package com.moma.cache;

import com.moma.di.Component;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 本地内存缓存。使用 ConcurrentHashMap + 惰性过期。
 * 作为 Redis 不可用时的后备方案。
 */
@Component
public class LocalCacheManager implements CacheManager {

    private static final Logger LOG = LoggerFactory.getLogger(LocalCacheManager.class);

    /** 默认 TTL（秒） */
    private static final long DEFAULT_TTL_SECONDS = 300;

    /** 最大条目数 */
    private static final int MAX_ENTRIES = 1000;

    private final ConcurrentHashMap<String, CacheEntry> cache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        CacheEntry entry = cache.get(key);
        if (entry == null) {
            missCount.incrementAndGet();
            return null;
        }

        // 惰性过期检查
        if (System.currentTimeMillis() > entry.expiryTime) {
            cache.remove(key);
            missCount.incrementAndGet();
            LOG.debug("缓存过期: key={}", key);
            return null;
        }

        hitCount.incrementAndGet();
        try {
            return objectMapper.readValue(entry.value, type);
        } catch (JsonProcessingException e) {
            LOG.warn("反序列化缓存值失败: key={}, error={}", key, e.getMessage());
            cache.remove(key);
            return null;
        }
    }

    @Override
    public void set(String key, Object value, long ttlSeconds) {
        // 检查容量，超出时清空最旧的条目（简化策略：直接清空全部）
        if (cache.size() >= MAX_ENTRIES) {
            LOG.warn("本地缓存已达上限 ({}), 执行清空", MAX_ENTRIES);
            cache.clear();
        }

        try {
            String json = objectMapper.writeValueAsString(value);
            long expiryTime = System.currentTimeMillis() + (ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS) * 1000;
            cache.put(key, new CacheEntry(json, expiryTime));
            LOG.debug("缓存已设置: key={}, ttl={}s", key, ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS);
        } catch (JsonProcessingException e) {
            LOG.error("序列化缓存值失败: key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        cache.remove(key);
        LOG.debug("缓存已删除: key={}", key);
    }

    @Override
    public void clear() {
        cache.clear();
        hitCount.set(0);
        missCount.set(0);
        LOG.info("本地缓存已清空");
    }

    @Override
    public boolean isAvailable() {
        return true; // 本地缓存始终可用
    }

    @Override
    public CacheStats getStats() {
        return new CacheStats(hitCount.get(), missCount.get(), cache.size());
    }

    /**
     * 缓存条目，包含 JSON 序列化后的值和过期时间。
     */
    private record CacheEntry(String value, long expiryTime) {}
}
