package com.moma.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moma.agent.AgentContext;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.lang.management.ThreadMXBean;

/**
 * 运行时指标监控工具。获取 Token、工具调用、JVM 等实时指标。
 *
 * <p>支持的指标类型：</p>
 * <ul>
 *   <li>{@code summary} — 总览（所有指标概览）</li>
 *   <li>{@code tokens} — Token 消耗统计</li>
 *   <li>{@code tools} — 工具调用统计</li>
 *   <li>{@code jvm} — JVM 内存和线程信息</li>
 * </ul>
 */
public class MomaMonitorTool implements Tool<MomaMonitorTool.Input, String> {

    public static final String NAME = "MomaMonitor";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record Input(
        String metric   // "summary" / "tokens" / "tools" / "jvm"
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "获取运行时监控指标。支持: summary（总览）、tokens（Token消耗）、tools（工具调用）、jvm（JVM状态）。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "metric": {
                    "type": "string",
                    "enum": ["summary", "tokens", "tools", "jvm"],
                    "description": "指标类型: summary（总览）, tokens, tools, jvm"
                }
            },
            "required": ["metric"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        String metric = input.metric() != null ? input.metric() : "summary";

        return switch (metric) {
            case "summary" -> buildSummary(context);
            case "tokens" -> buildTokenReport(context);
            case "tools" -> buildToolReport(context);
            case "jvm" -> buildJvmReport();
            default -> throw new ToolException("未知指标: " + metric + "。可用: summary, tokens, tools, jvm");
        };
    }

    private String buildSummary(AgentContext context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## MoMa 运行状态总览\n\n");

        // Agent 状态
        sb.append("### Agent\n");
        sb.append("| 指标 | 值 |\n");
        sb.append("|------|-----|\n");
        sb.append("| 当前模型 | ").append(context.getCurrentModel()).append(" |\n");
        sb.append("| 计划模式 | ").append(context.isPlanMode() ? "是" : "否").append(" |\n");
        sb.append("| 激活技能 | ").append(context.getActiveSkill() != null ? context.getActiveSkill() : "无").append(" |\n");

        // Token 统计
        sb.append("\n### Token 消耗\n");
        sb.append("| 指标 | 值 |\n");
        sb.append("|------|-----|\n");
        sb.append("| 输入 Token | ").append(String.format("%,d", context.getInputTokens())).append(" |\n");
        sb.append("| 输出 Token | ").append(String.format("%,d", context.getOutputTokens())).append(" |\n");
        sb.append("| 总计 Token | ").append(String.format("%,d", context.getInputTokens() + context.getOutputTokens())).append(" |\n");
        sb.append("| 工具调用次数 | ").append(context.getTotalToolCalls()).append(" |\n");

        // JVM 状态
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();

        sb.append("\n### JVM 内存\n");
        sb.append("| 指标 | 值 |\n");
        sb.append("|------|-----|\n");
        sb.append("| 堆内存已用 | ").append(formatBytes(heap.getUsed())).append(" |\n");
        sb.append("| 堆内存最大 | ").append(formatBytes(heap.getMax())).append(" |\n");
        sb.append("| CPU 核心数 | ").append(Runtime.getRuntime().availableProcessors()).append(" |\n");

        return sb.toString();
    }

    private String buildTokenReport(AgentContext context) {
        return String.format("""
            ## Token 消耗统计

            | 指标 | 值 |
            |------|-----|
            | 输入 Token | %,d |
            | 输出 Token | %,d |
            | 总计 Token | %,d |
            | 工具调用次数 | %d |
            """,
            context.getInputTokens(),
            context.getOutputTokens(),
            context.getInputTokens() + context.getOutputTokens(),
            context.getTotalToolCalls()
        );
    }

    private String buildToolReport(AgentContext context) {
        return String.format("""
            ## 工具调用统计

            | 指标 | 值 |
            |------|-----|
            | 总调用次数 | %d |
            | 项目 | %s |
            """,
            context.getTotalToolCalls(),
            context.getProjectName()
        );
    }

    private String buildJvmReport() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        MemoryUsage nonHeap = memoryBean.getNonHeapMemoryUsage();
        ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();

        return String.format("""
            ## JVM 状态

            ### 堆内存
            | 指标 | 值 |
            |------|-----|
            | 已用 | %s |
            | 已提交 | %s |
            | 最大 | %s |
            | 使用率 | %.1f%% |

            ### 非堆内存
            | 指标 | 值 |
            |------|-----|
            | 已用 | %s |

            ### 线程
            | 指标 | 值 |
            |------|-----|
            | 活动线程数 | %d |
            | 峰值线程数 | %d |
            | 守护线程数 | %d |
            """,
            formatBytes(heap.getUsed()),
            formatBytes(heap.getCommitted()),
            formatBytes(heap.getMax()),
            heap.getMax() > 0 ? (double) heap.getUsed() / heap.getMax() * 100 : 0,
            formatBytes(nonHeap.getUsed()),
            threadBean.getThreadCount(),
            threadBean.getPeakThreadCount(),
            threadBean.getDaemonThreadCount()
        );
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String metric = n.has("metric") ? n.get("metric").asText() : "summary";
            return new Input(metric);
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
