package com.moma.cache;

import com.moma.di.Component;
import com.moma.di.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Supplier;

/**
 * 缓存切面。在 CacheService 中手动调用，不依赖 AOP 框架。
 * 提供 before/after 模式的缓存操作方法。
 */
@Component
public class CacheAspect {

    private static final Logger LOG = LoggerFactory.getLogger(CacheAspect.class);

    private final CacheManager cacheManager;

    @Inject
    public CacheAspect(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 从缓存中获取值。
     */
    @SuppressWarnings("unchecked")
    public <T> T getFromCache(String key, Class<T> type) {
        if (!cacheManager.isAvailable()) {
            return null;
        }
        T value = cacheManager.get(key, type);
        if (value != null) {
            LOG.debug("缓存命中: key={}", key);
        } else {
            LOG.debug("缓存未命中: key={}", key);
        }
        return value;
    }

    /**
     * 将值存入缓存。
     */
    public void putInCache(String key, Object value, long ttl) {
        if (!cacheManager.isAvailable()) {
            return;
        }
        cacheManager.set(key, value, ttl);
        LOG.debug("缓存已写入: key={}, ttl={}s", key, ttl);
    }

    /**
     * 环绕缓存：先查缓存，命中直接返回；未命中执行 supplier 获取结果并缓存。
     *
     * @param key      缓存 key
     * @param supplier 值提供者（缓存未命中时执行）
     * @param type     值类型
     * @param ttl      缓存过期时间（秒）
     * @param <T>      值类型
     * @return 缓存值或 supplier 计算结果
     */
    public <T> T around(String key, Supplier<T> supplier, Class<T> type, long ttl) {
        // 先查缓存
        if (cacheManager.isAvailable()) {
            T cached = cacheManager.get(key, type);
            if (cached != null) {
                LOG.debug("[CacheAspect] 缓存命中: key={}", key);
                return cached;
            }
            LOG.debug("[CacheAspect] 缓存未命中，执行 supplier: key={}", key);
        }

        // 缓存未命中或不可用，执行原始逻辑
        T result = supplier.get();

        // 写入缓存（仅当缓存可用且结果非空）
        if (result != null && cacheManager.isAvailable()) {
            cacheManager.set(key, result, ttl);
            LOG.debug("[CacheAspect] 结果已缓存: key={}", key);
        }

        return result;
    }
}
