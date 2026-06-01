package com.codeassist.agent;

import com.codeassist.tool.Tool;
import com.codeassist.tool.ToolException;
import com.codeassist.tool.ToolRegistry;
import com.codeassist.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;

/**
 * 核心 Agent 循环：perceive → think → act。
 *
 * <p>对应 MiniClaude 的 {@code queryLoop()} 模式。</p>
 *
 * <p>工具编排策略（参照 MiniClaude 的 ToolOrchestration）:</p>
 * <ul>
 *   <li>只读工具（Read/Glob/Grep）：并发执行，互不干扰</li>
 *   <li>写工具（Write/Edit/Bash）：串行执行，避免竞态</li>
 * </ul>
 */
public class AgentLoop {

    private static final Logger LOG = LoggerFactory.getLogger(AgentLoop.class);

    /** 模型提供者（支持运行时热切换，每次迭代获取最新模型） */
    private final Supplier<ChatLanguageModel> modelSupplier;
    private final ToolRegistry toolRegistry;
    private final AgentContext context;
    private final List<ChatMessage> messages;

    /** 工具执行的线程池 */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final int MAX_MESSAGE_COUNT = 50;
    private static final int MAX_ITERATIONS = 20;
    private static final int TOOL_TIMEOUT_SECONDS = 120;

    /**
     * 使用固定模型构造。
     */
    public AgentLoop(ChatLanguageModel model, ToolRegistry toolRegistry, AgentContext context) {
        this(() -> model, toolRegistry, context);
    }

    /**
     * 使用模型提供者构造（支持运行时热切换）。
     */
    public AgentLoop(Supplier<ChatLanguageModel> modelSupplier, ToolRegistry toolRegistry, AgentContext context) {
        this.modelSupplier = modelSupplier;
        this.toolRegistry = toolRegistry;
        this.context = context;
        this.messages = new ArrayList<>();
        refreshSystemPrompt();
    }

    /**
     * 刷新系统提示词（用于 plan mode 切换后更新）。
     */
    public void refreshSystemPrompt() {
        if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) {
            messages.set(0, new SystemMessage(SystemPrompt.build(context)));
        } else {
            messages.add(0, new SystemMessage(SystemPrompt.build(context)));
        }
    }

    /**
     * 执行一次用户输入。
     */
    public AgentResponse execute(String userInput) {
        messages.add(new UserMessage(userInput));
        trimHistory();

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {

            LOG.debug("Agent loop iteration {}/{}: {} messages",
                iteration, MAX_ITERATIONS, messages.size());

            // 刷新系统提示词（使 plan mode 切换生效）
            refreshSystemPrompt();

            List<ToolSpecification> toolSpecs = toolRegistry.getToolSpecifications();

            Response<AiMessage> response;
            try {
                ChatLanguageModel model = modelSupplier.get();
                if (model == null) {
                    return new AgentResponse("错误: 模型未配置。请使用 /provider 命令切换 Provider。",
                        context.getInputTokens(), context.getOutputTokens(),
                        context.getTotalToolCalls());
                }
                response = model.generate(messages, toolSpecs);
            } catch (Exception e) {
                LOG.error("LLM 调用失败", e);
                return new AgentResponse("抱歉，AI 模型调用失败: " + e.getMessage(),
                    context.getInputTokens(), context.getOutputTokens(),
                    context.getTotalToolCalls());
            }

            AiMessage aiMessage = response.content();
            messages.add(aiMessage);

            if (response.tokenUsage() != null) {
                context.recordTokens(
                    response.tokenUsage().inputTokenCount(),
                    response.tokenUsage().outputTokenCount()
                );
            }

            if (!aiMessage.hasToolExecutionRequests()) {
                String text = aiMessage.text();
                return new AgentResponse(text != null && !text.isBlank() ? text : "(无文本回复)",
                    context.getInputTokens(), context.getOutputTokens(),
                    context.getTotalToolCalls());
            }

            // ── ACT: 工具编排 ──
            List<ToolExecutionRequest> requests = aiMessage.toolExecutionRequests();

            // 分组：只读工具并发，写工具串行
            List<ToolExecutionRequest> readOnlyRequests = new ArrayList<>();
            List<ToolExecutionRequest> writeRequests = new ArrayList<>();

            for (ToolExecutionRequest req : requests) {
                Tool<?, ?> tool = toolRegistry.getTool(req.name());
                if (tool != null && tool.isReadOnly()) {
                    readOnlyRequests.add(req);
                } else {
                    writeRequests.add(req);
                }
            }

            // ── 执行只读工具（并发） ──
            if (!readOnlyRequests.isEmpty()) {
                Map<String, ToolResult> readResults = executeConcurrently(readOnlyRequests);
                for (ToolExecutionRequest req : readOnlyRequests) {
                    ToolResult result = readResults.get(req.id());
                    context.recordToolCall();
                    messages.add(new ToolExecutionResultMessage(
                        req.id(), req.name(), result.toMessageContent()));
                }
            }

            // ── 执行写工具（串行） ──
            for (ToolExecutionRequest req : writeRequests) {
                ToolResult result = executeTool(req);
                context.recordToolCall();
                messages.add(new ToolExecutionResultMessage(
                    req.id(), req.name(), result.toMessageContent()));
            }

            // 继续循环
        }

        return new AgentResponse("已达到最大工具调用次数 (" + MAX_ITERATIONS + ")，请简化请求。",
            context.getInputTokens(), context.getOutputTokens(),
            context.getTotalToolCalls());
    }

    /**
     * 并发执行多个工具调用。
     */
    private Map<String, ToolResult> executeConcurrently(List<ToolExecutionRequest> requests) {
        Map<String, ToolResult> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ToolExecutionRequest req : requests) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                ToolResult result = executeTool(req);
                results.put(req.id(), result);
            }, executor);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOG.warn("并发工具执行超时 ({}s)", TOOL_TIMEOUT_SECONDS);
            for (ToolExecutionRequest req : requests) {
                results.putIfAbsent(req.id(),
                    ToolResult.failure("工具执行超时 (" + TOOL_TIMEOUT_SECONDS + "s)", 0));
            }
        } catch (Exception e) {
            LOG.error("并发工具执行失败", e);
        }

        return results;
    }

    /**
     * 执行单个工具调用。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private ToolResult executeTool(ToolExecutionRequest request) {
        String toolName = request.name();
        String args = request.arguments();

        LOG.info("执行工具: {} (参数长度: {})", toolName, args.length());

        Tool tool;
        try {
            tool = toolRegistry.getToolChecked(toolName, args);
        } catch (ToolException e) {
            return ToolResult.failure(e.getMessage(), 0);
        }
        if (tool == null) {
            return ToolResult.failure("未知工具: " + toolName, 0);
        }

        long startTime = System.currentTimeMillis();
        try {
            Object input = tool.parseInput(args);
            Object output = tool.execute(input, context);
            long duration = System.currentTimeMillis() - startTime;
            String formatted = tool.formatOutput(output);
            return ToolResult.success(formatted, duration);
        } catch (ToolException e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.warn("工具 {} 执行失败: {}", toolName, e.getMessage());
            return ToolResult.failure(e.getMessage(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.error("工具 {} 执行异常: {}", toolName, e.getMessage(), e);
            return ToolResult.failure("内部错误: " + e.getMessage(), duration);
        }
    }

    /**
     * 裁剪消息历史。
     */
    private void trimHistory() {
        while (messages.size() > MAX_MESSAGE_COUNT) {
            for (int i = 1; i < messages.size(); i++) {
                if (messages.get(i) instanceof UserMessage) {
                    int removeEnd = Math.min(i + 3, messages.size());
                    messages.subList(i, removeEnd).clear();
                    break;
                }
            }
        }
    }

    public List<ChatMessage> getMessageHistory() {
        return List.copyOf(messages);
    }

    public record AgentResponse(
        String text,
        int inputTokens,
        int outputTokens,
        int totalToolCalls
    ) {
        public AgentResponse(String text) {
            this(text, 0, 0, 0);
        }
    }
}
