package com.moma.tool;

import com.moma.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/**
 * 按通配符搜索文件工具。
 * 对应 MiniClaude 的 GlobTool。
 * 使用 Java NIO FileSystem 的 PathMatcher 实现通配符匹配。
 */
public class GlobTool implements Tool<GlobTool.Input, GlobTool.Output> {

    public static final String NAME = "Glob";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String pattern,
        String path,
        int limit
    ) {}

    public record Output(
        List<String> files,
        int totalFiles,
        boolean truncated,
        long durationMs
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "按通配符模式搜索文件。例如：\"** /*.java\" 搜索所有 Java 文件，\"src/**\" 搜索 src 目录下所有文件。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "description": "搜索文件",
            "properties": {
                "pattern": {
                    "type": "string",
                    "description": "通配符模式，如 **/*.java、src/**、*.xml"
                },
                "path": {
                    "type": "string",
                    "description": "搜索起始目录（默认当前工作目录）"
                },
                "limit": {
                    "type": "integer",
                    "description": "最大返回结果数（默认 100）"
                }
            },
            "required": ["pattern"]
        }
        """;
    }

    @Override
    public Output execute(Input input, AgentContext context) throws ToolException {
        long start = System.currentTimeMillis();
        try {
            Path baseDir = input.path() != null && !input.path().isBlank()
                ? Paths.get(input.path()).toAbsolutePath().normalize()
                : Paths.get(context.getWorkingDirectory()).toAbsolutePath().normalize();

            // 安全检查
            Path projectRoot = Paths.get(context.getWorkingDirectory()).toAbsolutePath().normalize();
            if (!baseDir.startsWith(projectRoot)) {
                throw new ToolException("禁止访问项目目录之外的路径: " + baseDir);
            }

            if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
                throw new ToolException("目录不存在: " + baseDir);
            }

            String globPattern = input.pattern();
            if (!globPattern.startsWith("glob:")) {
                globPattern = "glob:" + globPattern;
            }

            PathMatcher matcher = FileSystems.getDefault().getPathMatcher(globPattern);
            int maxResults = (input.limit() > 0) ? input.limit() : 100;

            List<String> results = new ArrayList<>();
            boolean[] truncated = {false};

            // 排除的目录
            List<String> excluded = List.of(".git", "node_modules", "target", "build", ".idea");

            Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String dirName = dir.getFileName().toString();
                    if (excluded.contains(dirName)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (results.size() >= maxResults) {
                        truncated[0] = true;
                        return FileVisitResult.TERMINATE;
                    }
                    // 匹配相对路径
                    Path relative = baseDir.relativize(file);
                    if (matcher.matches(relative)) {
                        results.add(relative.toString().replace('\\', '/'));
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            });

            long duration = System.currentTimeMillis() - start;
            return new Output(results, results.size(), truncated[0], duration);

        } catch (ToolException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolException("搜索文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String pattern = node.has("pattern") ? node.get("pattern").asText() : "";
            String path = node.has("path") && !node.get("path").isNull()
                ? node.get("path").asText() : null;
            int limit = node.has("limit") && !node.get("limit").isNull()
                ? node.get("limit").asInt() : 100;
            return new Input(pattern, path, limit);
        } catch (Exception e) {
            throw new ToolException("解析工具输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(Output output) {
        try {
            return MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            StringBuilder sb = new StringBuilder();
            sb.append("找到 ").append(output.totalFiles()).append(" 个文件");
            if (output.truncated()) sb.append("（已截断）");
            sb.append(":\n");
            for (String f : output.files()) {
                sb.append("  ").append(f).append("\n");
            }
            return sb.toString();
        }
    }
}
