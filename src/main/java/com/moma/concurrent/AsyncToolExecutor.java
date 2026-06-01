package com.moma.concurrent;

import com.moma.agent.AgentContext;
import com.moma.di.Component;
import com.moma.di.Inject;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.moma.tool.ToolRegistry;
import com.moma.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;

/**
 * 异步工具执行器。增强 AgentLoop 现有的并发执行能力。
 * 支持超时控制、结果预取、取消令牌。
 */
@Component
public class AsyncToolExecutor {

    private static final Logger LOG = LoggerFactory.getLogger(AsyncToolExecutor.class);

    /** 单个工具的默认超时（秒） */
    private static final int DEFAULT_TOOL_TIMEOUT_SECONDS = 120;

    private final ToolRegistry toolRegistry;
    private final ThreadPoolManager threadPoolManager;

    @Inject
    public AsyncToolExecutor(ToolRegistry toolRegistry, ThreadPoolManager threadPoolManager) {
        this.toolRegistry = toolRegistry;
        this.threadPoolManager = threadPoolManager;
    }

    /**
     * 并发执行多个工具调用，每个工具分别超时控制。
     *
     * @param requests 工具执行请求列表
     * @return 请求 ID -> 执行结果
     */
    public Map<String, ToolResult> executeConcurrently(List<ToolExecutionRequest> requests) {
        Map<String, ToolResult> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ToolExecutionRequest req : requests) {
            Duration timeout = Duration.ofSeconds(DEFAULT_TOOL_TIMEOUT_SECONDS);
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                ToolResult result = executeWithTimeout(req, timeout, null);
                results.put(req.id(), result);
            }, threadPoolManager.getExecutor("compute")); // 用 compute 池执行
            futures.add(future);
        }

        // 等待所有工具完成
        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(DEFAULT_TOOL_TIMEOUT_SECONDS + 10, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOG.warn("并发工具执行整体超时");
            for (ToolExecutionRequest req : requests) {
                results.putIfAbsent(req.id(),
                    ToolResult.failure("整体执行超时", 0));
            }
        } catch (InterruptedException e) {
            LOG.warn("并发工具执行被中断");
            Thread.currentThread().interrupt();
        } catch (ExecutionException e) {
            LOG.error("并发工具执行异常", e);
        }

        return results;
    }

    /**
     * 串行执行写工具，每执行一个回调一次。
     *
     * @param requests 工具执行请求列表
     * @param callback 每个工具完成后的回调
     */
    public void executeSequentially(List<ToolExecutionRequest> requests,
                                     Consumer<ToolResult> callback) {
        for (ToolExecutionRequest req : requests) {
            ToolResult result = executeWithTimeout(req,
                Duration.ofSeconds(DEFAULT_TOOL_TIMEOUT_SECONDS), null);
            if (callback != null) {
                callback.accept(result);
            }
        }
    }

    /**
     * 带超时的单个工具执行。
     *
     * @param request  工具执行请求
     * @param timeout  超时时间
     * @param context  Agent 上下文（可为 null，用于可选的统计记录）
     * @return 工具执行结果
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public ToolResult executeWithTimeout(ToolExecutionRequest request,
                                          Duration timeout,
                                          AgentContext context) {
        String toolName = request.name();
        String args = request.arguments();

        LOG.info("执行工具 (异步): {} (参数长度: {})", toolName, args.length());

        // 获取并检查工具
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

            // 用 CompletableFuture 包装带超时的执行
            CompletableFuture<Object> future = new CompletableFuture<>();
            threadPoolManager.submit(() -> {
                try {
                    Object output = tool.execute(input, context);
                    future.complete(output);
                } catch (Throwable t) {
                    future.completeExceptionally(t);
                }
                return null;
            }, tool.isReadOnly() ? "compute" : "io");

            Object output = future.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            long duration = System.currentTimeMillis() - startTime;
            String formatted = tool.formatOutput(output);
            LOG.debug("工具执行成功: {} ({}ms)", toolName, duration);
            return ToolResult.success(formatted, duration);

        } catch (TimeoutException e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.warn("工具执行超时: {} ({}s)", toolName, timeout.toSeconds());
            return ToolResult.failure("工具执行超时 (" + timeout.toSeconds() + "s)", duration);
        } catch (ToolException e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.warn("工具执行失败: {} - {}", toolName, e.getMessage());
            return ToolResult.failure(e.getMessage(), duration);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            long duration = System.currentTimeMillis() - startTime;
            return ToolResult.failure("执行被中断", duration);
        } catch (ExecutionException e) {
            long duration = System.currentTimeMillis() - startTime;
            Throwable cause = e.getCause();
            LOG.error("工具执行异常: {} - {}", toolName, cause != null ? cause.getMessage() : e.getMessage(), e);
            return ToolResult.failure("内部错误: " + (cause != null ? cause.getMessage() : e.getMessage()), duration);
        } catch (RejectedExecutionException e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.warn("工具执行被拒绝: {}", toolName);
            return ToolResult.failure("线程池已满，任务被拒绝: " + e.getMessage(), duration);
        }
    }

    /**
     * 编排工具执行（只读并发 + 写串行），与 AgentLoop 一致。
     *
     * @param requests   工具请求列表
     * @param resultCallback 每个工具完成后的回调
     */
    public void orchestrate(List<ToolExecutionRequest> requests,
                             Consumer<ToolExecutionRequest> resultCallback) {
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

        // ── 只读工具（并发） ──
        if (!readOnlyRequests.isEmpty()) {
            Map<String, ToolResult> readResults = executeConcurrently(readOnlyRequests);
            for (ToolExecutionRequest req : readOnlyRequests) {
                if (resultCallback != null) {
                    resultCallback.accept(req);
                }
            }
        }

        // ── 写工具（串行） ──
        executeSequentially(writeRequests, resultCallback == null ? null : tr -> {});
    }
}
