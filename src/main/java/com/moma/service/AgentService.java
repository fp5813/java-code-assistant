package com.moma.service;

import com.moma.di.Component;
import com.moma.di.Inject;
import com.moma.di.PostConstruct;
import com.moma.agent.AgentContext;
import com.moma.agent.AgentLoop;
import com.moma.agent.SystemPrompt;
import com.moma.context.ContextManager;
import com.moma.skill.SkillManager;
import com.moma.tool.ToolRegistry;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;

import java.util.List;
import java.util.function.Supplier;

/**
 * Agent 服务层。封装 Agent 循环的执行逻辑。
 */
@Component
public class AgentService {

    private final Supplier<ChatLanguageModel> modelSupplier;
    private final ToolRegistry toolRegistry;
    private final AgentContext agentContext;
    private final MessageService messageService;
    private final ToolOrchestrationService toolOrchestrationService;
    private final ContextManager contextManager;
    private final SkillManager skillManager;

    private AgentLoop agentLoop;

    @Inject
    public AgentService(Supplier<ChatLanguageModel> modelSupplier,
                        ToolRegistry toolRegistry,
                        AgentContext agentContext,
                        MessageService messageService,
                        ToolOrchestrationService toolOrchestrationService,
                        ContextManager contextManager,
                        SkillManager skillManager) {
        this.modelSupplier = modelSupplier;
        this.toolRegistry = toolRegistry;
        this.agentContext = agentContext;
        this.messageService = messageService;
        this.toolOrchestrationService = toolOrchestrationService;
        this.contextManager = contextManager;
        this.skillManager = skillManager;
    }

    @PostConstruct
    public void init() {
        this.agentLoop = new AgentLoop(modelSupplier, toolRegistry, agentContext, contextManager, skillManager);
    }

    /**
     * 执行一次用户输入。
     */
    public AgentLoop.AgentResponse execute(String userInput) {
        return agentLoop.execute(userInput);
    }

    /**
     * 刷新系统提示词。
     */
    public void refreshSystemPrompt() {
        agentLoop.refreshSystemPrompt();
    }

    /**
     * 获取消息历史。
     */
    public List<ChatMessage> getMessageHistory() {
        return agentLoop.getMessageHistory();
    }

    /**
     * 获取底层 AgentLoop 实例。
     */
    public AgentLoop getAgentLoop() {
        return agentLoop;
    }

    /**
     * 获取消息服务。
     */
    public MessageService getMessageService() {
        return messageService;
    }

    /**
     * 获取工具编排服务。
     */
    public ToolOrchestrationService getToolOrchestrationService() {
        return toolOrchestrationService;
    }
}
