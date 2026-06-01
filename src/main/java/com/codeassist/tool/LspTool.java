package com.codeassist.tool;

import com.codeassist.agent.AgentContext;
import com.codeassist.lsp.LspClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * LSP 诊断工具。
 * 启动语言服务器并获取指定文件的代码诊断信息（错误、警告等）。
 */
public class LspTool implements Tool<LspTool.Input, String> {

    public static final String NAME = "Lsp";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String filePath
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "启动语言服务器并分析指定文件的代码诊断信息（错误、警告）。适用于检查代码质量、发现潜在问题。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "filePath": {
                    "type": "string",
                    "description": "要分析的文件路径（支持 .java, .ts, .js, .py）"
                }
            },
            "required": ["filePath"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        Path baseDir = Paths.get(context.getWorkingDirectory()).toAbsolutePath().normalize();
        Path targetFile = baseDir.resolve(input.filePath()).normalize();

        if (!targetFile.startsWith(baseDir)) {
            throw new ToolException("禁止访问项目目录之外的文件");
        }
        if (!targetFile.toFile().exists()) {
            throw new ToolException("文件不存在: " + targetFile);
        }

        try (LspClient lsp = new LspClient()) {
            boolean started = lsp.startForFile(targetFile, baseDir);
            if (!started) {
                return "⚠ LSP 分析不可用。确保已安装相应的语言服务器。\n" +
                       "支持的格式: .java (需要 jdtls), .ts/.js (需要 typescript-language-server), .py (需要 pylsp)";
            }

            List<LspClient.Diagnostic> diags = lsp.getDiagnostics(targetFile);

            if (diags.isEmpty()) {
                return "✅ 未发现代码诊断问题。";
            }

            StringBuilder sb = new StringBuilder();
            long errors = diags.stream().filter(d -> "error".equals(d.severity())).count();
            long warnings = diags.stream().filter(d -> "warning".equals(d.severity())).count();
            sb.append(String.format("📋 %s — %d 个错误, %d 个警告%n", targetFile.getFileName(), errors, warnings));
            sb.append("---\n");

            for (LspClient.Diagnostic d : diags) {
                String icon = switch (d.severity()) {
                    case "error" -> "❌";
                    case "warning" -> "⚠️";
                    default -> "ℹ️";
                };
                sb.append(String.format("  %s [%s] %s: %s%n", icon, d.severity().toUpperCase(), d.position(), d.message()));
            }

            return sb.toString();
        } catch (IOException e) {
            throw new ToolException("LSP 分析失败: " + e.getMessage());
        }
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String filePath = n.has("filePath") ? n.get("filePath").asText() : "";
            return new Input(filePath);
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
