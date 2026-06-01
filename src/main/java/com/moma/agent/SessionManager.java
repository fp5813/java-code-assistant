package com.moma.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 会话管理器。
 * 负责会话的持久化、恢复和历史管理。
 * 会话文件存储在 {user.home}/.ca/sessions/ 目录下。
 */
public class SessionManager {

    private static final Logger LOG = LoggerFactory.getLogger(SessionManager.class);

    private static final Path SESSIONS_DIR = Paths.get(
        System.getProperty("user.home"), ".ca", "sessions");

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final String sessionId;
    private final Path sessionFile;

    public SessionManager() {
        this.sessionId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss"));
        this.sessionFile = SESSIONS_DIR.resolve(sessionId + ".json");
        initDir();
    }

    public SessionManager(String sessionId) {
        this.sessionId = sessionId;
        this.sessionFile = SESSIONS_DIR.resolve(sessionId + ".json");
        initDir();
    }

    private void initDir() {
        try {
            Files.createDirectories(SESSIONS_DIR);
        } catch (IOException e) {
            LOG.warn("无法创建会话目录: {}", e.getMessage());
        }
    }

    public String getSessionId() { return sessionId; }

    /**
     * 保存消息历史到文件。
     */
    public void saveHistory(SessionData data) {
        try {
            MAPPER.writeValue(sessionFile.toFile(), data);
            LOG.debug("会话已保存: {} ({} 条消息)", sessionId, data.messages().size());
        } catch (IOException e) {
            LOG.warn("保存会话失败: {}", e.getMessage());
        }
    }

    /**
     * 从文件加载消息历史。
     */
    public Optional<SessionData> loadHistory() {
        if (!Files.exists(sessionFile)) {
            return Optional.empty();
        }
        try {
            SessionData data = MAPPER.readValue(sessionFile.toFile(), SessionData.class);
            return Optional.of(data);
        } catch (IOException e) {
            LOG.warn("加载会话失败: {}", e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 列出所有历史会话。
     */
    public static List<SessionSummary> listSessions() {
        List<SessionSummary> summaries = new ArrayList<>();
        if (!Files.exists(SESSIONS_DIR)) return summaries;

        try (var files = Files.list(SESSIONS_DIR)) {
            files.filter(f -> f.toString().endsWith(".json"))
                .sorted((a, b) -> Long.compare(b.toFile().lastModified(), a.toFile().lastModified()))
                .forEach(f -> {
                    try {
                        SessionData data = MAPPER.readValue(f.toFile(), SessionData.class);
                        summaries.add(new SessionSummary(
                            f.getFileName().toString().replace(".json", ""),
                            data.projectName(),
                            data.modelName(),
                            data.messageCount(),
                            data.createdAt(),
                            data.updatedAt()
                        ));
                    } catch (IOException ignored) {}
                });
        } catch (IOException e) {
            LOG.warn("列出会话失败: {}", e.getMessage());
        }
        return summaries;
    }

    /**
     * 删除指定会话。
     */
    public static boolean deleteSession(String sessionId) {
        Path file = SESSIONS_DIR.resolve(sessionId + ".json");
        try {
            return Files.deleteIfExists(file);
        } catch (IOException e) {
            LOG.warn("删除会话失败: {}", e.getMessage());
            return false;
        }
    }

    // ─── 数据模型 ───

    public record SessionData(
        String sessionId,
        String projectName,
        String modelName,
        String workingDirectory,
        int messageCount,
        int inputTokens,
        int outputTokens,
        int totalToolCalls,
        List<MessageEntry> messages,
        String createdAt,
        String updatedAt
    ) {}

    public record MessageEntry(
        String role,
        String content,
        String toolName,
        long timestamp
    ) {}

    public record SessionSummary(
        String sessionId,
        String projectName,
        String modelName,
        int messageCount,
        String createdAt,
        String updatedAt
    ) {}

    /**
     * 构建 SessionData 快照。
     */
    public SessionData createSnapshot(String projectName, String modelName,
                                       String workingDir, int msgCount,
                                       int inTokens, int outTokens, int toolCalls,
                                       List<MessageEntry> messages) {
        String now = LocalDateTime.now().toString();
        return new SessionData(sessionId, projectName, modelName, workingDir,
            msgCount, inTokens, outTokens, toolCalls,
            messages, now, now);
    }
}
