package com.moma.model;

import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Provider 注册中心。
 * 管理多个 AI 模型提供商，支持运行时热切换。
 *
 * <p>对应 MiniClaude 的 Provider 切换机制（{@code /provider} 命令）。</p>
 */
public class ProviderRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ProviderRegistry.class);

    /** 当前活跃的 Provider 名称 */
    private volatile String activeProvider;

    /** 当前活跃的模型名称 */
    private volatile String activeModel;

    /** 当前活跃的 ChatLanguageModel 实例 */
    private volatile ChatLanguageModel currentModel;

    /** Provider 名称 → Provider 实例 */
    private final Map<String, ModelProvider> providers = new ConcurrentHashMap<>();

    /** Provider 名称 → 配置 */
    private final Map<String, ModelProvider.ModelConfig> configs = new ConcurrentHashMap<>();

    /** Provider 名称 → ProviderConfig（原始 JSON 配置） */
    private final Map<String, ProviderConfig> rawConfigs = new ConcurrentHashMap<>();

    // ─── 注册 ───

    /**
     * 注册一个 Provider。
     */
    public void register(ModelProvider provider) {
        providers.put(provider.name(), provider);
        LOG.info("注册 Provider: {}", provider.name());
    }

    /**
     * 从 settings.json 的 providers 配置块加载 Provider 配置。
     */
    public void loadConfigs(Map<String, ProviderConfig> providerConfigs) {
        if (providerConfigs == null) return;

        for (Map.Entry<String, ProviderConfig> entry : providerConfigs.entrySet()) {
            String name = entry.getKey();
            ProviderConfig pc = entry.getValue();
            rawConfigs.put(name, pc);

            ModelProvider.ModelConfig config = ModelProvider.ModelConfig.builder()
                .baseUrl(pc.getBaseUrl())
                .apiKey(pc.getApiKey())
                .modelName(pc.getDefaultModel())
                .build();
            configs.put(name, config);
        }
    }

    /**
     * 切换到指定的 Provider 和模型。
     *
     * @param providerName  Provider 名称
     * @param modelName     模型名称（null 则使用默认模型）
     * @return 切换说明
     */
    public String switchTo(String providerName, String modelName) {
        // 查找 Provider
        ModelProvider provider = providers.get(providerName);
        if (provider == null) {
            // 如果没注册，但有配置，自动注册一个默认实现
            ProviderConfig pc = rawConfigs.get(providerName);
            if (pc == null) {
                return "错误: 未知 Provider: " + providerName;
            }
            // 自动注册为 OpenAI 兼容 Provider
            OpenAiProvider autoProvider = new OpenAiProvider();
            // 覆写 name 为配置中的名称
            provider = new NamedProvider(providerName, autoProvider);
            register(provider);
        }

        // 获取或创建配置
        ModelProvider.ModelConfig config = configs.get(providerName);
        if (config == null) {
            ProviderConfig pc = rawConfigs.get(providerName);
            if (pc == null) {
                // 从环境变量构建
                String baseUrl = System.getenv("ANTHROPIC_BASE_URL");
                String apiKey = System.getenv("ANTHROPIC_AUTH_TOKEN");
                if (baseUrl == null) baseUrl = "https://api.openai.com/v1";
                if (apiKey == null) apiKey = "";
                config = ModelProvider.ModelConfig.builder()
                    .baseUrl(baseUrl)
                    .apiKey(apiKey)
                    .modelName(modelName != null ? modelName : "gpt-4o")
                    .build();
            } else {
                config = ModelProvider.ModelConfig.builder()
                    .baseUrl(pc.getBaseUrl())
                    .apiKey(pc.getApiKey())
                    .modelName(modelName != null ? modelName : pc.getDefaultModel())
                    .build();
            }
            configs.put(providerName, config);
        }

        // 如果指定了模型名，更新配置
        if (modelName != null && !modelName.isBlank()) {
            config = ModelProvider.ModelConfig.builder()
                .baseUrl(config.baseUrl())
                .apiKey(config.apiKey())
                .modelName(modelName)
                .temperature(config.temperature())
                .maxTokens(config.maxTokens())
                .timeoutSeconds(config.timeoutSeconds())
                .build();
            configs.put(providerName, config);
        }

        // 创建新的 ChatLanguageModel
        try {
            ChatLanguageModel newModel = provider.createModel(config);
            this.currentModel = newModel;
            this.activeProvider = providerName;
            this.activeModel = config.modelName();

            LOG.info("已切换到 Provider: {}, 模型: {}", activeProvider, activeModel);
            return String.format("已切换到 Provider: \u001B[36m%s\u001B[0m, 模型: \u001B[36m%s\u001B[0m\n  Base URL: %s",
                activeProvider, activeModel, config.baseUrl());
        } catch (Exception e) {
            LOG.error("创建模型失败: {}", e.getMessage());
            return "错误: 创建模型失败 - " + e.getMessage();
        }
    }

    /**
     * 从环境变量/默认配置初始化当前模型。
     */
    public void initDefault(ChatLanguageModel model, String providerName, String modelName) {
        this.currentModel = model;
        this.activeProvider = providerName != null ? providerName : "openai-compatible";
        this.activeModel = modelName != null ? modelName : "gpt-4o";

        // 注册默认 Provider
        if (!providers.containsKey("openai-compatible")) {
            register(new OpenAiProvider());
        }
    }

    // ─── 查询 ───

    public ChatLanguageModel getCurrentModel() { return currentModel; }
    public String getActiveProvider() { return activeProvider; }
    public String getActiveModel() { return activeModel; }

    /** 获取所有已注册 Provider 名称 */
    public Set<String> getProviderNames() {
        Set<String> names = new LinkedHashSet<>(providers.keySet());
        names.addAll(rawConfigs.keySet());
        return names;
    }

    /** 获取 Provider 的配置信息显示 */
    public String describeProvider(String name) {
        ProviderConfig pc = rawConfigs.get(name);
        if (pc != null) {
            return String.format("  %s: %s\n    Base URL: %s\n    Models: %s",
                name,
                pc.getDescription() != null ? pc.getDescription() : "",
                pc.getBaseUrl(),
                String.join(", ", pc.getModels() != null ? pc.getModels() : List.of("(未配置)")));
        }

        ModelProvider provider = providers.get(name);
        if (provider != null) {
            return String.format("  %s:\n    Models: %s",
                name, String.join(", ", provider.supportedModels()));
        }
        return "  " + name + ": (无详细信息)";
    }

    /**
     * 包装 Provider，允许动态命名。
     * 当 settings.json 中的 Provider 名称与内置名称不同时使用。
     */
    private static class NamedProvider implements ModelProvider {
        private final String name;
        private final ModelProvider delegate;

        NamedProvider(String name, ModelProvider delegate) {
            this.name = name;
            this.delegate = delegate;
        }

        @Override
        public String name() { return name; }

        @Override
        public ChatLanguageModel createModel(ModelConfig config) {
            return delegate.createModel(config);
        }

        @Override
        public List<String> supportedModels() {
            ProviderConfig pc = null;
            // 尝试从 rawConfigs 获取
            return delegate.supportedModels();
        }

        @Override
        public boolean isAvailable() { return delegate.isAvailable(); }
    }
}
