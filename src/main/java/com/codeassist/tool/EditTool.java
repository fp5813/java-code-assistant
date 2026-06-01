package com.codeassist.tool;

import com.codeassist.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 精准文件编辑工具。
 * 对应 MiniClaude 的 FileEditTool。
 * 支持两种编辑模式：
 * 1. 基于行号替换: 指定起始行和结束行，替换为新内容
 * 2. 基于文本匹配: 查找旧文本并替换为新文本（要求唯一匹配）
 */
public class EditTool implements Tool<EditTool.Input, EditTool.Output> {

    public static final String NAME = "Edit";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String filePath,
        String oldString,
        String newString,
        Integer startLine,
        Integer endLine
    ) {}

    public record Output(
        String filePath,
        boolean success,
        int linesChanged,
        int startLineResult,
        int endLineResult,
        String diffPreview
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "对文件执行精准文本替换编辑。支持两种模式：\n" +
               "1) oldString → newString: 查找精确字符串并替换（要求唯一匹配）\n" +
               "2) startLine/endLine: 替换指定行范围的内容\n" +
               "此操作不可逆，但会创建 .bak 备份。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "description": "编辑文件",
            "properties": {
                "filePath": {
                    "type": "string",
                    "description": "文件路径（绝对或相对当前工作目录）"
                },
                "oldString": {
                    "type": "string",
                    "description": "要替换的旧文本（精确匹配），提供此字段时基于文本匹配"
                },
                "newString": {
                    "type": "string",
                    "description": "替换后的新文本"
                },
                "startLine": {
                    "type": "integer",
                    "description": "起始行号（从 1 开始），提供此字段时基于行号替换"
                },
                "endLine": {
                    "type": "integer",
                    "description": "结束行号（包含），与 startLine 配合使用"
                }
            },
            "required": ["filePath", "newString"]
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
                throw new ToolException("禁止编辑项目目录之外的文件: " + targetPath);
            }
            if (!Files.exists(targetPath)) {
                throw new ToolException("文件不存在: " + targetPath);
            }
            if (!Files.isWritable(targetPath)) {
                throw new ToolException("文件不可写: " + targetPath);
            }

            // 读取文件全部内容
            String content = Files.readString(targetPath, StandardCharsets.UTF_8);
            String[] lines = content.split("\n", -1);

            String newContent;
            int startLineResult, endLineResult;

            // ── 模式 1: 基于行号替换 ──
            if (input.startLine() != null && input.startLine() > 0) {
                int startIdx = input.startLine() - 1;
                int endIdx = (input.endLine() != null && input.endLine() > 0)
                    ? Math.min(input.endLine(), lines.length)
                    : startIdx;

                if (startIdx < 0 || startIdx >= lines.length) {
                    throw new ToolException("起始行号超出范围: " + input.startLine() + " (文件共 " + lines.length + " 行)");
                }
                if (endIdx > lines.length) {
                    throw new ToolException("结束行号超出范围: " + input.endLine() + " (文件共 " + lines.length + " 行)");
                }
                if (startIdx > endIdx) {
                    throw new ToolException("起始行号不能大于结束行号");
                }

                // 构建旧内容预览
                StringBuilder oldPreview = new StringBuilder();
                for (int i = startIdx; i < endIdx; i++) {
                    oldPreview.append(lines[i]).append(i < endIdx - 1 ? "\n" : "");
                }

                // 替换行范围
                List<String> newLines = new ArrayList<>();
                for (int i = 0; i < startIdx; i++) {
                    newLines.add(lines[i]);
                }
                newLines.add(input.newString());
                for (int i = endIdx; i < lines.length; i++) {
                    newLines.add(lines[i]);
                }

                newContent = String.join("\n", newLines);
                startLineResult = input.startLine();
                endLineResult = startLineResult + input.newString().split("\n", -1).length - 1;

            // ── 模式 2: 基于文本匹配替换 ──
            } else if (input.oldString() != null && !input.oldString().isBlank()) {
                int idx = content.indexOf(input.oldString());
                if (idx < 0) {
                    throw new ToolException("在文件中未找到匹配的旧文本:\n---\n" +
                        input.oldString().substring(0, Math.min(200, input.oldString().length())) + "\n---");
                }

                // 检查是否唯一匹配
                int secondIdx = content.indexOf(input.oldString(), idx + 1);
                if (secondIdx >= 0) {
                    throw new ToolException("旧文本在文件中出现多次，请使用行号模式或提供更精确的匹配文本");
                }

                newContent = content.replace(input.oldString(), input.newString());

                // 计算受影响的行范围
                int lineBefore = content.substring(0, idx).split("\n", -1).length;
                startLineResult = lineBefore;
                int addedLines = input.newString().split("\n", -1).length;
                int removedLines = input.oldString().split("\n", -1).length;
                endLineResult = lineBefore + Math.max(addedLines, removedLines) - 1;
            } else {
                throw new ToolException("必须提供 oldString 或 startLine 之一");
            }

            // ── 创建 .bak 备份 ──
            Path bakPath = targetPath.resolveSibling(targetPath.getFileName() + ".bak");
            Files.copy(targetPath, bakPath, StandardCopyOption.REPLACE_EXISTING);

            // ── 写入新内容 ──
            Files.writeString(targetPath, newContent, StandardCharsets.UTF_8);

            // ── 生成差异预览 ──
            String diffPreview = generateDiff(content, newContent);

            return new Output(targetPath.toString(), true, lines.length, startLineResult, endLineResult, diffPreview);

        } catch (ToolException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolException("编辑文件失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成简化的差异预览。
     */
    private String generateDiff(String oldContent, String newContent) {
        String[] oldLines = oldContent.split("\n", -1);
        String[] newLines = newContent.split("\n", -1);

        StringBuilder sb = new StringBuilder();
        int maxContext = 2; // 上下文行数
        int changes = 0;

        // 简化的行级 diff
        int maxLen = Math.max(oldLines.length, newLines.length);
        boolean inChange = false;

        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : null;
            String newLine = i < newLines.length ? newLines[i] : null;

            boolean same = (oldLine != null && newLine != null && oldLine.equals(newLine));

            if (!same) {
                if (!inChange) {
                    // 输出上文上下文
                    for (int ctx = Math.max(0, i - maxContext); ctx < i; ctx++) {
                        if (ctx < oldLines.length) {
                            sb.append(String.format("  %6d\t%s%n", ctx + 1, oldLines[ctx]));
                        }
                    }
                    inChange = true;
                    changes++;
                }
                if (oldLine != null) {
                    sb.append(String.format("- %6d\t%s%n", i + 1, oldLine));
                }
                if (newLine != null) {
                    sb.append(String.format("+ %6d\t%s%n", i + 1, newLine));
                }
            } else {
                if (inChange) {
                    // 输出下文上下文
                    for (int ctx = i; ctx < Math.min(maxLen, i + maxContext); ctx++) {
                        String line = ctx < newLines.length ? newLines[ctx] : null;
                        if (line != null) {
                            sb.append(String.format("  %6d\t%s%n", ctx + 1, line));
                        }
                    }
                    inChange = false;
                    sb.append("---\n");
                }
            }
        }

        return sb.length() > 0
            ? "变更数: " + changes + "\n" + sb.toString()
            : "无变更";
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String filePath = node.has("filePath") ? node.get("filePath").asText() : "";
            String oldString = node.has("oldString") && !node.get("oldString").isNull()
                ? node.get("oldString").asText() : null;
            String newString = node.has("newString") ? node.get("newString").asText() : "";
            Integer startLine = node.has("startLine") && !node.get("startLine").isNull()
                ? node.get("startLine").asInt() : null;
            Integer endLine = node.has("endLine") && !node.get("endLine").isNull()
                ? node.get("endLine").asInt() : null;
            return new Input(filePath, oldString, newString, startLine, endLine);
        } catch (Exception e) {
            throw new ToolException("解析工具输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(Output output) {
        try {
            return MAPPER.writeValueAsString(output);
        } catch (Exception e) {
            return String.format("已编辑 %s (行 %d-%d)\n%s",
                output.filePath(), output.startLineResult(), output.endLineResult(), output.diffPreview());
        }
    }
}
