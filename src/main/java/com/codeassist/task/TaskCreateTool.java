package com.codeassist.task;

import com.codeassist.agent.AgentContext;
import com.codeassist.tool.Tool;
import com.codeassist.tool.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

/**
 * 创建子任务工具。
 * Agent 将复杂任务拆解为多个子任务时使用。
 */
public class TaskCreateTool implements Tool<TaskCreateTool.Input, Task> {

    public static final String NAME = "TaskCreate";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String description,
        List<String> dependencies
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "创建子任务。将复杂任务分解为多个可追踪的子任务。可指定依赖关系（先完成依赖任务再执行此任务）。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "description": { "type": "string", "description": "任务描述" },
                "dependencies": {
                    "type": "array",
                    "items": { "type": "string" },
                    "description": "依赖的任务 ID 列表（可选）"
                }
            },
            "required": ["description"]
        }
        """;
    }

    @Override
    public Task execute(Input input, AgentContext context) throws ToolException {
        TaskManager manager = context.getTaskManager();
        if (manager == null) {
            throw new ToolException("TaskManager 未初始化");
        }
        return manager.createTask(input.description(),
            input.dependencies() != null ? input.dependencies() : new ArrayList<>());
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String desc = node.has("description") ? node.get("description").asText() : "";
            List<String> deps = new ArrayList<>();
            if (node.has("dependencies") && node.get("dependencies").isArray()) {
                for (JsonNode dep : node.get("dependencies")) {
                    deps.add(dep.asText());
                }
            }
            return new Input(desc, deps);
        } catch (Exception e) {
            throw new ToolException("解析工具输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(Task output) {
        try {
            return MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            return output.toString();
        }
    }
}
