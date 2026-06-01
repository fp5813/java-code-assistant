package com.codeassist.tool;

import com.codeassist.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Git 提交工具。
 * 暂存文件并创建提交。
 */
public class GitCommitTool implements Tool<GitCommitTool.Input, String> {

    public static final String NAME = "GitCommit";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String message,
        boolean all
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "创建 Git 提交。先暂存变更，再提交。可指定提交信息。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "message": { "type": "string", "description": "提交信息" },
                "all": { "type": "boolean", "description": "是否自动暂存所有变更（git add -A，默认 false）" }
            },
            "required": ["message"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        Path workDir = Paths.get(context.getWorkingDirectory());

        // 1. 暂存
        if (input.all()) {
            GitDiffTool.execGit(List.of("git", "add", "-A"), workDir, 1000);
        }

        // 2. 提交
        List<String> cmd = new ArrayList<>(List.of("git", "commit", "-m", input.message()));
        String result = GitDiffTool.execGit(cmd, workDir, 10_000);

        // 3. 获取短 hash
        try {
            String hash = GitDiffTool.execGit(List.of("git", "rev-parse", "--short", "HEAD"), workDir, 100);
            return "✅ 已提交: " + hash.trim() + "\n" + result;
        } catch (Exception e) {
            return result;
        }
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String msg = n.has("message") ? n.get("message").asText() : "";
            boolean all = n.has("all") && n.get("all").asBoolean(false);
            return new Input(msg, all);
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
