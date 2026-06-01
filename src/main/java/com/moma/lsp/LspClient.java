package com.moma.lsp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简化的 LSP 客户端。
 * 通过 JSON-RPC 协议与语言服务器通信，获取代码诊断信息。
 * 支持 Java (javase) 和 TypeScript/JavaScript 的语言服务器。
 */
public class LspClient implements AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(LspClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AtomicInteger requestId = new AtomicInteger(1);

    private Process serverProcess;
    private BufferedReader reader;
    private BufferedWriter writer;
    private boolean initialized = false;

    /** 支持的 LSP 服务器命令 */
    private static final Map<String, List<String>> LS_COMMANDS = Map.of(
        ".java", List.of("java", "-jar", "jdtls"),
        ".ts", List.of("typescript-language-server", "--stdio"),
        ".js", List.of("typescript-language-server", "--stdio"),
        ".py", List.of("pylsp")
    );

    /**
     * 尝试为给定文件启动 LSP 服务器。
     */
    public boolean startForFile(Path filePath, Path projectRoot) {
        String ext = getExtension(filePath.toString());
        List<String> cmd = LS_COMMANDS.get(ext);
        if (cmd == null) {
            LOG.debug("不支持的文件类型: {}", ext);
            return false;
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(projectRoot.toFile());
            pb.redirectErrorStream(false);
            serverProcess = pb.start();

            reader = new BufferedReader(new InputStreamReader(serverProcess.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(new OutputStreamWriter(serverProcess.getOutputStream(), StandardCharsets.UTF_8));

            // 异步读取错误流
            CompletableFuture.runAsync(() -> {
                try (var errReader = new BufferedReader(
                        new InputStreamReader(serverProcess.getErrorStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = errReader.readLine()) != null) {
                        LOG.trace("[LSP stderr] {}", line);
                    }
                } catch (IOException ignored) {}
            });

            initialize(projectRoot);
            return true;
        } catch (IOException e) {
            LOG.warn("启动 LSP 服务器失败: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 发送 initialize 请求。
     */
    private void initialize(Path projectRoot) throws IOException {
        ObjectNode initParams = MAPPER.createObjectNode();
        initParams.put("processId", ProcessHandle.current().pid());
        initParams.set("capabilities", MAPPER.createObjectNode());
        initParams.put("rootUri", projectRoot.toUri().toString());

        sendRequest("initialize", initParams);
        // 等待并消费 initialize 结果
        consumeResponse(5000);

        // 发送 initialized 通知
        sendNotification("initialized", MAPPER.createObjectNode());
        initialized = true;
        LOG.info("LSP 初始化完成");
    }

    /**
     * 打开文件并获取诊断信息。
     */
    public List<Diagnostic> getDiagnostics(Path filePath) throws IOException {
        if (!initialized) {
            return List.of(new Diagnostic("error", "LSP 未初始化", ""));
        }

        // 读取文件内容
        String text = Files.readString(filePath);

        // 发送 didOpen 通知
        ObjectNode textDoc = MAPPER.createObjectNode()
            .put("uri", filePath.toUri().toString())
            .put("languageId", getLanguageId(filePath.toString()))
            .put("version", 1)
            .put("text", text);
        ObjectNode didOpenParams = MAPPER.createObjectNode()
            .set("textDocument", textDoc);
        sendNotification("textDocument/didOpen", didOpenParams);

        // 读取诊断推送
        List<Diagnostic> diagnostics = new ArrayList<>();
        long deadline = System.currentTimeMillis() + 3000;
        while (System.currentTimeMillis() < deadline) {
            String response = tryReadResponse(500);
            if (response == null) break;

            JsonNode root = MAPPER.readTree(response);
            JsonNode method = root.get("method");
            if (method != null && "textDocument/publishDiagnostics".equals(method.asText())) {
                JsonNode params = root.get("params");
                if (params != null) {
                    JsonNode diags = params.get("diagnostics");
                    if (diags != null && diags.isArray()) {
                        for (JsonNode d : diags) {
                            String severity = mapSeverity(d.get("severity"));
                            String message = d.get("message").asText();
                            JsonNode range = d.get("range");
                            String position = "";
                            if (range != null) {
                                JsonNode start = range.get("start");
                                position = "L" + start.get("line").asInt()
                                    + ":" + start.get("character").asInt();
                            }
                            diagnostics.add(new Diagnostic(severity, message, position));
                        }
                    }
                }
                break;
            }
        }

        return diagnostics;
    }

    /**
     * 发送 didClose 通知。
     */
    public void closeFile(Path filePath) throws IOException {
        if (!initialized) return;
        ObjectNode closeParams = MAPPER.createObjectNode()
            .set("textDocument", MAPPER.createObjectNode()
                .put("uri", filePath.toUri().toString()));
        sendNotification("textDocument/didClose", closeParams);
    }

    @Override
    public void close() {
        if (serverProcess != null) {
            try {
                sendNotification("shutdown", MAPPER.createObjectNode());
                serverProcess.destroy();
            } catch (Exception ignored) {}
        }
    }

    // ─── JSON-RPC 通信 ───

    private void sendRequest(String method, ObjectNode params) throws IOException {
        int id = requestId.getAndIncrement();
        ObjectNode request = MAPPER.createObjectNode()
            .put("jsonrpc", "2.0")
            .put("id", id)
            .put("method", method)
            .set("params", params);
        writeMessage(request);
    }

    private void sendNotification(String method, ObjectNode params) throws IOException {
        ObjectNode notification = MAPPER.createObjectNode()
            .put("jsonrpc", "2.0")
            .put("method", method)
            .set("params", params);
        writeMessage(notification);
    }

    private void writeMessage(ObjectNode message) throws IOException {
        String json = MAPPER.writeValueAsString(message);
        String header = "Content-Length: " + json.getBytes(StandardCharsets.UTF_8).length + "\r\n\r\n";
        writer.write(header);
        writer.write(json);
        writer.flush();
    }

    private String consumeResponse(long timeoutMs) {
        return tryReadResponse(timeoutMs);
    }

    private String tryReadResponse(long timeoutMs) {
        try {
            // 读取 Content-Length 头
            long deadline = System.currentTimeMillis() + timeoutMs;
            String header = null;
            while (System.currentTimeMillis() < deadline) {
                if (reader.ready()) {
                    header = reader.readLine();
                    if (header != null && header.startsWith("Content-Length:")) {
                        break;
                    }
                }
                Thread.sleep(10);
            }
            if (header == null) return null;

            int length = Integer.parseInt(header.substring("Content-Length:".length()).trim());
            // 跳过空行
            reader.readLine();
            // 读取内容
            char[] buf = new char[length];
            int total = 0;
            while (total < length) {
                int n = reader.read(buf, total, length - total);
                if (n == -1) break;
                total += n;
            }
            return new String(buf, 0, total);
        } catch (Exception e) {
            return null;
        }
    }

    // ─── 辅助方法 ───

    private String getExtension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : "";
    }

    private String getLanguageId(String filename) {
        String ext = getExtension(filename);
        return switch (ext) {
            case ".java" -> "java";
            case ".ts" -> "typescript";
            case ".js" -> "javascript";
            case ".py" -> "python";
            default -> "plaintext";
        };
    }

    private static final Map<Integer, String> SEVERITY_MAP = Map.of(
        1, "error", 2, "warning", 3, "info", 4, "hint"
    );

    private String mapSeverity(JsonNode severityNode) {
        if (severityNode == null) return "info";
        return SEVERITY_MAP.getOrDefault(severityNode.asInt(), "info");
    }

    /** 诊断信息 */
    public record Diagnostic(String severity, String message, String position) {}
}
