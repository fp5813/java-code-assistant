package com.moma.controller;

import com.moma.cli.CommandParser;
import com.moma.di.Inject;
import com.moma.agent.AgentContext;
import com.moma.memory.MemoryStore;

/**
 * 记忆命令控制器。处理 /memory 命令。
 */
public class MemoryController extends CommandController {

    private final MemoryStore memoryStore;
    private final AgentContext agentContext;

    @Inject
    public MemoryController(MemoryStore memoryStore, AgentContext agentContext) {
        super("memory");
        this.memoryStore = memoryStore;
        this.agentContext = agentContext;
    }

    @Override
    public void registerHandlers(java.util.Map<String, CommandParser.CommandHandler> handlers) {
        handlers.put("memory", args -> {
            String query = args != null ? args.trim() : "";
            if (query.isBlank()) {
                var mems = memoryStore.getProjectMemories(agentContext.getProjectName(), 10);
                if (mems.isEmpty()) {
                    return new CommandParser.CommandResult(true,
                        "暂无记忆。Agent 使用 MemorySave 工具保存重要信息。", null);
                }
                StringBuilder sb = new StringBuilder("\u001B[1m当前项目的记忆:\u001B[0m\n");
                for (var m : mems) {
                    sb.append(String.format("  [%s] [%s] %s%n",
                        m.getType(), m.getId(),
                        m.getContent().length() > 80 ? m.getContent().substring(0, 80) + "..." : m.getContent()));
                }
                return new CommandParser.CommandResult(true, sb.toString(), null);
            }
            var results = memoryStore.search(agentContext.getProjectName(), null, query, 10);
            if (results.isEmpty()) {
                return new CommandParser.CommandResult(true, "未找到匹配的记忆。", null);
            }
            StringBuilder sb = new StringBuilder("\u001B[1m搜索记忆结果:\u001B[0m\n");
            for (var m : results) {
                sb.append(String.format("  [%s] [%s] %s%n", m.getType(), m.getId(), m.getContent()));
            }
            return new CommandParser.CommandResult(true, sb.toString(), null);
        });
    }
}
