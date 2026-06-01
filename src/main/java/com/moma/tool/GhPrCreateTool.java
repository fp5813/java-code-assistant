package com.moma.tool;

import com.moma.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 创建 Pull Request 工具。
 * 使用 gh CLI 创建 GitHub PR。
 */
public class GhPrCreateTool implements Tool<GhPrCreateTool.Input, String> {

    public static final String NAME = "GhPrCreate";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String title,
        String body,
        String base,
        String head,
        boolean draft
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "使用 gh CLI 创建 GitHub Pull Request。需先安装 gh 并登录。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "title": { "type": "string", "description": "PR 标题" },
                "body": { "type": "string", "description": "PR 描述内容" },
                "base": { "type": "string", "description": "目标分支（默认 main）" },
                "head": { "type": "string", "description": "源分支（默认当前分支）" },
                "draft": { "type": "boolean", "description": "是否创建 Draft PR（默认 false）" }
            },
            "required": ["title"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        var workDir = Paths.get(context.getWorkingDirectory());
        List<String> cmd = new ArrayList<>(List.of("gh", "pr", "create",
            "--title", input.title()));

        if (input.body() != null && !input.body().isBlank()) {
            cmd.add("--body");
            cmd.add(input.body());
        }
        if (input.base() != null && !input.base().isBlank()) {
            cmd.add("--base");
            cmd.add(input.base());
        }
        if (input.head() != null && !input.head().isBlank()) {
            cmd.add("--head");
            cmd.add(input.head());
        }
        if (input.draft()) {
            cmd.add("--draft");
        }

        String result = GhCommand.execGh(cmd, workDir, 5000);

        // 同时获取 PR 详情
        try {
            String prUrl = result.trim();
            String details = GhCommand.execGh(
                List.of("gh", "pr", "view", "--json", "title,url,state,baseRefName,headRefName", "--jq",
                    "\"PR: \\(.title)\\nURL: \\(.url)\\n状态: \\(.state)\\n\\(.baseRefName) ← \\(.headRefName)\""),
                workDir, 2000);
            return "✅ PR 已创建\n" + details;
        } catch (Exception e) {
            return "✅ PR 已创建: " + result;
        }
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            return new Input(
                n.has("title") ? n.get("title").asText() : "",
                n.has("body") && !n.get("body").isNull() ? n.get("body").asText() : null,
                n.has("base") && !n.get("base").isNull() ? n.get("base").asText() : null,
                n.has("head") && !n.get("head").isNull() ? n.get("head").asText() : null,
                n.has("draft") && n.get("draft").asBoolean(false));
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
