package com.moma.tool;

import com.moma.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * 内容搜索工具。
 * 对应 MiniClaude 的 GrepTool。
 * 支持正则搜索，文件类型过滤，行号显示。
 */
public class GrepTool implements Tool<GrepTool.Input, GrepTool.Output> {

    public static final String NAME = "Grep";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final List<String> EXCLUDED_DIRS = List.of(".git", "node_modules", "target", "build", ".idea", ".gradle");

    public record Input(
        String pattern,
        String path,
        String glob,
        int limit,
        boolean caseInsensitive
    ) {}

    public record Match(
        String file,
        int lineNumber,
        String lineContent
    ) {}

    public record Output(
        List<Match> matches,
        int totalMatches,
        boolean truncated,
        long durationMs
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "在文件中搜索匹配的文本内容。支持正则表达式，按文件类型过滤，显示行号。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "description": "搜索文件内容",
            "properties": {
                "pattern": {
                    "type": "string",
                    "description": "搜索模式（支持正则表达式）"
                },
                "path": {
                    "type": "string",
                    "description": "搜索目录（默认当前工作目录）"
                },
                "glob": {
                    "type": "string",
                    "description": "文件通配符过滤，如 *.java、*.{ts,tsx}（默认全部）"
                },
                "limit": {
                    "type": "integer",
                    "description": "最大结果数（默认 50）"
                },
                "caseInsensitive": {
                    "type": "boolean",
                    "description": "是否忽略大小写（默认 false）"
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
            // 验证正则
            int flags = input.caseInsensitive() ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
            Pattern regex;
            try {
                regex = Pattern.compile(input.pattern(), flags);
            } catch (PatternSyntaxException e) {
                throw new ToolException("无效的正则表达式: " + e.getMessage());
            }

            Path baseDir = input.path() != null && !input.path().isBlank()
                ? Paths.get(input.path()).toAbsolutePath().normalize()
                : Paths.get(context.getWorkingDirectory()).toAbsolutePath().normalize();

            Path projectRoot = Paths.get(context.getWorkingDirectory()).toAbsolutePath().normalize();
            if (!baseDir.startsWith(projectRoot)) {
                throw new ToolException("禁止搜索项目目录之外的路径: " + baseDir);
            }
            if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
                throw new ToolException("目录不存在: " + baseDir);
            }

            int maxResults = (input.limit() > 0) ? input.limit() : 50;
            PathMatcher fileMatcher = (input.glob() != null && !input.glob().isBlank())
                ? FileSystems.getDefault().getPathMatcher("glob:" + input.glob())
                : null;

            List<Match> matches = new ArrayList<>();
            AtomicInteger totalMatches = new AtomicInteger(0);

            Files.walkFileTree(baseDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (!dir.equals(baseDir) && EXCLUDED_DIRS.contains(dir.getFileName().toString())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    // 文件类型过滤
                    if (fileMatcher != null) {
                        Path relative = baseDir.relativize(file);
                        if (!fileMatcher.matches(relative)) {
                            return FileVisitResult.CONTINUE;
                        }
                    }

                    // 跳过二进制文件（只搜索文本文件）
                    if (isBinaryFile(file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    Path relativePath = baseDir.relativize(file);

                    try (BufferedReader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                        String line;
                        int lineNum = 0;
                        while ((line = reader.readLine()) != null) {
                            lineNum++;
                            if (regex.matcher(line).find()) {
                                totalMatches.incrementAndGet();
                                if (matches.size() < maxResults) {
                                    matches.add(new Match(
                                        relativePath.toString().replace('\\', '/'),
                                        lineNum,
                                        line.length() > 500 ? line.substring(0, 500) + "..." : line
                                    ));
                                }
                            }
                        }
                    } catch (IOException e) {
                        // 跳过无法读取的文件
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
            });

            boolean truncated = totalMatches.get() > maxResults;
            long duration = System.currentTimeMillis() - start;

            return new Output(matches, totalMatches.get(), truncated, duration);

        } catch (ToolException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolException("搜索失败: " + e.getMessage(), e);
        }
    }

    /**
     * 简单的二进制文件检测（检查前 8KB 是否有 null 字节）。
     */
    private boolean isBinaryFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        // 已知的文本扩展名白名单
        if (name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".ts") ||
            name.endsWith(".js") || name.endsWith(".tsx") || name.endsWith(".jsx") ||
            name.endsWith(".py") || name.endsWith(".go") || name.endsWith(".rs") ||
            name.endsWith(".c") || name.endsWith(".cpp") || name.endsWith(".h") ||
            name.endsWith(".hpp") || name.endsWith(".md") || name.endsWith(".txt") ||
            name.endsWith(".xml") || name.endsWith(".json") || name.endsWith(".yaml") ||
            name.endsWith(".yml") || name.endsWith(".properties") || name.endsWith(".cfg") ||
            name.endsWith(".conf") || name.endsWith(".sh") || name.endsWith(".bat") ||
            name.endsWith(".cmd") || name.endsWith(".ps1") || name.endsWith(".sql") ||
            name.endsWith(".css") || name.endsWith(".html") || name.endsWith(".htm") ||
            name.endsWith(".gradle") || name.endsWith(".toml") || name.endsWith(".ini")) {
            return false;
        }
        // 通过检查 null 字节判断
        try (var is = Files.newInputStream(path)) {
            byte[] header = is.readNBytes(8192);
            for (byte b : header) {
                if (b == 0) return true;
            }
        } catch (IOException e) {
            return true;
        }
        return false;
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
            String glob = node.has("glob") && !node.get("glob").isNull()
                ? node.get("glob").asText() : null;
            int limit = node.has("limit") && !node.get("limit").isNull()
                ? node.get("limit").asInt() : 50;
            boolean ci = node.has("caseInsensitive") && node.get("caseInsensitive").asBoolean(false);
            return new Input(pattern, path, glob, limit, ci);
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
            sb.append("找到 ").append(output.totalMatches()).append(" 个匹配");
            if (output.truncated()) sb.append("（显示前 ").append(output.matches().size()).append(" 个）");
            sb.append(":\n");
            for (Match m : output.matches()) {
                sb.append(String.format("%s:%d:  %s%n", m.file(), m.lineNumber(), m.lineContent().trim()));
            }
            return sb.toString();
        }
    }
}
