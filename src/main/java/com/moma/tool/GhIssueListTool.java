package com.moma.tool;

import com.moma.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 列出 Issue 工具。
 */
public class GhIssueListTool implements Tool<GhIssueListTool.Input, String> {

    public static final String NAME = "GhIssueList";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(String state, String label, int limit) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "列出 GitHub Issue。可按状态和标签过滤。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "state": { "type": "string", "description": "过滤状态: open / closed / all（默认 open）" },
                "label": { "type": "string", "description": "按标签过滤（可选）" },
                "limit": { "type": "integer", "description": "返回数量（默认 10）" }
            }
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        var workDir = Paths.get(context.getWorkingDirectory());
        List<String> cmd = new ArrayList<>(List.of("gh", "issue", "list",
            "--json", "number,title,state,labels,updatedAt",
            "--jq", ".[] | \"#\\(.number) [\\(.state)] \\(.title) \\(if .labels then (.labels | map(.name) | join(\", \")) else \"\" end)\""));

        String state = (input.state() != null && !input.state().isBlank()) ? input.state() : "open";
        cmd.add("--state");
        cmd.add(state);

        if (input.label() != null && !input.label().isBlank()) {
            cmd.add("--label");
            cmd.add(input.label());
        }

        int limit = (input.limit() > 0) ? input.limit() : 10;
        cmd.add("--limit");
        cmd.add(String.valueOf(limit));

        String result = GhCommand.execGh(cmd, workDir, 20000);
        return result.isEmpty() ? "暂无 Issue。" : result;
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String state = n.has("state") && !n.get("state").isNull() ? n.get("state").asText() : null;
            String label = n.has("label") && !n.get("label").isNull() ? n.get("label").asText() : null;
            int limit = n.has("limit") && !n.get("limit").isNull() ? n.get("limit").asInt() : 10;
            return new Input(state, label, limit);
        } catch (Exception e) {
            return new Input(null, null, 10);
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
