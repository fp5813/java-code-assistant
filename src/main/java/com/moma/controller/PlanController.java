package com.moma.controller;

import com.moma.agent.AgentContext;
import com.moma.agent.AgentLoop;
import com.moma.cli.CommandParser;
import com.moma.di.Inject;
import com.moma.plan.PlanManager;

import java.util.Map;

/**
 * 处理 /plan 和 /plan execute 命令的控制器。
 */
public class PlanController extends CommandController {

    private final PlanManager planManager;
    private final AgentContext agentContext;
    private final AgentLoop agentLoop;

    @Inject
    public PlanController(PlanManager planManager, AgentContext agentContext, AgentLoop agentLoop) {
        super("plan");
        this.planManager = planManager;
        this.agentContext = agentContext;
        this.agentLoop = agentLoop;
    }

    @Override
    public void registerHandlers(Map<String, CommandParser.CommandHandler> handlers) {
        // ── /plan 命令 ──
        handlers.put("plan", args -> {
            if (args != null && args.trim().equalsIgnoreCase("execute")) {
                String result = planManager.exitPlanMode(agentContext);
                agentLoop.refreshSystemPrompt();
                return new CommandParser.CommandResult(true, result + "\n你可以继续描述需求。", null);
            }
            if (planManager.isPlanMode()) {
                return new CommandParser.CommandResult(true,
                    "当前已在计划模式。描述需求后 Agent 将先制定计划。", null);
            }
            String result = planManager.enterPlanMode(agentContext);
            agentLoop.refreshSystemPrompt();
            return new CommandParser.CommandResult(true, result, null);
        });
    }
}
