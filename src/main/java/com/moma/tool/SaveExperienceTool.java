package com.moma.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moma.agent.AgentContext;
import com.moma.memory.MemoryEntry;
import com.moma.memory.MemoryStore;

/**
 * 经验保存工具。Agent 可主动保存开发经验到跨会话记忆。
 *
 * <p>经验类型：</p>
 * <ul>
 *   <li>{@code bugfix} — Bug 修复经验</li>
 *   <li>{@code feature} — 新增功能</li>
 *   <li>{@code refactor} — 重构记录</li>
 *   <li>{@code pattern} — 编码模式</li>
 * </ul>
 */
public class SaveExperienceTool implements Tool<SaveExperienceTool.Input, String> {

    public static final String NAME = "SaveExperience";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MemoryStore memoryStore;

    public record Input(
        String type,     // "bugfix" / "feature" / "refactor" / "pattern"
        String title,    // 经验标题
        String detail,   // 详细描述
        String lesson    // 学到的教训/经验
    ) {}

    public SaveExperienceTool(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "保存开发经验到跨会话记忆。类型: bugfix（Bug修复经验）、feature（新增功能）、refactor（重构记录）、pattern（编码模式）。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "type": {
                    "type": "string",
                    "enum": ["bugfix", "feature", "refactor", "pattern"],
                    "description": "经验类型"
                },
                "title": {
                    "type": "string",
                    "description": "经验标题（简洁描述）"
                },
                "detail": {
                    "type": "string",
                    "description": "详细描述（文件、修改内容等）"
                },
                "lesson": {
                    "type": "string",
                    "description": "学到的教训或经验总结"
                }
            },
            "required": ["type", "title", "detail", "lesson"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        if (input.title() == null || input.title().isBlank()) {
            throw new ToolException("经验标题不能为空");
        }
        if (input.detail() == null || input.detail().isBlank()) {
            throw new ToolException("经验详情不能为空");
        }
        if (input.lesson() == null || input.lesson().isBlank()) {
            throw new ToolException("经验总结不能为空");
        }

        String typeLabel = switch (input.type()) {
            case "bugfix" -> "Bug修复";
            case "feature" -> "新增功能";
            case "refactor" -> "重构记录";
            case "pattern" -> "编码模式";
            default -> input.type();
        };

        // 格式化经验内容
        String content = String.format("""
            ## %s: %s

            详情:
            %s

            经验总结:
            %s
            """,
            typeLabel, input.title(), input.detail(), input.lesson()
        );

        // 保存为 Decision 类型，带项目标签
        MemoryEntry.Type entryType = "pattern".equals(input.type())
            ? MemoryEntry.Type.DECISION
            : (input.type().equals("feature") ? MemoryEntry.Type.FACT : MemoryEntry.Type.DECISION);

        String tags = "project:" + context.getProjectName() + "," + input.type();
        memoryStore.save(entryType, content, context.getProjectName(), tags);

        return "✅ 经验已保存。类型: " + typeLabel + ", 标题: " + input.title();
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String type = n.has("type") ? n.get("type").asText() : "bugfix";
            String title = n.has("title") ? n.get("title").asText() : "";
            String detail = n.has("detail") ? n.get("detail").asText() : "";
            String lesson = n.has("lesson") ? n.get("lesson").asText() : "";
            return new Input(type, title, detail, lesson);
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
