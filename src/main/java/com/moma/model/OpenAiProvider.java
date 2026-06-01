package com.moma.model;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;

/**
 * OpenAI 兼容 API 的模型提供商实现。
 * 通过 baseUrl 配置，可兼容 OpenAI、DeepSeek、Kiro 等所有 OpenAI 兼容 API。
 *
 * <p>对应 MiniClaude 的 Provider 热切换机制。</p>
 */
public class OpenAiProvider implements ModelProvider {

    private static final Logger LOG = LoggerFactory.getLogger(OpenAiProvider.class);

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public ChatLanguageModel createModel(ModelConfig config) {
        LOG.info("Creating model: provider={}, model={}, baseUrl={}",
            name(), config.modelName(), config.baseUrl());

        return OpenAiChatModel.builder()
            .baseUrl(config.baseUrl())
            .apiKey(config.apiKey())
            .modelName(config.modelName())
            .temperature(config.temperature())
            .maxTokens(config.maxTokens())
            .timeout(Duration.ofSeconds(config.timeoutSeconds()))
            .logRequests(false)
            .logResponses(false)
            .build();
    }

    @Override
    public List<String> supportedModels() {
        // OpenAI 兼容 API 支持动态模型名，这里列出常用模型
        return List.of(
            "gpt-4o",
            "gpt-4o-mini",
            "deepseek-chat",
            "deepseek-coder",
            "deepseek-reasoner"
        );
    }

    @Override
    public boolean isAvailable() {
        // 通过 ConfigLoader 加载后的配置判断
        return true; // 由调用方确保 API Key 已配置
    }
}
