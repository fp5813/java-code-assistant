package com.moma.context;

import com.moma.tool.ToolRegistry;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiTokenizer;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 上下文管理器。
 * <p>
 * 负责 Token 估算、基于模型上下文窗口的智能消息裁剪、超限时自动摘要压缩。
 * </p>
 *
 * <p>工作流程：</p>
 * <ol>
 *   <li>AgentLoop 每次收到用户输入后调用 {@link #checkAndOptimize}</li>
 *   <li>估算当前消息列表的 Token 总数（含工具定义开销）</li>
 *   <li>若使用率 &gt; 65% → 基于 Token 数裁剪最早的非关键消息</li>
 *   <li>若使用率 &gt; 80% → 调用 LLM 将最早的消息压缩为摘要</li>
 * </ol>
 */
public class ContextManager {

    private static final Logger LOG = LoggerFactory.getLogger(ContextManager.class);

    /** 触发裁剪的阈值（窗口占比） */
    private static final double TRIM_THRESHOLD = 0.65;

    /** 触发摘要压缩的阈值（窗口占比） */
    private static final double COMPRESS_THRESHOLD = 0.80;

    /** 裁剪后目标占比 */
    private static final double TRIM_TARGET = 0.50;

    /** 摘要时截取的消息 Token 上限（占窗口比例） */
    private static final double SUMMARY_CHUNK_RATIO = 0.20;

    private final ContextWindowRegistry registry;
    private final ToolRegistry toolRegistry;

    /** 工具定义的估算 Token 开销缓存（按模型名缓存） */
    private final Map<String, Integer> toolOverheadCache = new HashMap<>();

    public ContextManager(ContextWindowRegistry registry, ToolRegistry toolRegistry) {
        this.registry = registry;
        this.toolRegistry = toolRegistry;
    }

    // ──────────────────────────────────────────────
    // 公共 API
    // ──────────────────────────────────────────────

    /**
     * 检查并优化消息列表（裁剪或摘要压缩）。
     *
     * @param messages  消息列表（会被修改）
     * @param modelName 当前模型名
     * @param model     LLM 实例（摘要压缩时需要）
     * @return 优化后的消息列表
     */
    public List<ChatMessage> checkAndOptimize(List<ChatMessage> messages,
                                               String modelName,
                                               ChatLanguageModel model) {
        int totalTokens = estimateTokens(messages, modelName);
        int contextWindow = registry.getContextWindow(modelName);
        double ratio = (double) totalTokens / contextWindow;

        if (ratio >= COMPRESS_THRESHOLD) {
            LOG.info("上下文使用率 {}/{} ({}) ≥ {}%，触发摘要压缩 (窗口={})",
                totalTokens, contextWindow, String.format("%.1f", ratio * 100),
                (int) (COMPRESS_THRESHOLD * 100), contextWindow);
            return compressMessages(messages, model, modelName);
        }

        if (ratio >= TRIM_THRESHOLD) {
            LOG.info("上下文使用率 {}/{} ({}) ≥ {}%，触发基于 Token 的裁剪 (窗口={})",
                totalTokens, contextWindow, String.format("%.1f", ratio * 100),
                (int) (TRIM_THRESHOLD * 100), contextWindow);
            return trimMessages(messages, modelName);
        }

        return messages;
    }

    /**
     * 获取当前模型的上下文窗口大小。
     */
    public int getContextWindow(String modelName) {
        return registry.getContextWindow(modelName);
    }

    /**
     * 获取安全可用的最大 Token 数。
     */
    public int getSafeMaxTokens(String modelName) {
        return registry.getSafeMaxTokens(modelName);
    }

    /**
     * 估算整个消息列表的 Token 数（含工具定义开销）。
     */
    public int estimateTokens(List<ChatMessage> messages, String modelName) {
        if (messages == null || messages.isEmpty()) return 0;
        try {
            OpenAiTokenizer tokenizer = new OpenAiTokenizer(modelName);
            return tokenizer.estimateTokenCountInMessages(messages)
                + getToolSpecificationTokens(modelName);
        } catch (Exception e) {
            // 降级：粗略估算
            LOG.debug("Token 估算失败，使用粗略估算: {}", e.getMessage());
            int total = 0;
            for (ChatMessage msg : messages) {
                if (msg instanceof SystemMessage sm && sm.text() != null) {
                    total += sm.text().length() / 3;
                } else if (msg instanceof UserMessage um && um.singleText() != null) {
                    total += um.singleText().length() / 3;
                } else if (msg instanceof AiMessage am) {
                    if (am.text() != null) total += am.text().length() / 3;
                } else if (msg instanceof ToolExecutionResultMessage trm) {
                    total += trm.text().length() / 3;
                }
            }
            return total + getToolSpecificationTokens(modelName);
        }
    }

    /**
     * 获取当前 Token 使用占比。
     */
    public double getUsageRatio(List<ChatMessage> messages, String modelName) {
        int totalTokens = estimateTokens(messages, modelName);
        int contextWindow = getContextWindow(modelName);
        return contextWindow > 0 ? (double) totalTokens / contextWindow : 0;
    }

    /**
     * 获取注册表描述。
     */
    public String getRegistryDescription() {
        return registry.describe();
    }

    // ──────────────────────────────────────────────
    // 消息裁剪
    // ──────────────────────────────────────────────

    /**
     * 基于 Token 数的消息裁剪。
     * 保留 index 0 的系统提示词和最近的 UserMessage 组，
     * 从最早的消息组开始删除，直到 Token 数 ≤ 窗口的 {@link #TRIM_TARGET}。
     *
     * @param messages  原始消息列表
     * @param modelName 当前模型名
     * @return 裁剪后的消息列表
     */
    List<ChatMessage> trimMessages(List<ChatMessage> messages, String modelName) {
        if (messages.size() <= 2) return messages;

        int targetTokens = (int) (registry.getContextWindow(modelName) * TRIM_TARGET);

        // 保护最后 2 组 UserMessage 组不被删除
        int protectCount = findRecentUserMessageGroups(messages, 2);

        // 尝试从最早的消息组开始删除
        List<ChatMessage> result = new ArrayList<>(messages);
        int currentTokens = estimateTokens(result, modelName);

        while (currentTokens > targetTokens && result.size() > (1 + protectCount)) {
            // 找到最早的可删除的消息组（UserMessage 及其后续消息）
            int removeStart = findEarliestRemovableGroup(result, protectCount);
            if (removeStart < 0) break;

            int removeEnd = findGroupEnd(result, removeStart);
            List<ChatMessage> removed = result.subList(removeStart, removeEnd);
            int removedTokens = estimateTokens(removed, modelName);

            removed.clear();
            currentTokens -= removedTokens;

            LOG.debug("裁剪消息组: 索引 {}-{}, 释放 {} tokens", removeStart, removeEnd, removedTokens);
        }

        LOG.info("裁剪完成: {} 条消息, {} tokens (目标 ≤ {} tokens)",
            result.size(), currentTokens, targetTokens);
        return result;
    }

    /**
     * 查找最近 N 组 UserMessage 的数量（保护它们不被删除）。
     */
    private int findRecentUserMessageGroups(List<ChatMessage> messages, int groups) {
        int count = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            if (messages.get(i) instanceof UserMessage) {
                count++;
                if (count >= groups) return messages.size() - i;
            }
        }
        return messages.size();
    }

    /**
     * 查找最早的可删除消息组（UserMessage 的起始索引，跳过受保护区域）。
     */
    private int findEarliestRemovableGroup(List<ChatMessage> messages, int protectedCount) {
        int endBoundary = messages.size() - protectedCount;
        for (int i = 1; i < endBoundary; i++) { // 从 1 开始，保护 index 0（系统提示词）
            if (messages.get(i) instanceof UserMessage) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 查找从 start 开始的 UserMessage 组的结束位置。
     * 删除该 UserMessage 及其后的 Assistant 回复和工具结果。
     */
    private int findGroupEnd(List<ChatMessage> messages, int start) {
        int end = start + 1;
        // 最多包含后续 5 条消息（AI回复 + 工具结果）
        while (end < messages.size() && end - start <= 6) {
            if (end > start && messages.get(end) instanceof UserMessage) {
                break; // 遇到下一个 UserMessage 停止
            }
            end++;
        }
        return end;
    }

    // ──────────────────────────────────────────────
    // 摘要压缩
    // ──────────────────────────────────────────────

    /**
     * 摘要压缩：将最早的一批消息发送给 LLM 生成摘要，
     * 替换为一条 {@code SystemMessage("【对话摘要】...")}。
     * <p>
     * 如果 LLM 调用失败，回退到 {@link #trimMessages}。
     * </p>
     *
     * @param messages  原始消息列表
     * @param model     LLM 实例
     * @param modelName 当前模型名
     * @return 压缩后的消息列表
     */
    List<ChatMessage> compressMessages(List<ChatMessage> messages,
                                        ChatLanguageModel model,
                                        String modelName) {
        if (messages.size() <= 3) return trimMessages(messages, modelName);

        int contextWindow = registry.getContextWindow(modelName);
        int summaryTokenBudget = (int) (contextWindow * SUMMARY_CHUNK_RATIO);

        // 收集最早的消息用于摘要（不超过 budget）
        List<ChatMessage> toSummarize = new ArrayList<>();
        int accumulatedTokens = 0;
        int endIndex = messages.size() - 2; // 保留最近的一组消息

        for (int i = 1; i < endIndex; i++) {
            List<ChatMessage> singleMsg = List.of(messages.get(i));
            int msgTokens = estimateTokens(singleMsg, modelName);
            if (accumulatedTokens + msgTokens > summaryTokenBudget) break;
            toSummarize.add(messages.get(i));
            accumulatedTokens += msgTokens;
        }

        if (toSummarize.isEmpty()) return trimMessages(messages, modelName);

        // 调用 LLM 生成摘要
        String summary;
        try {
            summary = generateSummary(toSummarize, model);
        } catch (Exception e) {
            LOG.warn("摘要生成失败，回退到裁剪: {}", e.getMessage());
            return trimMessages(messages, modelName);
        }

        if (summary == null || summary.isBlank()) {
            return trimMessages(messages, modelName);
        }

        // 构建新消息列表：系统提示词 → 摘要 → 保留的最新消息
        List<ChatMessage> result = new ArrayList<>();
        result.add(messages.get(0)); // 保留原始系统提示词

        // 添加摘要消息
        result.add(new SystemMessage("【对话摘要】" + summary));

        // 添加未摘要的后续消息
        int firstKept = messages.indexOf(toSummarize.get(toSummarize.size() - 1)) + 1;
        for (int i = firstKept; i < messages.size(); i++) {
            result.add(messages.get(i));
        }

        int newTokens = estimateTokens(result, modelName);
        LOG.info("摘要压缩完成: {} → {} 条消息, 释放约 {} tokens",
            messages.size(), result.size(),
            estimateTokens(messages, modelName) - newTokens);

        return result;
    }

    /**
     * 调用 LLM 生成对话摘要。
     */
    private String generateSummary(List<ChatMessage> messages, ChatLanguageModel model) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("""
            请用简洁的中文总结以下对话内容。保留关键信息：
            - 用户提出的需求
            - 已完成的代码修改（文件路径 + 修改内容概要）
            - 做出的关键决策
            - 当前待办事项

            对话内容：
            """);

        for (ChatMessage msg : messages) {
            String role = switch (msg.type()) {
                case USER -> "用户";
                case AI -> "AI";
                case SYSTEM -> "系统";
                case TOOL_EXECUTION_RESULT -> "工具结果";
            };
            String text = switch (msg) {
                case SystemMessage sm -> sm.text();
                case UserMessage um -> um.singleText();
                case AiMessage am -> am.text();
                case ToolExecutionResultMessage trm -> trm.text();
                default -> "";
            };
            if (text != null && !text.isBlank()) {
                // 截断过长的文本
                if (text.length() > 500) {
                    text = text.substring(0, 500) + "...(截断)";
                }
                promptBuilder.append("[").append(role).append("] ").append(text).append("\n");
            }
        }

        promptBuilder.append("\n请用 200 字以内的中文总结：");

        List<ChatMessage> summaryMessages = List.of(
            new SystemMessage("你是一个对话摘要助手。请精确、简洁地总结对话内容。"),
            new UserMessage(promptBuilder.toString())
        );

        Response<AiMessage> response = model.generate(summaryMessages);
        return response.content().text();
    }

    // ──────────────────────────────────────────────
    // 工具定义开销估算
    // ──────────────────────────────────────────────

    /**
     * 获取工具定义的 Token 开销。
     * 工具定义（名称 + 描述 + 参数 Schema）会占用上下文，需要计入。
     */
    private int getToolSpecificationTokens(String modelName) {
        return toolOverheadCache.computeIfAbsent(modelName, this::calculateToolOverhead);
    }

    /**
     * 计算工具定义的 Token 开销。
     */
    private int calculateToolOverhead(String modelName) {
        try {
            List<ToolSpecification> specs = toolRegistry.getToolSpecifications();
            if (specs.isEmpty()) return 0;
            OpenAiTokenizer tokenizer = new OpenAiTokenizer(modelName);
            return tokenizer.estimateTokenCountInToolSpecifications(specs);
        } catch (Exception e) {
            LOG.debug("工具定义 Token 估算失败: {}", e.getMessage());
            return 2000; // 粗略估算
        }
    }
}
