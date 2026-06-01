package com.moma.tool;

import com.moma.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;

/**
 * 写入/覆盖文件工具。
 * 对应 MiniClaude 的 FileWriteTool。
 */
public class WriteTool implements Tool<WriteTool.Input, WriteTool.Output> {

    public static final String NAME = "Write";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String filePath,
        String content
    ) {}

    public record Output(
        String filePath,
        int bytesWritten,
        int linesWritten
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "写入新文件或覆盖已有文件。适用于创建新文件或完全替换文件内容。注意：此操作不可逆。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "description": "写入文件",
            "properties": {
                "filePath": {
                    "type": "string",
                    "description": "文件路径（绝对或相对当前工作目录）"
                },
                "content": {
                    "type": "string",
                    "description": "文件内容"
                }
            },
            "required": ["filePath", "content"]
        }
        """;
    }

    @Override
    public Output execute(Input input, AgentContext context) throws ToolException {
        try {
            Path baseDir = Paths.get(context.getWorkingDirectory()).toAbsolutePath().normalize();
            Path targetPath = baseDir.resolve(input.filePath()).normalize();

            // 安全检查
            if (!targetPath.startsWith(baseDir)) {
                throw new ToolException("禁止写入项目目录之外的文件: " + targetPath);
            }

            // 创建父目录
            Path parentDir = targetPath.getParent();
            if (parentDir != null && !Files.exists(parentDir)) {
                Files.createDirectories(parentDir);
            }

            byte[] contentBytes = input.content().getBytes(StandardCharsets.UTF_8);
            Files.write(targetPath, contentBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            int linesWritten = input.content().split("\n", -1).length;

            return new Output(targetPath.toString(), contentBytes.length, linesWritten);
        } catch (ToolException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolException("写入文件失败: " + e.getMessage(), e);
        }
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String filePath = node.has("filePath") ? node.get("filePath").asText() : "";
            String content = node.has("content") ? node.get("content").asText() : "";
            return new Input(filePath, content);
        } catch (Exception e) {
            throw new ToolException("解析工具输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(Output output) {
        try {
            return MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            return String.format("已写入 %s (%d 字节, %d 行)",
                output.filePath(), output.bytesWritten(), output.linesWritten());
        }
    }
}
