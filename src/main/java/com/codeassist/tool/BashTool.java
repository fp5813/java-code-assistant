package com.codeassist.tool;

import com.codeassist.agent.AgentContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.*;
import java.util.regex.Pattern;

/**
 * Shell 命令执行工具。
 * 对应 MiniClaude 的 BashTool。
 * 支持命令执行、超时控制、目录隔离、输出截断。
 */
public class BashTool implements Tool<BashTool.Input, BashTool.Output> {

    public static final String NAME = "Bash";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 输出最大字符数 */
    private static final int MAX_OUTPUT_CHARS = 50_000;

    /** 默认超时（秒） */
    private static final int DEFAULT_TIMEOUT = 60;

    /** Windows 上默认使用 cmd，其他系统使用 bash */
    private static final boolean IS_WINDOWS = System.getProperty("os.name", "").toLowerCase().contains("win");

    public record Input(
        String command,
        String workingDir,
        int timeoutSeconds,
        boolean background
    ) {}

    public record Output(
        String command,
        int exitCode,
        String stdout,
        String stderr,
        boolean timedOut,
        boolean truncated,
        long durationMs
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "执行 Shell 命令。可用于编译、运行测试、Git 操作等开发任务。" +
               "默认超时 " + DEFAULT_TIMEOUT + " 秒，输出截断 " + (MAX_OUTPUT_CHARS / 1000) + "KB。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "description": "执行 Shell 命令",
            "properties": {
                "command": {
                    "type": "string",
                    "description": "要执行的 Shell 命令"
                },
                "workingDir": {
                    "type": "string",
                    "description": "工作目录（默认当前项目目录）"
                },
                "timeoutSeconds": {
                    "type": "integer",
                    "description": "超时时间（默认 60 秒）"
                },
                "background": {
                    "type": "boolean",
                    "description": "是否后台执行（默认 false，暂不支持）"
                }
            },
            "required": ["command"]
        }
        """;
    }

    @Override
    public Output execute(Input input, AgentContext context) throws ToolException {
        long start = System.currentTimeMillis();

        // ── 安全校验 ──
        String command = input.command().trim();
        if (command.isBlank()) {
            throw new ToolException("命令不能为空");
        }

        // HardDeny 检查由 ToolRegistry.getToolChecked() 集中处理

        // ── 路径解析 ──
        Path baseDir = input.workingDir() != null && !input.workingDir().isBlank()
            ? Paths.get(input.workingDir()).toAbsolutePath().normalize()
            : Paths.get(context.getWorkingDirectory()).toAbsolutePath().normalize();

        Path projectRoot = Paths.get(context.getWorkingDirectory()).toAbsolutePath().normalize();
        if (!baseDir.startsWith(projectRoot)) {
            throw new ToolException("禁止在项目目录之外执行命令: " + baseDir);
        }

        if (!Files.exists(baseDir) || !Files.isDirectory(baseDir)) {
            throw new ToolException("目录不存在: " + baseDir);
        }

        int timeout = (input.timeoutSeconds() > 0) ? input.timeoutSeconds() : DEFAULT_TIMEOUT;

        try {
            // ── 构建进程 ──
            ProcessBuilder pb;
            if (IS_WINDOWS) {
                pb = new ProcessBuilder("cmd.exe", "/c", command);
            } else {
                pb = new ProcessBuilder("sh", "-c", command);
            }

            pb.directory(baseDir.toFile());
            pb.redirectErrorStream(false);

            // 继承父进程的基本环境变量，但隔离
            pb.environment().put("PWD", baseDir.toString());
            pb.environment().remove("CLASSPATH"); // 避免意外影响

            Process process = pb.start();

            // ── 异步读取输出 ──
            ExecutorService executor = Executors.newFixedThreadPool(2);
            CompletableFuture<String> stdoutFuture = CompletableFuture.supplyAsync(() ->
                readStreamSafe(process.getInputStream(), MAX_OUTPUT_CHARS), executor);
            CompletableFuture<String> stderrFuture = CompletableFuture.supplyAsync(() ->
                readStreamSafe(process.getErrorStream(), MAX_OUTPUT_CHARS), executor);

            // ── 等待完成（带超时） ──
            boolean finished;
            try {
                finished = process.waitFor(timeout, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                process.destroyForcibly();
                Thread.currentThread().interrupt();
                executor.shutdownNow();
                long duration = System.currentTimeMillis() - start;
                return new Output(command, -1, "", "执行被中断", false, false, duration);
            }

            long duration = System.currentTimeMillis() - start;

            String stdout, stderr;
            boolean timedOut;
            boolean truncatedOut, truncatedErr;

            if (finished) {
                int exitCode = process.exitValue();
                stdout = stdoutFuture.get(5, TimeUnit.SECONDS);
                stderr = stderrFuture.get(5, TimeUnit.SECONDS);
                truncatedOut = stdout.length() == MAX_OUTPUT_CHARS;
                truncatedErr = stderr.length() == MAX_OUTPUT_CHARS;
                timedOut = false;

                executor.shutdown();
                return new Output(command, exitCode, stdout, stderr, timedOut, truncatedOut || truncatedErr, duration);
            } else {
                // 超时
                process.destroyForcibly();
                try {
                    stdout = stdoutFuture.get(1, TimeUnit.SECONDS);
                } catch (Exception ex) { stdout = ""; }
                try {
                    stderr = stderrFuture.get(1, TimeUnit.SECONDS);
                } catch (Exception ex) { stderr = ""; }
                executor.shutdownNow();

                return new Output(command, -1,
                    stdout, stderr + "\n[命令超时: " + timeout + "秒]",
                    true, false, duration);
            }

        } catch (Exception e) {
            throw new ToolException("命令执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 读取输入流，限制最大字符数。当流关闭或出错时返回已有内容。
     */
    private String readStreamSafe(InputStream is, int maxChars) {
        try {
            Reader reader = new InputStreamReader(is, StandardCharsets.UTF_8);
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[4096];
            int total = 0;
            int n;
            while ((n = reader.read(buf)) != -1) {
                if (total + n > maxChars) {
                    sb.append(buf, 0, maxChars - total);
                    sb.append("\n... (输出过长，已截断)");
                    break;
                }
                sb.append(buf, 0, n);
                total += n;
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            return "[读取输出失败: " + e.getMessage() + "]";
        }
    }

    @Override
    public boolean isReadOnly() { return false; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode node = MAPPER.readTree(jsonInput);
            String command = node.has("command") ? node.get("command").asText() : "";
            String workingDir = node.has("workingDir") && !node.get("workingDir").isNull()
                ? node.get("workingDir").asText() : null;
            int timeout = node.has("timeoutSeconds") && !node.get("timeoutSeconds").isNull()
                ? node.get("timeoutSeconds").asInt() : DEFAULT_TIMEOUT;
            boolean bg = node.has("background") && node.get("background").asBoolean(false);
            return new Input(command, workingDir, timeout, bg);
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
            sb.append("$ ").append(output.command()).append("\n");
            sb.append("> 退出码: ").append(output.exitCode());
            if (output.timedOut()) sb.append(" [超时]");
            sb.append(" (").append(output.durationMs()).append("ms)\n");

            if (!output.stdout().isBlank()) {
                sb.append("--- stdout ---\n").append(output.stdout());
                if (!output.stdout().endsWith("\n")) sb.append("\n");
            }
            if (!output.stderr().isBlank()) {
                sb.append("--- stderr ---\n").append(output.stderr());
                if (!output.stderr().endsWith("\n")) sb.append("\n");
            }
            if (output.truncated()) {
                sb.append("(输出已截断)\n");
            }
            return sb.toString();
        }
    }
}
