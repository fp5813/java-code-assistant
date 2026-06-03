package com.moma.skill;

import com.moma.agent.AgentContext;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 激活技能工具。
 * Agent 主动选择技能来调整自己的行为模式。
 * 对应 MiniClaude 的 skill 系统。
 */
public class SkillTool implements Tool<SkillTool.Input, String> {

    public static final String NAME = "Skill";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final SkillManager skillManager;

    public record Input(String skillName) {}

    public SkillTool(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "激活指定的技能来改变行为模式。可用技能: code-review（代码审查）, test-generation（测试生成）, refactoring（重构）, bug-fix（Bug 修复）, moma-dev（墨码项目开发）。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "skillName": {
                    "type": "string",
                    "description": "技能名称: code-review / test-generation / refactoring / bug-fix / moma-dev"
                }
            },
            "required": ["skillName"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        java.util.Optional<Skill> skillOpt = skillManager.getSkill(input.skillName());
        if (skillOpt.isEmpty()) {
            throw new ToolException("未知技能: " + input.skillName()
                + "。可用技能: " + skillManager.getAllSkills().stream()
                    .map(Skill::name).toList());
        }
        Skill skill = skillOpt.get();
        context.setActiveSkill(input.skillName());
        return "✅ 已激活技能: " + skill.name() + "\n" + skill.description()
            + "\n\n" + skill.prompt();
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String name = n.has("skillName") ? n.get("skillName").asText() : "";
            return new Input(name);
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
