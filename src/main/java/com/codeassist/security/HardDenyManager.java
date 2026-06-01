package com.codeassist.security;

import com.codeassist.tool.Tool;
import com.codeassist.tool.ToolException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Hard Deny 安全管理器。
 * 在工具执行前拦截并检查是否违反安全规则。
 * 对应 MiniClaude 的 hard_deny 安全系统。
 *
 * <p>规则格式示例:</p>
 * <pre>
 * "Bash(rm -rf /)"      → 禁止 rm -rf /
 * "Bash(git push --force)" → 禁止强制推送
 * "WebFetch"            → 禁止所有 WebFetch 调用
 * "FileWrite(*.env)"    → 禁止写入 .env 文件
 * </pre>
 */
public class HardDenyManager {

    private static final Logger LOG = LoggerFactory.getLogger(HardDenyManager.class);

    private final List<HardDenyRule> rules = new ArrayList<>();

    /** 添加一条硬拒绝规则 */
    public void addRule(String ruleStr) {
        try {
            HardDenyRule rule = new HardDenyRule(ruleStr);
            rules.add(rule);
            LOG.info("添加 hard_deny 规则: {}", rule);
        } catch (IllegalArgumentException e) {
            LOG.warn("无效的 hard_deny 规则: {} — {}", ruleStr, e.getMessage());
        }
    }

    /** 批量添加规则 */
    public void addRules(List<String> ruleStrs) {
        if (ruleStrs != null) {
            ruleStrs.forEach(this::addRule);
        }
    }

    /**
     * 检查工具调用是否被禁止。
     *
     * @param tool      要调用的工具
     * @param inputJson 工具输入 JSON 字符串
     * @return 如果被禁止返回错误信息，否则返回 null
     */
    public String checkTool(Tool<?, ?> tool, String inputJson) {
        if (rules.isEmpty()) return null;

        for (HardDenyRule rule : rules) {
            if (rule.matches(tool.name(), inputJson)) {
                String msg = String.format("⛔ 安全拦截: 工具 %s 被 hard_deny 规则禁止: %s",
                    tool.name(), rule);
                LOG.warn(msg);
                return msg;
            }
        }
        return null;
    }

    /**
     * 在工具执行前检查，如果被禁止则抛出 ToolException。
     */
    public void enforce(Tool<?, ?> tool, String inputJson) throws ToolException {
        String blockMsg = checkTool(tool, inputJson);
        if (blockMsg != null) {
            throw new ToolException(blockMsg);
        }
    }

    /** 获取所有规则 */
    public List<HardDenyRule> getRules() {
        return List.copyOf(rules);
    }

    /** 规则数量 */
    public int size() { return rules.size(); }

    /** 清空规则 */
    public void clear() {
        rules.clear();
    }

    /** 添加默认安全规则（保护系统安全） */
    public void addDefaultRules() {
        addRule("Bash(rm -rf /)");
        addRule("Bash(rm -rf /*)");
        addRule("Bash(sudo rm)");
        addRule("Bash(dd if=/dev/zero)");
        addRule("Bash(mkfs.)");
        addRule("Bash(:(){ :|:& };:)");  // fork bomb
        addRule("Bash(wget http://)");
        addRule("Bash(curl http://)");
        addRule("Bash(chmod 777)");
    }
}
