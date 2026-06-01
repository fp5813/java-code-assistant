package com.moma.task;

import com.moma.agent.AgentContext;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 更新任务状态工具。
 */
public class TaskUpdateTool implements Tool<TaskUpdateTool.Input, String> {

    public static final String NAME = "TaskUpdate";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String taskId,
        String status,
        String result,
        String errorMessage
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "更新任务的状态或结果。任务完成时设置 result，失败时设置 errorMessage。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "taskId": { "type": "string", "description": "任务 ID" },
                "status": {
                    "type": "string",
                    "description": "新状态：pending / in_progress / completed / failed / blocked"
                },
                "result": { "type": "string", "description": "任务执行结果（完成时设置，会自动标记为 completed）" },
                "errorMessage": { "type": "string", "description": "错误信息（失败时设置，会自动标记为 failed）" }
            },
            "required": ["taskId"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        TaskManager manager = context.getTaskManager();
        if (manager == null) {
            throw new ToolException("TaskManager 未初始化");
        }

        Task task = manager.getTask(input.taskId());
        if (task == null) {
            throw new ToolException("任务不存在: " + input.taskId());
        }

        // 优先级：result > errorMessage > status
        if (input.result() != null && !input.result().isBlank()) {
            manager.updateResult(input.taskId(), input.result());
            return "任务 " + input.taskId() + " 已标记为完成。";
        }
        if (input.errorMessage() != null && !input.errorMessage().isBlank()) {
            manager.updateError(input.taskId(), input.errorMessage());
            return "任务 " + input.taskId() + " 已标记为失败。";
        }
        if (input.status() != null) {
            Task.Status status = Task.Status.valueOf(input.status().toUpperCase());
            manager.updateStatus(input.taskId(), status);
            return "任务 " + input.taskId() + " 状态已更新为 " + status;
        }

        return "未指定更新内容。";
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String taskId = node.has("taskId") ? node.get("taskId").asText() : "";
            String status = node.has("status") && !node.get("status").isNull()
                ? node.get("status").asText() : null;
            String result = node.has("result") && !node.get("result").isNull()
                ? node.get("result").asText() : null;
            String error = node.has("errorMessage") && !node.get("errorMessage").isNull()
                ? node.get("errorMessage").asText() : null;
            return new Input(taskId, status, result, error);
        } catch (Exception e) {
            throw new ToolException("解析工具输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
