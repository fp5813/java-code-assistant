package com.moma.config;

import com.moma.agent.AgentContext;
import com.moma.agent.AgentLoop;
import com.moma.agent.SessionManager;
import com.moma.cli.CliApp;
import com.moma.concurrent.EventBus;
import com.moma.context.ContextManager;
import com.moma.context.ContextWindowRegistry;
import com.moma.controller.*;
import com.moma.di.Bean;
import com.moma.di.Configuration;
import com.moma.learning.PatternLearner;
import com.moma.memory.MemorySaveTool;
import com.moma.memory.MemorySearchTool;
import com.moma.memory.MemoryStore;
import com.moma.model.ModelProvider;
import com.moma.model.OpenAiProvider;
import com.moma.model.ProviderRegistry;
import com.moma.plan.EnterPlanModeTool;
import com.moma.plan.ExitPlanModeTool;
import com.moma.plan.PlanManager;
import com.moma.security.HardDenyManager;
import com.moma.service.AgentLearningService;
import com.moma.skill.SkillManager;
import com.moma.skill.SkillTool;
import com.moma.task.*;
import com.moma.tool.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Supplier;

/**
 * DI 配置类。管理复杂 Bean 的创建和组装。
 * 对应 Spring {@code @Configuration} + {@code @Bean} 模式。
 */
@Configuration
public class DiConfig {

    private static final Logger LOG = LoggerFactory.getLogger(DiConfig.class);

    // ───────────────────────────────────────────
    // 基础设施 Bean
    // ───────────────────────────────────────────

    @Bean
    public AppConfig appConfig() {
        try {
            return ConfigLoader.load();
        } catch (Exception e) {
            LOG.warn("配置加载失败，使用默认配置: {}", e.getMessage());
            return new AppConfig();
        }
    }

    @Bean
    public HardDenyManager hardDenyManager() {
        HardDenyManager manager = new HardDenyManager();
        manager.addDefaultRules();
        return manager;
    }

    @Bean
    public SessionManager sessionManager() {
        return new SessionManager();
    }

    @Bean
    public PlanManager planManager() {
        return new PlanManager();
    }

    @Bean
    public TaskManager taskManager(SessionManager sessionManager) {
        return new TaskManager(sessionManager.getSessionId());
    }
    @Bean
    public SkillManager skillManager() {
        return new SkillManager();
    }

    @Bean
    public MemoryStore memoryStore() {
        return new MemoryStore();
    }

    @Bean
    public EventBus eventBus() {
        return new EventBus();
    }

    @Bean
    public PatternLearner patternLearner() {
        return new PatternLearner();
    }

    @Bean
    public AgentLearningService agentLearningService(EventBus eventBus, MemoryStore memoryStore) {
        return new AgentLearningService(eventBus, memoryStore);
    }

    // ───────────────────────────────────────────
    // 上下文窗口管理
    // ───────────────────────────────────────────

    @Bean
    public ContextWindowRegistry contextWindowRegistry() {
        return new ContextWindowRegistry();
    }

    @Bean
    public ContextManager contextManager(ContextWindowRegistry contextWindowRegistry,
                                          ToolRegistry toolRegistry) {
        return new ContextManager(contextWindowRegistry, toolRegistry);
    }

    @Bean
    public ProviderRegistry providerRegistry(AppConfig config) {
        ProviderRegistry registry = new ProviderRegistry();
        registry.register(new OpenAiProvider());
        registry.loadConfigs(config.getProviders());

        // 初始化默认模型（如已配置 API Key）
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            var modelConfig = ModelProvider.ModelConfig.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .build();
            ChatLanguageModel initialModel = new OpenAiProvider().createModel(modelConfig);
            registry.initDefault(initialModel, "openai-compatible", config.getModelName());
        }

        // providers 预热
        if (config.getProviders() != null && !config.getProviders().isEmpty()) {
            for (String providerName : config.getProviders().keySet()) {
                var pc = config.getProviders().get(providerName);
                if (pc.getModels() != null && pc.getModels().contains(config.getModelName())) {
                    registry.switchTo(providerName, config.getModelName());
                    break;
                }
            }
        }

        return registry;
    }

    // ───────────────────────────────────────────
    // 工具注册中心
    // ───────────────────────────────────────────

    @Bean
    public ToolRegistry toolRegistry(HardDenyManager hardDenyManager,
                                     PlanManager planManager,
                                     SkillManager skillManager,
                                     MemoryStore memoryStore,
                                     PatternLearner patternLearner) {
        ToolRegistry registry = new ToolRegistry();
        registry.setHardDenyManager(hardDenyManager);

        // 文件操作工具
        registry.register(new ReadTool());
        registry.register(new WriteTool());
        registry.register(new GlobTool());
        registry.register(new EditTool());
        registry.register(new GrepTool());
        registry.register(new BashTool());
        registry.register(new LspTool());

        // 任务工具
        registry.register(new TaskCreateTool());
        registry.register(new TaskListTool());
        registry.register(new TaskUpdateTool());
        registry.register(new TaskGetTool());

        // 计划模式工具
        registry.register(new EnterPlanModeTool(planManager));
        registry.register(new ExitPlanModeTool(planManager));

        // Git 工具
        registry.register(new GitDiffTool());
        registry.register(new GitStatusTool());
        registry.register(new GitCommitTool());

        // 技能工具
        registry.register(new SkillTool(skillManager));

        // 记忆工具
        registry.register(new MemorySaveTool(memoryStore));
        registry.register(new MemorySearchTool(memoryStore));

        // HTML 输出
        registry.register(new HtmlOutputTool());

        // GitHub CLI 工具
        registry.register(new GhPrCreateTool());
        registry.register(new GhPrListTool());
        registry.register(new GhIssueListTool());

        // ── 墨码开发工具（日志监控 + 自我学习 + 知识库）──
        registry.register(new MomaLogTool());
        registry.register(new MomaMonitorTool());
        registry.register(new SaveExperienceTool(memoryStore));
        registry.register(new PatternLearnTool(patternLearner));
        registry.register(new KnowledgeBaseTool());

        LOG.info("已注册 {} 个工具", registry.size());
        return registry;
    }

    // ───────────────────────────────────────────
    // Agent 层
    // ───────────────────────────────────────────

    @Bean
    public AgentContext agentContext(AppConfig config, TaskManager taskManager) {
        String cwd = System.getProperty("user.dir");
        AgentContext ctx = new AgentContext(cwd, null, config.getModelName());
        ctx.setTaskManager(taskManager);
        return ctx;
    }

    @Bean
    public Supplier<ChatLanguageModel> modelSupplier(ProviderRegistry providerRegistry) {
        return () -> providerRegistry.getCurrentModel();
    }

    @Bean
    public AgentLoop agentLoop(Supplier<ChatLanguageModel> modelSupplier,
                               ToolRegistry toolRegistry,
                               AgentContext agentContext,
                               ContextManager contextManager,
                               SkillManager skillManager) {
        return new AgentLoop(modelSupplier, toolRegistry, agentContext, contextManager, skillManager);
    }

    // ───────────────────────────────────────────
    // 命令控制器
    // ───────────────────────────────────────────

    @Bean
    public List<CommandController> commandControllers(AppConfig config,
                                                       ProviderRegistry providerRegistry,
                                                       ToolRegistry toolRegistry,
                                                       PlanManager planManager,
                                                       TaskManager taskManager,
                                                       AgentContext agentContext,
                                                       AgentLoop agentLoop,
                                                       MemoryStore memoryStore,
                                                       ContextManager contextManager,
                                                       SkillManager skillManager,
                                                       PatternLearner patternLearner) {
        return List.of(
            new ProviderController(providerRegistry),
            new PlanController(planManager, agentContext, agentLoop),
            new TaskController(taskManager),
            new MemoryController(memoryStore, agentContext),
            new StatusController(providerRegistry, toolRegistry, planManager,
                taskManager, agentContext, config, contextManager),
            new MomaDevController(skillManager, patternLearner, memoryStore)
        );
    }

    // ───────────────────────────────────────────
    // CLI 层
    // ───────────────────────────────────────────

    @Bean
    public CliApp cliApp(AppConfig config,
                         ProviderRegistry providerRegistry,
                         ToolRegistry toolRegistry,
                         AgentContext agentContext,
                         AgentLoop agentLoop,
                         PlanManager planManager,
                         TaskManager taskManager,
                         HardDenyManager hardDenyManager,
                         SkillManager skillManager,
                         MemoryStore memoryStore,
                         AgentLearningService learningService,
                         List<CommandController> controllers) {
        return new CliApp(config, providerRegistry, toolRegistry, agentContext,
            agentLoop, planManager, taskManager, hardDenyManager,
            skillManager, memoryStore, learningService, controllers);
    }
}
