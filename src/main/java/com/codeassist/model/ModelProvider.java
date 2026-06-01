package com.codeassist.model;

import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.List;

/**
 * 模型提供商接口。
 * 每种 AI 模型服务商实现此接口，支持运行时热切换。
 */
public interface ModelProvider {

    /** 提供商唯一名称，如 "openai"、"deepseek"、"kiro" */
    String name();

    /** 创建 ChatLanguageModel 实例 */
    ChatLanguageModel createModel(ModelConfig config);

    /** 此提供商支持的模型列表 */
    List<String> supportedModels();

    /** 提供商是否可用（如 API Key 是否配置） */
    boolean isAvailable();

    /** 配置对象 */
    record ModelConfig(
        String baseUrl,
        String apiKey,
        String modelName,
        double temperature,
        int maxTokens,
        int timeoutSeconds
    ) {
        public static Builder builder() {
            return new Builder();
        }

        public static class Builder {
            private String baseUrl = "https://api.openai.com/v1";
            private String apiKey = "";
            private String modelName = "gpt-4o";
            private double temperature = 0.0;
            private int maxTokens = 8192;
            private int timeoutSeconds = 120;

            public Builder baseUrl(String baseUrl) { this.baseUrl = baseUrl; return this; }
            public Builder apiKey(String apiKey) { this.apiKey = apiKey; return this; }
            public Builder modelName(String modelName) { this.modelName = modelName; return this; }
            public Builder temperature(double temperature) { this.temperature = temperature; return this; }
            public Builder maxTokens(int maxTokens) { this.maxTokens = maxTokens; return this; }
            public Builder timeoutSeconds(int timeout) { this.timeoutSeconds = timeout; return this; }
            public ModelConfig build() {
                return new ModelConfig(baseUrl, apiKey, modelName, temperature, maxTokens, timeoutSeconds);
            }
        }
    }
}
