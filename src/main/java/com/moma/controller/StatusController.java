package com.moma.controller;

import com.moma.agent.AgentContext;
import com.moma.cli.CommandParser;
import com.moma.config.AppConfig;
import com.moma.context.ContextManager;
import com.moma.di.Inject;
import com.moma.model.ProviderRegistry;
import com.moma.plan.PlanManager;
import com.moma.task.TaskManager;
import com.moma.tool.ToolRegistry;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.Map;

/**
 * 处理 /status 命令的控制器。
 */
public class StatusController extends CommandController {

    private final ProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private final PlanManager planManager;
    private final TaskManager taskManager;
    private final AgentContext agentContext;
    private final AppConfig config;
    private final ContextManager contextManager;

    @Inject
    public StatusController(ProviderRegistry providerRegistry, ToolRegistry toolRegistry,
                            PlanManager planManager, TaskManager taskManager,
                            AgentContext agentContext, AppConfig config,
                            ContextManager contextManager) {
        super("status");
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.planManager = planManager;
        this.taskManager = taskManager;
        this.agentContext = agentContext;
        this.config = config;
        this.contextManager = contextManager;
    }

    @Override
    public void registerHandlers(Map<String, CommandParser.CommandHandler> handlers) {
        // ── /status 命令 ──
        handlers.put("status", args -> {
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            String modelName = providerRegistry.getActiveModel();
            int contextWindow = contextManager.getContextWindow(modelName);
            String status = String.format("""
                \u001B[1m当前状态:\u001B[0m
                  工作目录: %s
                  Provider: %s
                  模型: %s
                  Base URL: %s
                  上下文窗口: %d
                  可用工具: %d
                  计划模式: %s
                  任务数: %d
                  Token 使用: %d in / %d out
                  工具调用: %d
                  线程池: %d 活跃线程 / %d 峰值 / %d 总创建
                  可用处理器: %d
                """,
                agentContext.getWorkingDirectory(),
                providerRegistry.getActiveProvider(),
                modelName,
                config.getBaseUrl(),
                contextWindow,
                toolRegistry.size(),
                planManager.isPlanMode() ? "\u001B[33mON\u001B[0m" : "OFF",
                taskManager.size(),
                agentContext.getInputTokens(),
                agentContext.getOutputTokens(),
                agentContext.getTotalToolCalls(),
                threadBean.getThreadCount(),
                threadBean.getPeakThreadCount(),
                threadBean.getTotalStartedThreadCount(),
                Runtime.getRuntime().availableProcessors()
            );
            return new CommandParser.CommandResult(true, status, null);
        });
    }
}
