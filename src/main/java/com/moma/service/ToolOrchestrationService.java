package com.moma.service;

import com.moma.agent.AgentContext;
import com.moma.di.Component;
import com.moma.di.Inject;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.moma.tool.ToolRegistry;
import com.moma.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * 工具编排服务。管理只读/写工具的执行策略。
 */
@Component
public class ToolOrchestrationService {

    private static final Logger LOG = LoggerFactory.getLogger(ToolOrchestrationService.class);

    private final ToolRegistry toolRegistry;
    private final AgentContext agentContext;

    /** 虚拟线程执行器（保留原有模式） */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final int TOOL_TIMEOUT_SECONDS = 120;

    @Inject
    public ToolOrchestrationService(ToolRegistry toolRegistry, AgentContext agentContext) {
        this.toolRegistry = toolRegistry;
        this.agentContext = agentContext;
    }

    /**
     * 执行工具请求列表。只读工具并发执行，写工具串行执行。
     *
     * @return 工具执行结果消息列表
     */
    public List<ChatMessage> executeTools(List<ToolExecutionRequest> requests) {
        List<ChatMessage> resultMessages = new ArrayList<>();

        // 分组
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

        // 并发执行只读工具
        if (!readOnlyRequests.isEmpty()) {
            Map<String, ToolResult> readResults = executeConcurrently(readOnlyRequests);
            for (ToolExecutionRequest req : readOnlyRequests) {
                agentContext.recordToolCall();
                ToolResult result = readResults.get(req.id());
                resultMessages.add(new ToolExecutionResultMessage(
                    req.id(), req.name(),
                    result != null ? result.toMessageContent() : "工具执行失败"));
            }
        }

        // 串行执行写工具
        for (ToolExecutionRequest req : writeRequests) {
            agentContext.recordToolCall();
            ToolResult result = executeTool(req);
            resultMessages.add(new ToolExecutionResultMessage(
                req.id(), req.name(), result.toMessageContent()));
        }

        return resultMessages;
    }

    /**
     * 并发执行多个工具。
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
     * 执行单个工具。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ToolResult executeTool(ToolExecutionRequest request) {
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
            Object output = tool.execute(input, agentContext);
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
}
