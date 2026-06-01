package com.codeassist.tool;

import com.codeassist.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Git 状态查看工具。
 * 显示工作区文件状态（已修改/已暂存/未跟踪）。
 */
public class GitStatusTool implements Tool<GitStatusTool.Input, String> {

    public static final String NAME = "GitStatus";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(boolean shortFormat) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "查看 Git 工作区状态。显示已修改、已暂存、未跟踪的文件。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "shortFormat": { "type": "boolean", "description": "使用简短格式（默认 false）" }
            }
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        Path workDir = Paths.get(context.getWorkingDirectory());
        List<String> cmd;
        if (input.shortFormat()) {
            cmd = List.of("git", "status", "--short", "--branch");
        } else {
            cmd = List.of("git", "status");
        }
        return GitDiffTool.execGit(cmd, workDir, 50_000);
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            boolean shortFmt = n.has("shortFormat") && n.get("shortFormat").asBoolean(false);
            return new Input(shortFmt);
        } catch (Exception e) {
            return new Input(false);
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
