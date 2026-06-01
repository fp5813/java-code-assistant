package com.moma.memory;

import com.moma.agent.AgentContext;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 保存记忆工具。
 * Agent 在对话中遇到重要信息时可保存为记忆供未来参考。
 */
public class MemorySaveTool implements Tool<MemorySaveTool.Input, String> {

    public static final String NAME = "MemorySave";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MemoryStore store;

    public record Input(
        String type,
        String content,
        String tags
    ) {}

    public MemorySaveTool(MemoryStore store) { this.store = store; }

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "保存一条记忆。将重要的项目信息、架构决策、用户偏好等保存到记忆系统中，供未来对话参考。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "type": {
                    "type": "string",
                    "enum": ["fact", "preference", "decision", "reference"],
                    "description": "记忆类型: fact(事实) / preference(偏好) / decision(决策) / reference(参考)"
                },
                "content": { "type": "string", "description": "记忆内容" },
                "tags": { "type": "string", "description": "标签（逗号分隔，用于检索）" }
            },
            "required": ["type", "content"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        MemoryEntry.Type type;
        try {
            type = MemoryEntry.Type.valueOf(input.type().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ToolException("无效的记忆类型: " + input.type() + "，可选: fact, preference, decision, reference");
        }
        MemoryEntry entry = store.save(type, input.content(), context.getProjectName(), input.tags());
        return "✅ 已保存记忆 [" + entry.getId() + "] 类型: " + type;
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            return new Input(
                n.has("type") ? n.get("type").asText() : "fact",
                n.has("content") ? n.get("content").asText() : "",
                n.has("tags") && !n.get("tags").isNull() ? n.get("tags").asText() : "");
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
