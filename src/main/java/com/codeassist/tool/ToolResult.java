package com.codeassist.tool;

/**
 * 工具执行结果。
 *
 * @param success   是否成功
 * @param data      结果数据（JSON 字符串）
 * @param errorMsg  错误信息（失败时）
 * @param durationMs 执行耗时（毫秒）
 */
public record ToolResult(
    boolean success,
    String data,
    String errorMsg,
    long durationMs
) {
    /** 创建成功结果 */
    public static ToolResult success(String data, long durationMs) {
        return new ToolResult(true, data, null, durationMs);
    }

    /** 创建失败结果 */
    public static ToolResult failure(String errorMsg, long durationMs) {
        return new ToolResult(false, null, errorMsg, durationMs);
    }

    /** 格式化为 LLM 可读的文本 */
    public String toMessageContent() {
        if (success) {
            return "[工具执行成功 (%.2fs)]\n%s".formatted(durationMs / 1000.0, data);
        }
        return "[工具执行失败 (%.2fs)]\n错误: %s".formatted(durationMs / 1000.0, errorMsg);
    }
}
