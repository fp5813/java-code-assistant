package com.codeassist.config;

import com.codeassist.model.ProviderConfig;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Collections;
import java.util.Map;

/**
 * 应用程序配置模型。
 * 兼容 Claude Code / MiniClaude 的 settings.json 格式。
 *
 * <p>完整配置示例:</p>
 * <pre>
 * {
 *   "env": {
 *     "ANTHROPIC_BASE_URL": "https://api.deepseek.com",
 *     "ANTHROPIC_AUTH_TOKEN": "sk-xxx",
 *     "ANTHROPIC_MODEL": "deepseek-chat"
 *   },
 *   "model": "deepseek-chat",
 *   "providers": {
 *     "deepseek": {
 *       "baseUrl": "https://api.deepseek.com",
 *       "apiKey": "sk-xxx",
 *       "models": ["deepseek-chat", "deepseek-coder"],
 *       "description": "DeepSeek API"
 *     },
 *     "openai": {
 *       "baseUrl": "https://api.openai.com/v1",
 *       "apiKey": "sk-xxx",
 *       "models": ["gpt-4o", "gpt-4o-mini"],
 *       "description": "OpenAI API"
 *     }
 *   }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class AppConfig {

    @JsonProperty("env")
    private Map<String, String> env;

    @JsonProperty("model")
    private String model;

    @JsonProperty("providers")
    private Map<String, ProviderConfig> providers;

    // ---------- getters / setters ----------

    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public Map<String, ProviderConfig> getProviders() { return providers; }
    public void setProviders(Map<String, ProviderConfig> providers) { this.providers = providers; }

    // ---------- 便捷访问 ----------

    /** 获取 API Base URL，优先级: env.ANTHROPIC_BASE_URL > 默认值 */
    public String getBaseUrl() {
        if (env != null && env.containsKey("ANTHROPIC_BASE_URL")) {
            return env.get("ANTHROPIC_BASE_URL");
        }
        return "https://api.openai.com/v1";
    }

    /** 获取 API Key */
    public String getApiKey() {
        if (env != null && env.containsKey("ANTHROPIC_AUTH_TOKEN")) {
            return env.get("ANTHROPIC_AUTH_TOKEN");
        }
        return "";
    }

    /** 获取模型名称 */
    public String getModelName() {
        if (model != null && !model.isBlank()) {
            return model;
        }
        if (env != null && env.containsKey("ANTHROPIC_MODEL")) {
            return env.get("ANTHROPIC_MODEL");
        }
        return "gpt-4o";
    }

    /** 获取 providers 配置（不可修改视图） */
    public Map<String, ProviderConfig> getProvidersView() {
        return providers != null ? Collections.unmodifiableMap(providers) : Collections.emptyMap();
    }
}
