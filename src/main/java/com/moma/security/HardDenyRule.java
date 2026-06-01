package com.moma.security;

import java.util.regex.Pattern;

/**
 * Hard Deny 规则模型。
 * 格式: "ToolName(pattern)" 或 "ToolName(*)"。
 * 对应 MiniClaude 的 hard_deny 安全规则。
 */
public class HardDenyRule {

    private final String toolName;
    private final Pattern contentPattern;
    private final boolean denyAll; // true 表示禁止该工具的所有调用

    /**
     * 解析规则字符串。
     * <pre>
     * "Bash(rm -rf /)"      → 禁止包含 "rm -rf /" 的 Bash 调用
     * "WebFetch"            → 禁止所有 WebFetch 调用
     * "FileWrite(*.env)"    → 禁止写入 .env 文件
     * "Bash(curl 10.)"      → 禁止 curl 到 10.x 网段
     * </pre>
     */
    public HardDenyRule(String rule) {
        if (rule == null || rule.isBlank()) {
            throw new IllegalArgumentException("规则不能为空");
        }

        int openParen = rule.indexOf('(');
        int closeParen = rule.lastIndexOf(')');

        if (openParen > 0 && closeParen == rule.length() - 1 && closeParen > openParen) {
            this.toolName = rule.substring(0, openParen).trim();
            String content = rule.substring(openParen + 1, closeParen);
            if (content.isEmpty() || "*".equals(content.trim())) {
                this.denyAll = true;
                this.contentPattern = null;
            } else {
                this.denyAll = false;
                this.contentPattern = Pattern.compile(Pattern.quote(content), Pattern.CASE_INSENSITIVE);
            }
        } else {
            this.toolName = rule.trim();
            this.denyAll = true;
            this.contentPattern = null;
        }
    }

    /** 检查此规则是否拦截指定的工具调用 */
    public boolean matches(String toolName, String inputJson) {
        if (!this.toolName.equalsIgnoreCase(toolName)) {
            return false;
        }
        if (denyAll) {
            return true;
        }
        if (contentPattern != null && inputJson != null) {
            return contentPattern.matcher(inputJson).find();
        }
        return false;
    }

    public String getToolName() { return toolName; }
    public boolean isDenyAll() { return denyAll; }

    @Override
    public String toString() {
        if (denyAll) {
            return toolName + "(*)";
        }
        return toolName + "(" + contentPattern.pattern() + ")";
    }
}
