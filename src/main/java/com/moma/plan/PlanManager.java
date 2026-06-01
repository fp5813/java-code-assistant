package com.moma.plan;

import com.moma.agent.AgentContext;

/**
 * 计划模式管理器。
 * 控制 Agent 是否处于"先计划后执行"的模式。
 * 对应 MiniClaude 的 EnterPlanMode/ExitPlanMode 工具。
 */
public class PlanManager {

    private volatile boolean planMode = false;

    /** 获取当前是否处于计划模式 */
    public boolean isPlanMode() { return planMode; }

    /**
     * 进入计划模式。
     * 切换后，Agent 在收到复杂请求时将先制定计划再执行。
     */
    public String enterPlanMode(AgentContext context) {
        if (planMode) {
            return "已处于计划模式。请描述需求，Agent 将先制定计划。";
        }
        planMode = true;
        context.setPlanMode(true);
        return "已进入计划模式。Agent 将先制定详细计划，确认后再逐步执行。\n" +
               "使用 ExitPlanMode 退出计划模式。";
    }

    /**
     * 退出计划模式，恢复直接执行模式。
     */
    public String exitPlanMode(AgentContext context) {
        if (!planMode) {
            return "当前不在计划模式。";
        }
        planMode = false;
        context.setPlanMode(false);
        return "已退出计划模式，恢复标准执行模式。";
    }
}
