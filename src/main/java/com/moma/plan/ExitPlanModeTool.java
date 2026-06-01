package com.moma.plan;

import com.moma.agent.AgentContext;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 退出计划模式工具。
 * 对应 MiniClaude 的 ExitPlanModeTool。
 */
public class ExitPlanModeTool implements Tool<ExitPlanModeTool.Input, String> {

    public static final String NAME = "ExitPlanMode";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(String summary) {}

    private final PlanManager planManager;

    public ExitPlanModeTool(PlanManager planManager) {
        this.planManager = planManager;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "退出计划模式，恢复标准执行模式。适用于计划完成或用户取消时。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "summary": {
                    "type": "string",
                    "description": "执行摘要，简述计划执行结果"
                }
            }
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        return planManager.exitPlanMode(context);
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String summary = node.has("summary") ? node.get("summary").asText() : "";
            return new Input(summary);
        } catch (Exception e) {
            return new Input("");
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
