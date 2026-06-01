package com.codeassist.plan;

import com.codeassist.agent.AgentContext;
import com.codeassist.tool.Tool;
import com.codeassist.tool.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 进入计划模式工具。
 * 对应 MiniClaude 的 EnterPlanModeTool。
 * Agent 在计划模式下先输出计划再执行。
 */
public class EnterPlanModeTool implements Tool<EnterPlanModeTool.Input, String> {

    public static final String NAME = "EnterPlanMode";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(String request) {}

    private final PlanManager planManager;

    public EnterPlanModeTool(PlanManager planManager) {
        this.planManager = planManager;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "进入计划模式。对于复杂的多步骤任务，先制定详细计划，再逐步执行。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "request": {
                    "type": "string",
                    "description": "需要规划的任务描述"
                }
            },
            "required": ["request"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        return planManager.enterPlanMode(context);
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String request = node.has("request") ? node.get("request").asText() : "";
            return new Input(request);
        } catch (Exception e) {
            return new Input("");
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
