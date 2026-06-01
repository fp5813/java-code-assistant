package com.moma.tool;

import com.moma.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * 读取文件工具。
 * 对应 MiniClaude 的 FileReadTool。
 * 支持按行范围读取文件内容。
 */
public class ReadTool implements Tool<ReadTool.Input, ReadTool.Output> {

    public static final String NAME = "Read";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String filePath,
        Integer offset,
        Integer limit
    ) {}

    public record Output(
        String filePath,
        String content,
        int totalLines,
        int startLine,
        boolean truncated
    ) {
        public String toFormattedString() {
            StringBuilder sb = new StringBuilder();
            sb.append("文件: ").append(filePath).append(" (").append(totalLines).append(" 行)\n");
            sb.append("---\n");
            sb.append(content);
            if (truncated) {
                sb.append("\n... (文件过长，已截断)");
            }
            return sb.toString();
        }
    }

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "读取文件内容。可指定行范围（offset/limit）读取文件的部分内容。适用于查看源码、日志等。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "description": "读取文件",
            "properties": {
                "filePath": {
                    "type": "string",
                    "description": "文件路径（绝对或相对当前工作目录）"
                },
                "offset": {
                    "type": "integer",
                    "description": "起始行号（从 1 开始，默认从头）"
                },
                "limit": {
                    "type": "integer",
                    "description": "读取行数限制（默认全部）"
                }
            },
            "required": ["filePath"]
        }
        """;
    }

    @Override
    public Output execute(Input input, AgentContext context) throws ToolException {
        try {
            Path baseDir = Paths.get(context.getWorkingDirectory()).toAbsolutePath().normalize();
            Path targetPath = baseDir.resolve(input.filePath()).normalize();

            // 安全检查：禁止读取项目目录之外的文件
            if (!targetPath.startsWith(baseDir)) {
                throw new ToolException("禁止访问项目目录之外的文件: " + targetPath);
            }

            if (!Files.exists(targetPath)) {
                throw new ToolException("文件不存在: " + targetPath);
            }
            if (!Files.isReadable(targetPath)) {
                throw new ToolException("文件不可读: " + targetPath);
            }

            List<String> allLines = Files.readAllLines(targetPath);
            int totalLines = allLines.size();

            int startIdx = (input.offset() != null && input.offset() > 0) ? input.offset() - 1 : 0;
            int lineLimit = (input.limit() != null && input.limit() > 0) ? input.limit() : totalLines;

            int endIdx = Math.min(startIdx + lineLimit, totalLines);
            List<String> lines = allLines.subList(startIdx, endIdx);
            boolean truncated = endIdx < totalLines;

            // 拼接行号
            StringBuilder content = new StringBuilder();
            for (int i = 0; i < lines.size(); i++) {
                content.append(String.format("%6d\t%s%n", startIdx + i + 1, lines.get(i)));
            }

            return new Output(
                targetPath.toString(),
                content.toString(),
                totalLines,
                startIdx + 1,
                truncated
            );
        } catch (ToolException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolException("读取文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String filePath = node.has("filePath") ? node.get("filePath").asText() : "";
            Integer offset = node.has("offset") && !node.get("offset").isNull()
                ? node.get("offset").asInt() : null;
            Integer limit = node.has("limit") && !node.get("limit").isNull()
                ? node.get("limit").asInt() : null;
            return new Input(filePath, offset, limit);
        } catch (Exception e) {
            throw new ToolException("解析工具输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(Output output) {
        try {
            return MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            return output.toFormattedString();
        }
    }
}
