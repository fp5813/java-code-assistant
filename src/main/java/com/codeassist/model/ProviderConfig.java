package com.codeassist.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Map;

/**
 * 单个 Provider 的配置信息。
 * 对应 settings.json 中 providers 块内的每个条目。
 *
 * <p>示例配置:</p>
 * <pre>
 * {
 *   "providers": {
 *     "deepseek": {
 *       "baseUrl": "https://api.deepseek.com",
 *       "apiKey": "sk-xxx",
 *       "models": ["deepseek-chat", "deepseek-coder"],
 *       "description": "DeepSeek API"
 *     }
 *   }
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProviderConfig {

    @JsonProperty("baseUrl")
    private String baseUrl;

    @JsonProperty("apiKey")
    private String apiKey;

    @JsonProperty("models")
    private java.util.List<String> models;

    @JsonProperty("description")
    private String description;

    public ProviderConfig() {}

    public ProviderConfig(String baseUrl, String apiKey, java.util.List<String> models, String description) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.models = models;
        this.description = description;
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public java.util.List<String> getModels() { return models; }
    public void setModels(java.util.List<String> models) { this.models = models; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    /** 首个模型或默认模型名 */
    public String getDefaultModel() {
        if (models != null && !models.isEmpty()) {
            return models.get(0);
        }
        return "gpt-4o";
    }
}
