package com.moma.service;

import com.moma.cache.CacheManager;
import com.moma.cache.Cacheable;
import com.moma.di.Component;
import com.moma.di.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 缓存业务服务。封装 LLM 响应缓存、工具结果缓存等业务场景。
 */
@Component
public class CacheService {

    private static final Logger LOG = LoggerFactory.getLogger(CacheService.class);

    private final CacheManager cacheManager;

    @Inject
    public CacheService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 获取缓存的 LLM 响应。
     */
    public String getCachedLlmResponse(String prompt) {
        String key = "llm:" + hashKey(prompt);
        return cacheManager.get(key, String.class);
    }

    /**
     * 缓存 LLM 响应。
     */
    public void cacheLlmResponse(String prompt, String response) {
        String key = "llm:" + hashKey(prompt);
        cacheManager.set(key, response, 300);
    }

    /**
     * 获取缓存的工具结果。
     */
    public String getCachedToolResult(String toolName, String args) {
        String key = "tool:" + toolName + ":" + hashKey(args);
        return cacheManager.get(key, String.class);
    }

    /**
     * 缓存工具结果。
     */
    public void cacheToolResult(String toolName, String args, String result) {
        String key = "tool:" + toolName + ":" + hashKey(args);
        cacheManager.set(key, result, 60);
    }

    /**
     * 清空缓存。
     */
    public void clearCache() {
        cacheManager.clear();
        LOG.info("缓存已清空");
    }

    /**
     * 获取缓存统计。
     */
    public CacheManager.CacheStats getStats() {
        return cacheManager.getStats();
    }

    /**
     * 缓存是否可用。
     */
    public boolean isCacheAvailable() {
        return cacheManager.isAvailable();
    }

    /**
     * 对字符串进行 SHA-256 哈希，用于缓存键。
     */
    private String hashKey(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return Integer.toHexString(input.hashCode());
        }
    }
}
