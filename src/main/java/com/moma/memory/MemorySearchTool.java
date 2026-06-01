package com.moma.memory;

import com.moma.agent.AgentContext;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 搜索记忆工具。
 * Agent 回忆之前保存的项目信息、决策和偏好。
 */
public class MemorySearchTool implements Tool<MemorySearchTool.Input, String> {

    public static final String NAME = "MemorySearch";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MemoryStore store;

    public record Input(
        String keyword,
        String type,
        String tags,
        int limit
    ) {}

    public MemorySearchTool(MemoryStore store) { this.store = store; }

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "搜索保存在记忆系统中的信息。可按关键词、类型、标签检索过去的项目上下文。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "keyword": { "type": "string", "description": "搜索关键词" },
                "type": { "type": "string", "description": "记忆类型过滤: fact / preference / decision / reference" },
                "tags": { "type": "string", "description": "标签过滤" },
                "limit": { "type": "integer", "description": "最大返回条数（默认 10）" }
            }
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        int limit = input.limit() > 0 ? input.limit() : 10;
        List<MemoryEntry> results;

        if (input.keyword() != null && !input.keyword().isBlank()) {
            results = store.search(context.getProjectName(), input.tags(), input.keyword(), limit);
        } else if (input.type() != null && !input.type().isBlank()) {
            results = store.search(context.getProjectName(), null, null, limit);
            results = results.stream()
                .filter(e -> e.getType().name().equalsIgnoreCase(input.type()))
                .limit(limit)
                .collect(Collectors.toList());
        } else {
            results = store.getProjectMemories(context.getProjectName(), limit);
        }

        if (results.isEmpty()) {
            return "未找到相关的记忆。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("找到 ").append(results.size()).append(" 条记忆:\n");
        for (MemoryEntry e : results) {
            sb.append(String.format("  [%s] [%s] %s%n      %s%n",
                e.getType(), e.getId(),
                e.getTags() != null ? "🏷 " + e.getTags() : "",
                e.getContent().length() > 200 ? e.getContent().substring(0, 200) + "..." : e.getContent()));
        }
        return sb.toString();
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            return new Input(
                n.has("keyword") && !n.get("keyword").isNull() ? n.get("keyword").asText() : null,
                n.has("type") && !n.get("type").isNull() ? n.get("type").asText() : null,
                n.has("tags") && !n.get("tags").isNull() ? n.get("tags").asText() : null,
                n.has("limit") && !n.get("limit").isNull() ? n.get("limit").asInt() : 10);
        } catch (Exception e) {
            return new Input(null, null, null, 10);
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
