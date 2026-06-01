package com.moma.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Redis 缓存实现。使用 Jedis 客户端。
 * 连接失败时优雅降级，标记为不可用。
 *
 * <p>注意：此类不添加 @Component，由 CacheConfig 条件创建。</p>
 */
public class RedisCacheManager implements CacheManager {

    private static final Logger LOG = LoggerFactory.getLogger(RedisCacheManager.class);

    /** 缓存 key 前缀 */
    private static final String KEY_PREFIX = "ca:";

    private final JedisPool jedisPool;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicBoolean available = new AtomicBoolean(true);
    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);

    public RedisCacheManager(String host, int port, String password, int timeout,
                             int maxTotal, int maxIdle) {
        JedisPoolConfig poolConfig = new JedisPoolConfig();
        poolConfig.setMaxTotal(maxTotal);
        poolConfig.setMaxIdle(maxIdle);
        poolConfig.setMinIdle(1);
        poolConfig.setTestOnBorrow(true);
        poolConfig.setTestOnReturn(true);
        poolConfig.setMaxWait(Duration.ofMillis(timeout));

        if (password != null && !password.isBlank()) {
            this.jedisPool = new JedisPool(poolConfig, host, port, timeout, password);
        } else {
            this.jedisPool = new JedisPool(poolConfig, host, port, timeout);
        }

        // 初始化时检测连接
        checkAvailability();
        LOG.info("RedisCacheManager 初始化完成: {}:{}", host, port);
    }

    @Override
    public <T> T get(String key, Class<T> type) {
        if (!available.get()) {
            missCount.incrementAndGet();
            return null;
        }

        String redisKey = buildKey(key);
        try (Jedis jedis = jedisPool.getResource()) {
            String value = jedis.get(redisKey);
            if (value == null) {
                missCount.incrementAndGet();
                return null;
            }
            hitCount.incrementAndGet();
            return objectMapper.readValue(value, type);
        } catch (JedisException e) {
            LOG.warn("Redis GET 失败: key={}, error={}", key, e.getMessage());
            markUnavailable();
            missCount.incrementAndGet();
            return null;
        } catch (JsonProcessingException e) {
            LOG.warn("反序列化 Redis 缓存值失败: key={}, error={}", key, e.getMessage());
            missCount.incrementAndGet();
            return null;
        }
    }

    @Override
    public void set(String key, Object value, long ttlSeconds) {
        if (!available.get()) {
            LOG.warn("Redis 不可用，跳过缓存设置: key={}", key);
            return;
        }

        String redisKey = buildKey(key);
        try (Jedis jedis = jedisPool.getResource()) {
            String json = objectMapper.writeValueAsString(value);
            jedis.setex(redisKey, (int) ttlSeconds, json);
            LOG.debug("Redis 缓存已设置: key={}, ttl={}s", key, ttlSeconds);
        } catch (JedisException e) {
            LOG.warn("Redis SETEX 失败: key={}, error={}", key, e.getMessage());
            markUnavailable();
        } catch (JsonProcessingException e) {
            LOG.error("序列化 Redis 缓存值失败: key={}, error={}", key, e.getMessage());
        }
    }

    @Override
    public void delete(String key) {
        if (!available.get()) {
            return;
        }

        String redisKey = buildKey(key);
        try (Jedis jedis = jedisPool.getResource()) {
            jedis.del(redisKey);
            LOG.debug("Redis 缓存已删除: key={}", key);
        } catch (JedisException e) {
            LOG.warn("Redis DEL 失败: key={}, error={}", key, e.getMessage());
            markUnavailable();
        }
    }

    @Override
    public void clear() {
        if (!available.get()) {
            return;
        }

        try (Jedis jedis = jedisPool.getResource()) {
            // 只删除带 "ca:" 前缀的 key
            String pattern = KEY_PREFIX + "*";
            jedis.del(jedis.keys(pattern).toArray(new String[0]));
            LOG.info("Redis 缓存已清空 (前缀: {})", KEY_PREFIX);
        } catch (JedisException e) {
            LOG.warn("Redis 清空失败: error={}", e.getMessage());
            markUnavailable();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!available.get()) {
            return false;
        }
        // 定期检查连接是否恢复
        checkAvailability();
        return available.get();
    }

    @Override
    public CacheStats getStats() {
        long size = 0;
        if (available.get()) {
            try (Jedis jedis = jedisPool.getResource()) {
                size = jedis.dbSize();
            } catch (JedisException e) {
                LOG.debug("获取 Redis dbSize 失败: {}", e.getMessage());
            }
        }
        return new CacheStats(hitCount.get(), missCount.get(), size);
    }

    /**
     * 检查 Redis 连接是否可用。
     */
    private void checkAvailability() {
        try (Jedis jedis = jedisPool.getResource()) {
            String pong = jedis.ping();
            if ("PONG".equals(pong)) {
                if (!available.get()) {
                    LOG.info("Redis 连接已恢复");
                    available.set(true);
                }
            }
        } catch (JedisException e) {
            if (available.get()) {
                LOG.warn("Redis 连接检测失败: {}", e.getMessage());
                available.set(false);
            }
        }
    }

    /**
     * 标记 Redis 为不可用状态。
     */
    private void markUnavailable() {
        if (available.compareAndSet(true, false)) {
            LOG.warn("Redis 标记为不可用，后续操作将降级到本地缓存");
            // 注意：不销毁 jedisPool，保留恢复能力
        }
    }

    /**
     * 构建带前缀的 Redis key。
     */
    private String buildKey(String key) {
        return KEY_PREFIX + key;
    }

    /**
     * 关闭连接池（由 DI 容器生命周期管理调用）。
     */
    public void close() {
        LOG.info("关闭 Redis 连接池");
        jedisPool.close();
    }
}
