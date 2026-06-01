package com.moma.tool;

import com.moma.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * Git 差异查看工具。
 * 显示工作区或暂存区的文件变更。
 */
public class GitDiffTool implements Tool<GitDiffTool.Input, String> {

    public static final String NAME = "GitDiff";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String path,
        boolean staged,
        String since
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "查看 Git 差异。显示工作区未暂存的变更（默认）或已暂存的变更。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "path": { "type": "string", "description": "文件或目录路径（可选，默认全部）" },
                "staged": { "type": "boolean", "description": "是否显示已暂存的变更（默认 false）" },
                "since": { "type": "string", "description": "从某个提交开始查看差异（可选）" }
            }
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        Path workDir = Paths.get(context.getWorkingDirectory());
        List<String> cmd = new ArrayList<>(List.of("git", "diff"));

        if (input.staged()) {
            cmd.add("--cached");
        }
        if (input.since() != null && !input.since().isBlank()) {
            cmd.add(input.since() + "..HEAD");
        }
        cmd.add("--no-color");
        if (input.path() != null && !input.path().isBlank()) {
            cmd.add("--");
            cmd.add(input.path());
        }

        return execGit(cmd, workDir, 100_000);
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String path = n.has("path") && !n.get("path").isNull() ? n.get("path").asText() : null;
            boolean staged = n.has("staged") && n.get("staged").asBoolean(false);
            String since = n.has("since") && !n.get("since").isNull() ? n.get("since").asText() : null;
            return new Input(path, staged, since);
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }

    /** 执行 Git 命令的共享方法 */
    static String execGit(List<String> cmd, Path workDir, int maxChars) throws ToolException {
        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(workDir.toFile());
            pb.redirectErrorStream(true);

            Process process = pb.start();
            int exitCode = process.waitFor();

            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                if (sb.length() + line.length() + 1 > maxChars) {
                    sb.append("... (输出过长，已截断)");
                    break;
                }
                sb.append(line).append("\n");
            }
            reader.close();

            if (exitCode != 0) {
                throw new ToolException("Git 命令失败 (退出码 " + exitCode + "):\n" + sb);
            }
            String output = sb.toString().trim();
            return output.isEmpty() ? "(无变更)" : output;
        } catch (ToolException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolException("Git 命令执行失败: " + e.getMessage());
        }
    }
}
