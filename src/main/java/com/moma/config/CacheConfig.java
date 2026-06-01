package com.moma.config;

import com.moma.di.Component;
import com.moma.di.Configuration;
import com.moma.di.Bean;
import com.moma.di.Value;
import com.moma.cache.CacheManager;
import com.moma.cache.LocalCacheManager;
import com.moma.cache.RedisCacheManager;
import com.moma.concurrent.ThreadPoolManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 缓存配置类。根据 Redis 可用性条件创建缓存管理器。
 * 对应 Spring {@code @Configuration}。
 */
@Configuration
public class CacheConfig {

    private static final Logger LOG = LoggerFactory.getLogger(CacheConfig.class);

    @Value("${redis.host:localhost}")
    private String redisHost;

    @Value("${redis.port:6379}")
    private int redisPort;

    @Value("${redis.password:}")
    private String redisPassword;

    @Value("${redis.timeout:2000}")
    private int redisTimeout;

    @Value("${redis.maxTotal:8}")
    private int redisMaxTotal;

    @Value("${redis.maxIdle:4}")
    private int redisMaxIdle;

    @Value("${cache.local.maxSize:1000}")
    private int localCacheMaxSize;

    @Value("${cache.local.defaultTtl:300}")
    private int localCacheDefaultTtl;

    /**
     * 创建缓存管理器。优先尝试 Redis，失败则降级到本地缓存。
     */
    @Bean
    public CacheManager cacheManager() {
        // 尝试创建 Redis 缓存
        try {
            RedisCacheManager redisCache = new RedisCacheManager(
                redisHost, redisPort, redisPassword,
                redisTimeout, redisMaxTotal, redisMaxIdle);
            if (redisCache.isAvailable()) {
                LOG.info("Redis 缓存已连接: {}:{}", redisHost, redisPort);
                return redisCache;
            }
            LOG.warn("Redis 不可用，降级到本地缓存");
        } catch (Exception e) {
            LOG.warn("Redis 连接失败 ({}:{}): {}，降级到本地缓存", redisHost, redisPort, e.getMessage());
        }

        // 降级到本地缓存
        LocalCacheManager localCache = new LocalCacheManager();
        LOG.info("使用本地内存缓存 (maxSize={}, defaultTtl={}s)", localCacheMaxSize, localCacheDefaultTtl);
        return localCache;
    }
}
