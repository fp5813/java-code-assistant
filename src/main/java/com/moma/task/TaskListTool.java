package com.moma.task;

import com.moma.agent.AgentContext;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * 列出任务工具。
 */
public class TaskListTool implements Tool<TaskListTool.Input, String> {

    public static final String NAME = "TaskList";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(String status) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "列出所有任务（含状态）。可指定过滤条件按状态筛选。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "status": {
                    "type": "string",
                    "description": "按状态过滤：pending / in_progress / completed / failed / blocked（可选，默认全部）"
                }
            }
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        TaskManager manager = context.getTaskManager();
        if (manager == null) {
            throw new ToolException("TaskManager 未初始化");
        }
        List<Task> all = manager.getAllTasks();
        if (all.isEmpty()) {
            return "暂无任务。";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("任务列表 (").append(all.size()).append(" 个):\n");
        for (Task task : all) {
            String statusIcon = switch (task.getStatus()) {
                case PENDING -> "⏳";
                case IN_PROGRESS -> "🔄";
                case COMPLETED -> "✅";
                case FAILED -> "❌";
                case BLOCKED -> "🔒";
            };
            sb.append(String.format("  %s [%s] %s — %s%n",
                statusIcon, task.getId(), task.getStatus(), task.getDescription()));
            if (!task.getDependencies().isEmpty()) {
                sb.append("     依赖: ").append(String.join(", ", task.getDependencies())).append("\n");
            }
        }
        return sb.toString();
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String status = node.has("status") && !node.get("status").isNull()
                ? node.get("status").asText() : null;
            return new Input(status);
        } catch (Exception e) {
            return new Input(null);
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
