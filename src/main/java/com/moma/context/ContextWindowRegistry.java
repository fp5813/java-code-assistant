package com.moma.context;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 模型上下文窗口注册表。
 * <p>
 * 管理各 AI 模型的上下文窗口大小，支持通配符模式匹配。
 * 用于 AgentLoop 在裁剪消息历史时参考不同模型的上限。
 * </p>
 *
 * <pre>
 * 使用示例:
 *   registry.register("qwen2.5-coder:*", 32768);
 *   int window = registry.getContextWindow("qwen2.5-coder:7b"); // → 32768
 * </pre>
 */
public class ContextWindowRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ContextWindowRegistry.class);

    /** 默认上下文窗口大小（8K，保守值） */
    public static final int DEFAULT_WINDOW = 8192;

    /** 安全边际比例：实际使用的 token 不超过窗口的 85% */
    public static final double SAFETY_MARGIN = 0.85;

    private final List<ContextWindowEntry> entries = new CopyOnWriteArrayList<>();

    public ContextWindowRegistry() {
        registerBuiltinModels();
    }

    // ──────────────────────────────────────────────
    // 注册 API
    // ──────────────────────────────────────────────

    /**
     * 注册一个模型模式及其上下文窗口大小。
     *
     * @param modelPattern     模型名模式，支持 {@code *} 通配符（如 {@code "qwen2.5-coder:*"}）
     * @param contextWindowSize 上下文窗口大小（Token 数）
     */
    public void register(String modelPattern, int contextWindowSize) {
        String regex = toRegex(modelPattern);
        entries.add(new ContextWindowEntry(modelPattern, regex, contextWindowSize));
        LOG.debug("注册模型上下文窗口: pattern={}, size={}", modelPattern, contextWindowSize);
    }

    /**
     * 查询指定模型的上下文窗口大小。
     * 优先精确匹配，再按注册顺序匹配通配符，最后返回默认值。
     *
     * @param modelName 完整模型名（如 "qwen2.5-coder:7b"）
     * @return 上下文窗口大小（Token 数），最低 2048
     */
    public int getContextWindow(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return DEFAULT_WINDOW;
        }

        for (ContextWindowEntry entry : entries) {
            if (modelName.matches(entry.regex())) {
                return Math.max(entry.windowSize(), 2048);
            }
        }

        LOG.debug("未找到模型 '{}' 的上下文窗口配置，使用默认值 {}", modelName, DEFAULT_WINDOW);
        return DEFAULT_WINDOW;
    }

    /**
     * 获取安全使用的最大 Token 数（窗口大小 × 安全边际）。
     */
    public int getSafeMaxTokens(String modelName) {
        return (int) (getContextWindow(modelName) * SAFETY_MARGIN);
    }

    /**
     * 获取注册表描述文本（供 /status 显示）。
     */
    public String describe() {
        StringBuilder sb = new StringBuilder();
        sb.append("模型上下文窗口配置:\n");
        for (ContextWindowEntry entry : entries) {
            sb.append(String.format("  %s → %d%n", entry.pattern(), entry.windowSize()));
        }
        sb.append(String.format("  (默认) → %d%n", DEFAULT_WINDOW));
        return sb.toString();
    }

    /**
     * 获取所有条目数量。
     */
    public int size() {
        return entries.size();
    }

    // ──────────────────────────────────────────────
    // 内置模型注册
    // ──────────────────────────────────────────────

    private void registerBuiltinModels() {
        // ── 本地模型（Ollama） ──
        register("qwen2.5-coder:*", 32_768);    // Qwen 2.5 Coder 32K
        register("qwen2.5:*", 32_768);           // Qwen 2.5 32K
        register("qwen:*", 32_768);              // Qwen 系列 32K
        register("deepseek-coder:*", 65_536);    // DeepSeek Coder V2 64K
        register("deepseek-chat:*", 65_536);     // DeepSeek Chat V2 64K
        register("deepseek-r1:*", 65_536);       // DeepSeek R1 64K
        register("llama3.1:*", 131_072);         // Llama 3.1 128K
        register("llama3:*", 8_192);             // Llama 3 8K
        register("llama2:*", 4_096);             // Llama 2 4K
        register("codellama:*", 16_384);         // CodeLlama 16K
        register("mistral:*", 32_768);           // Mistral 32K
        register("mixtral:*", 32_768);           // Mixtral 32K
        register("phi3:*", 131_072);             // Phi-3 128K
        register("phi:*", 128_000);              // Phi 系列
        register("gemma:*", 8_192);              // Gemma 8K
        register("gemma2:*", 8_192);             // Gemma 2 8K
        register("starcoder:*", 16_384);         // StarCoder 16K
        register("codegemma:*", 8_192);          // CodeGemma 8K
        register("nomic-embed-text:*", 2_048);   // 嵌入模型 2K
        register("llava:*", 4_096);              // LLaVA 4K

        // ── 云端模型 ──
        register("gpt-4o*", 128_000);            // GPT-4o / 4o-mini 128K
        register("gpt-4-turbo*", 128_000);       // GPT-4 Turbo 128K
        register("gpt-4*", 8_192);               // GPT-4 8K
        register("gpt-3.5-turbo*", 16_384);      // GPT-3.5 Turbo 16K
        register("o1*", 200_000);                // o1 200K
        register("o3*", 200_000);                // o3 200K
        register("claude-*", 200_000);           // Claude 系列 200K
        register("deepseek-*", 65_536);          // DeepSeek 通用 64K
        register("glm-*", 131_072);              // GLM 系列 128K
        register("moonshot*", 131_072);          // Moonshot 128K
        register("yi-*", 32_768);                // Yi 系列 32K
        register("command-r*", 131_072);         // Command R 128K

        LOG.info("已注册 {} 个模型上下文窗口配置", entries.size());
    }

    // ──────────────────────────────────────────────
    // 内部工具
    // ──────────────────────────────────────────────

    /**
     * 将通配符模式转换为正则表达式。
     * {@code *} → {@code .*}, 并转义其他特殊字符。
     */
    static String toRegex(String pattern) {
        if (pattern == null) return ".*";
        // 转义正则特殊字符（保留 *）
        String regex = pattern
            .replaceAll("([.+^${}()|\\[\\]\\\\])", "\\\\$1")
            .replace("*", ".*");
        return "^" + regex + "$";
    }

    /**
     * 上下文窗口条目。
     */
    private record ContextWindowEntry(
        String pattern,
        String regex,
        int windowSize
    ) {}
}
