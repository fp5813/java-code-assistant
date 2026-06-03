package com.moma.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moma.agent.AgentContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;

/**
 * 日志分析工具。读取和分析 MoMa 运行日志。
 *
 * <p>支持三种操作模式：</p>
 * <ul>
 *   <li>{@code tail} — 读取日志文件最后 N 行</li>
 *   <li>{@code search} — 在日志中搜索关键词（含上下文）</li>
 *   <li>{@code errors} — 提取所有 ERROR 级别日志行</li>
 * </ul>
 */
public class MomaLogTool implements Tool<MomaLogTool.Input, String> {

    public static final String NAME = "MomaLog";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_OUTPUT_CHARS = 5000;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 日志目录（相对于工作目录） */
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "moma.log";

    public record Input(
        String action,   // "tail" / "search" / "errors"
        String query,    // action=search 时的搜索关键词
        Integer lines    // action=tail 时的行数，默认 50
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "读取和分析 MoMa 运行日志。支持三种操作：tail（查看最后 N 行）、search（搜索关键词含上下文）、errors（提取所有错误日志）。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["tail", "search", "errors"],
                    "description": "操作类型: tail（末尾行）, search（关键词搜索）, errors（错误日志）"
                },
                "query": {
                    "type": "string",
                    "description": "搜索关键词（仅 action=search 时有效）"
                },
                "lines": {
                    "type": "integer",
                    "description": "读取行数（仅 action=tail 时有效，默认 50）"
                }
            },
            "required": ["action"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        Path logPath = Paths.get(context.getWorkingDirectory(), LOG_DIR, LOG_FILE);

        String action = input.action() != null ? input.action() : "tail";

        return switch (action) {
            case "tail" -> handleTail(logPath, input.lines() != null ? input.lines() : 50);
            case "search" -> handleSearch(logPath, input.query());
            case "errors" -> handleErrors(logPath);
            default -> throw new ToolException("未知操作: " + action + "。可用: tail, search, errors");
        };
    }

    private String handleTail(Path logPath, int lines) throws ToolException {
        List<String> logLines = readAllLines(logPath);
        if (logLines.isEmpty()) {
            return "(日志文件为空或不存在)";
        }

        int from = Math.max(0, logLines.size() - lines);
        List<String> tail = logLines.subList(from, logLines.size());

        StringBuilder sb = new StringBuilder();
        sb.append("=== 日志末尾 ").append(tail.size()).append(" 行 ===\n\n");
        for (String line : tail) {
            sb.append(line).append("\n");
            if (sb.length() > MAX_OUTPUT_CHARS) {
                sb.append("... (输出过长，已截断)");
                break;
            }
        }
        return sb.toString();
    }

    private String handleSearch(Path logPath, String query) throws ToolException {
        if (query == null || query.isBlank()) {
            throw new ToolException("搜索操作需要提供 query 参数");
        }

        List<String> logLines = readAllLines(logPath);
        if (logLines.isEmpty()) {
            return "(日志文件为空或不存在)";
        }

        String lowerQuery = query.toLowerCase();
        StringBuilder sb = new StringBuilder();
        sb.append("=== 搜索: \"").append(query).append("\" ===\n\n");

        int matchCount = 0;
        for (int i = 0; i < logLines.size(); i++) {
            if (logLines.get(i).toLowerCase().contains(lowerQuery)) {
                matchCount++;
                // 显示上下文：前 2 行 + 当前行 + 后 2 行
                int ctxStart = Math.max(0, i - 2);
                int ctxEnd = Math.min(logLines.size() - 1, i + 2);

                sb.append("--- 匹配 #").append(matchCount).append(" (行 ").append(i + 1).append(") ---\n");
                for (int j = ctxStart; j <= ctxEnd; j++) {
                    String prefix = (j == i) ? ">>> " : "    ";
                    sb.append(prefix).append(logLines.get(j)).append("\n");
                }
                sb.append("\n");

                if (sb.length() > MAX_OUTPUT_CHARS) {
                    sb.append("... (输出过长，已截断，共匹配 ").append(matchCount).append(" 条)");
                    return sb.toString();
                }
            }
        }

        if (matchCount == 0) {
            sb.append("未找到匹配 \"").append(query).append("\" 的日志行。");
        } else {
            sb.append("共找到 ").append(matchCount).append(" 条匹配。");
        }
        return sb.toString();
    }

    private String handleErrors(Path logPath) throws ToolException {
        List<String> logLines = readAllLines(logPath);
        if (logLines.isEmpty()) {
            return "(日志文件为空或不存在)";
        }

        List<String> errors = logLines.stream()
            .filter(line -> line.contains("ERROR") || line.contains(" ERROR "))
            .toList();

        if (errors.isEmpty()) {
            // 也尝试搜索滚动日志文件
            String rollingErrors = searchInRollingLogs(logPath, "ERROR");
            if (rollingErrors != null && !rollingErrors.isBlank()) {
                return "当前日志中无 ERROR。\n\n=== 滚动日志中的错误 ===\n" + rollingErrors;
            }
            return "当前日志中无 ERROR 级别日志。";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 错误日志 (").append(errors.size()).append(" 条) ===\n\n");
        for (int i = 0; i < errors.size(); i++) {
            sb.append("[").append(i + 1).append("] ").append(errors.get(i)).append("\n");
            if (sb.length() > MAX_OUTPUT_CHARS) {
                sb.append("\n... (总共 ").append(errors.size()).append(" 条，输出已截断)");
                break;
            }
        }
        return sb.toString();
    }

    /**
     * 搜索滚动日志文件。
     */
    private String searchInRollingLogs(Path logPath, String keyword) {
        try {
            LocalDate today = LocalDate.now();
            for (int i = 1; i <= 7; i++) {
                String dateStr = today.minusDays(i).format(DATE_FMT);
                Path rollingPath = logPath.getParent().resolve("moma." + dateStr + ".log");
                if (Files.exists(rollingPath)) {
                    List<String> lines = readAllLines(rollingPath);
                    long count = lines.stream().filter(l -> l.contains(keyword)).count();
                    if (count > 0) {
                        return "文件 moma." + dateStr + ".log 中有 " + count + " 条包含 \"" + keyword + "\" 的行。";
                    }
                }
            }
        } catch (Exception e) {
            // 忽略滚动文件搜索失败
        }
        return null;
    }

    private List<String> readAllLines(Path path) throws ToolException {
        try {
            if (!Files.exists(path)) {
                return List.of();
            }
            return Files.readAllLines(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ToolException("读取日志失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String action = n.has("action") ? n.get("action").asText() : "tail";
            String query = n.has("query") ? n.get("query").asText() : null;
            Integer lines = n.has("lines") ? n.get("lines").asInt() : null;
            return new Input(action, query, lines);
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
