package com.moma.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moma.agent.AgentContext;
import com.moma.learning.PatternLearner;

/**
 * 代码模式学习工具。分析项目结构和 Git 历史，总结开发模式。
 *
 * <p>支持的查询模式：</p>
 * <ul>
 *   <li>{@code git} — Git 提交历史分析</li>
 *   <li>{@code code} — 代码结构分析</li>
 *   <li>{@code all} — 综合分析</li>
 * </ul>
 */
public class PatternLearnTool implements Tool<PatternLearnTool.Input, String> {

    public static final String NAME = "PatternLearn";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final PatternLearner patternLearner;

    public record Input(
        String mode   // "git" / "code" / "all"
    ) {}

    public PatternLearnTool(PatternLearner patternLearner) {
        this.patternLearner = patternLearner;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "学习项目代码模式和 Git 提交历史。支持: git（Git历史分析）、code（代码结构分析）、all（综合分析）。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "mode": {
                    "type": "string",
                    "enum": ["git", "code", "all"],
                    "description": "分析模式: git（Git历史）、code（代码结构）、all（综合分析）"
                }
            },
            "required": ["mode"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        String mode = input.mode() != null ? input.mode() : "all";

        return switch (mode) {
            case "git" -> patternLearner.learnFromGitHistory(20);
            case "code" -> patternLearner.learnFromCodebase();
            case "all" -> patternLearner.summarize();
            default -> throw new ToolException("未知模式: " + mode + "。可用: git, code, all");
        };
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String mode = n.has("mode") ? n.get("mode").asText() : "all";
            return new Input(mode);
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
