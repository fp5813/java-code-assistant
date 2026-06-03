package com.moma.service;

import com.moma.concurrent.EventBus;
import com.moma.di.Component;
import com.moma.di.Inject;
import com.moma.di.PostConstruct;
import com.moma.memory.MemoryEntry;
import com.moma.memory.MemoryStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Agent 自我学习服务。
 *
 * <p>核心能力：</p>
 * <ul>
 *   <li>日志分析 — 扫描 ERROR 日志，识别重复模式，生成修复建议</li>
 *   <li>会话总结 — 每次会话结束后统计关键指标，自动保存经验记忆</li>
 *   <li>模式发现 — 从多会话中提取高频操作模式</li>
 * </ul>
 */
@Component
public class AgentLearningService {

    private static final Logger LOG = LoggerFactory.getLogger(AgentLearningService.class);

    private final EventBus eventBus;
    private final MemoryStore memoryStore;

    /** 日志目录 */
    private static final String LOG_DIR = "logs";
    private static final String LOG_FILE = "moma.log";
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /** 已分析的日志行数标记（避免重复分析） */
    private int lastAnalyzedLineCount = 0;

    @Inject
    public AgentLearningService(EventBus eventBus, MemoryStore memoryStore) {
        this.eventBus = eventBus;
        this.memoryStore = memoryStore;
    }

    @PostConstruct
    public void init() {
        // 订阅工具执行完成事件（用于后续自动学习）
        eventBus.subscribe(ToolExecutionEvent.class, this::onToolExecuted);
        LOG.info("AgentLearningService 初始化完成，已订阅 EventBus");
    }

    /**
     * 分析最近的日志，发现错误模式并生成改进建议。
     *
     * @return 分析结果摘要
     */
    public String analyzeLogsAndLearn() {
        Path logPath = Paths.get(System.getProperty("user.dir"), LOG_DIR, LOG_FILE);
        if (!Files.exists(logPath)) {
            return "日志文件不存在: " + logPath;
        }

        try {
            List<String> lines = Files.readAllLines(logPath, StandardCharsets.UTF_8);
            if (lines.size() <= lastAnalyzedLineCount) {
                return "无新日志需要分析。";
            }

            // 分析 ERROR 日志
            List<String> errorLines = lines.stream()
                .filter(l -> l.contains("ERROR") || l.contains(" ERROR "))
                .collect(Collectors.toList());

            if (errorLines.isEmpty()) {
                lastAnalyzedLineCount = lines.size();
                return "日志中无错误，系统运行正常。";
            }

            // 提取错误模式
            Map<String, Integer> errorPatterns = new HashMap<>();
            for (String err : errorLines) {
                // 提取异常类名作为模式
                String pattern = extractErrorPattern(err);
                errorPatterns.merge(pattern, 1, Integer::sum);
            }

            // 生成建议
            StringBuilder report = new StringBuilder();
            report.append("## 日志分析报告\n\n");
            report.append(String.format("分析范围: 第 %d ~ %d 行 (%d 行新增)\n",
                lastAnalyzedLineCount + 1, lines.size(),
                lines.size() - lastAnalyzedLineCount));
            report.append(String.format("发现 %d 条错误日志\n\n", errorLines.size()));

            if (!errorPatterns.isEmpty()) {
                report.append("### 错误模式统计\n\n");
                report.append("| 模式 | 出现次数 |\n");
                report.append("|------|----------|\n");
                errorPatterns.entrySet().stream()
                    .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                    .limit(10)
                    .forEach(e -> report.append("| ").append(e.getKey())
                        .append(" | ").append(e.getValue()).append(" |\n"));
            }

            // 自动保存高频错误模式为决策记忆
            for (var entry : errorPatterns.entrySet()) {
                if (entry.getValue() >= 3) {
                    String content = String.format(
                        "高频错误模式: %s (出现 %d 次)。建议排查相关代码。",
                        entry.getKey(), entry.getValue()
                    );
                    memoryStore.save(MemoryEntry.Type.DECISION, content, "moma",
                        "project:moma,auto-learned,error-pattern");
                }
            }

            lastAnalyzedLineCount = lines.size();
            report.append("\n分析结果已自动保存到 MemoryStore。");
            return report.toString();

        } catch (IOException e) {
            LOG.warn("日志分析失败: {}", e.getMessage());
            return "日志分析失败: " + e.getMessage();
        }
    }

    /**
     * 生成当前会话的学习摘要。
     */
    public String generateSessionSummary(String sessionId, int messageCount,
                                          int inputTokens, int outputTokens,
                                          int toolCalls, List<String> modifiedFiles) {
        StringBuilder summary = new StringBuilder();
        summary.append(String.format("## 会话摘要: %s\n\n", sessionId));
        summary.append(String.format("- 消息数: %d\n", messageCount));
        summary.append(String.format("- Token 消耗: %d in / %d out\n", inputTokens, outputTokens));
        summary.append(String.format("- 工具调用: %d 次\n", toolCalls));

        if (modifiedFiles != null && !modifiedFiles.isEmpty()) {
            summary.append("- 修改文件: ").append(modifiedFiles.size()).append(" 个\n");
            for (String f : modifiedFiles) {
                summary.append("  - ").append(f).append("\n");
            }
        }

        summary.append(String.format("\n时间: %s\n", new java.util.Date()));

        // 保存到 MemoryStore
        memoryStore.save(MemoryEntry.Type.FACT, summary.toString(), "moma",
            "project:moma,session-summary,auto-learned");

        return summary.toString();
    }

    /**
     * 提取日志中的错误模式（异常类名或关键短语）。
     */
    private String extractErrorPattern(String logLine) {
        // 尝试匹配 Java 异常类名
        int idx = logLine.indexOf("Exception");
        if (idx > 0) {
            // 提取异常类名
            for (int i = idx - 1; i >= 0; i--) {
                if (!Character.isJavaIdentifierPart(logLine.charAt(i)) && logLine.charAt(i) != '.') {
                    String className = logLine.substring(i + 1, idx + "Exception".length());
                    return className.replace("Caused by: ", "").trim();
                }
            }
        }

        // 匹配 Error
        idx = logLine.indexOf("Error");
        if (idx > 0 && idx < logLine.length() - 5) {
            for (int i = idx - 1; i >= 0; i--) {
                if (!Character.isJavaIdentifierPart(logLine.charAt(i)) && logLine.charAt(i) != '.') {
                    return logLine.substring(i + 1, idx + "Error".length()).trim();
                }
            }
        }

        // 退化为短摘要
        return logLine.length() > 60
            ? logLine.substring(logLine.lastIndexOf(":") > 0 ? logLine.lastIndexOf(":") + 1 : 0, Math.min(60, logLine.length())).trim()
            : logLine;
    }

    /**
     * 工具执行完成时的回调（EventBus 订阅）。
     */
    private void onToolExecuted(ToolExecutionEvent event) {
        // 记录工具调用模式（用于后续分析）
        LOG.debug("工具执行完成: {} (耗时 {}ms)", event.toolName, event.durationMs);
    }

    /**
     * 工具执行事件。
     */
    public record ToolExecutionEvent(
        String toolName,
        long durationMs,
        boolean success,
        String errorMessage
    ) {}
}
