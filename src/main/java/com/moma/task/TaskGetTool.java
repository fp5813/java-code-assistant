package com.moma.task;

import com.moma.agent.AgentContext;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 获取任务详情工具。
 */
public class TaskGetTool implements Tool<TaskGetTool.Input, Task> {

    public static final String NAME = "TaskGet";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(String taskId) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "获取单个任务的详细信息，包含状态、描述、依赖、结果等。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "taskId": { "type": "string", "description": "任务 ID" }
            },
            "required": ["taskId"]
        }
        """;
    }

    @Override
    public Task execute(Input input, AgentContext context) throws ToolException {
        TaskManager manager = context.getTaskManager();
        if (manager == null) {
            throw new ToolException("TaskManager 未初始化");
        }
        Task task = manager.getTask(input.taskId());
        if (task == null) {
            throw new ToolException("任务不存在: " + input.taskId());
        }
        return task;
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String taskId = node.has("taskId") ? node.get("taskId").asText() : "";
            return new Input(taskId);
        } catch (Exception e) {
            throw new ToolException("解析工具输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(Task output) {
        try {
            return MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            sb.append("任务 ").append(output.getId()).append(":\n");
            sb.append("  描述: ").append(output.getDescription()).append("\n");
            sb.append("  状态: ").append(output.getStatus()).append("\n");
            if (!output.getDependencies().isEmpty()) {
                sb.append("  依赖: ").append(String.join(", ", output.getDependencies())).append("\n");
            }
            if (output.getResult() != null) {
                sb.append("  结果: ").append(output.getResult()).append("\n");
            }
            return sb.toString();
        }
    }
}
