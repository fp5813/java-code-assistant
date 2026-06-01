package com.codeassist.cli;

import com.codeassist.agent.*;
import com.codeassist.config.AppConfig;
import com.codeassist.model.ModelProvider;
import com.codeassist.model.OpenAiProvider;
import com.codeassist.model.ProviderRegistry;
import com.codeassist.plan.*;
import com.codeassist.lsp.LspClient;
import com.codeassist.memory.*;
import com.codeassist.security.HardDenyManager;
import com.codeassist.skill.*;
import com.codeassist.task.*;
import com.codeassist.tool.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.jline.reader.*;
import org.jline.reader.impl.DefaultParser;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * REPL 交互界面。
 * 基于 JLine 实现终端交互循环，支持多行输入、Tab 补全、历史记录。
 */
public class CliApp {

    private static final Logger LOG = LoggerFactory.getLogger(CliApp.class);

    private static final String APP_NAME = "\u001B[36mJava Code Assistant\u001B[0m";
    private static final String PROMPT = "\u001B[32m>\u001B[0m ";

    private final AppConfig config;
    private final ProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private AgentContext agentContext;
    private final AgentLoop agentLoop;
    private final CommandParser commandParser;
    private final SessionManager sessionManager;
    private final PlanManager planManager;
    private final TaskManager taskManager;
    private final HardDenyManager hardDenyManager;
    private final SkillManager skillManager;
    private final MemoryStore memoryStore;

    private volatile boolean running = true;

    public CliApp(AppConfig config) {
        this.config = config;
        this.sessionManager = new SessionManager();
        this.planManager = new PlanManager();
        this.taskManager = new TaskManager(sessionManager.getSessionId());
        this.hardDenyManager = new HardDenyManager();
        this.skillManager = new SkillManager();
        this.memoryStore = new MemoryStore();
        hardDenyManager.addDefaultRules();

        // ── 初始化 Provider 注册中心 ──
        this.providerRegistry = new ProviderRegistry();
        providerRegistry.register(new OpenAiProvider());
        providerRegistry.loadConfigs(config.getProviders());

        // ── 初始化模型 ──
        ModelProvider.ModelConfig modelConfig = ModelProvider.ModelConfig.builder()
            .baseUrl(config.getBaseUrl())
            .apiKey(config.getApiKey())
            .modelName(config.getModelName())
            .build();
        ChatLanguageModel initialModel = new OpenAiProvider().createModel(modelConfig);
        providerRegistry.initDefault(initialModel, "openai-compatible", config.getModelName());

        // ── 用 settings.json 中的 providers 配置预热 ──
        if (config.getProviders() != null && !config.getProviders().isEmpty()) {
            String modelName = config.getModelName();
            // 尝试从 providers 中找到匹配的 Provider
            for (String providerName : config.getProviders().keySet()) {
                if (config.getProviders().get(providerName).getModels() != null &&
                    config.getProviders().get(providerName).getModels().contains(modelName)) {
                    providerRegistry.switchTo(providerName, modelName);
                    break;
                }
            }
        }

        // ── 初始化工具注册中心 ──
        this.toolRegistry = new ToolRegistry();
        toolRegistry.setHardDenyManager(hardDenyManager);
        toolRegistry.register(new ReadTool());
        toolRegistry.register(new WriteTool());
        toolRegistry.register(new GlobTool());
        toolRegistry.register(new EditTool());
        toolRegistry.register(new GrepTool());
        toolRegistry.register(new BashTool());

        // 任务工具
        toolRegistry.register(new TaskCreateTool());
        toolRegistry.register(new TaskListTool());
        toolRegistry.register(new TaskUpdateTool());
        toolRegistry.register(new TaskGetTool());

        // 计划模式工具
        toolRegistry.register(new EnterPlanModeTool(planManager));
        toolRegistry.register(new ExitPlanModeTool(planManager));

        // Git 工具
        toolRegistry.register(new GitDiffTool());
        toolRegistry.register(new GitStatusTool());
        toolRegistry.register(new GitCommitTool());

        // 技能工具
        toolRegistry.register(new SkillTool(skillManager));

        // 记忆工具
        toolRegistry.register(new MemorySaveTool(memoryStore));
        toolRegistry.register(new MemorySearchTool(memoryStore));

        // HTML 输出
        toolRegistry.register(new HtmlOutputTool());

        // LSP 诊断
        toolRegistry.register(new LspTool());

        // ── 初始化 Agent 上下文 ──
        String cwd = System.getProperty("user.dir");
        this.agentContext = new AgentContext(cwd, null, providerRegistry.getActiveModel());
        agentContext.setTaskManager(taskManager);

        // ── 初始化 Agent 循环（使用 ProviderRegistry 作为模型提供者） ──
        this.agentLoop = new AgentLoop(
            () -> providerRegistry.getCurrentModel(),
            toolRegistry, agentContext);

        // ── 初始化命令解析器 ──
        this.commandParser = createCommandParser();
    }

    /**
     * 启动 REPL 循环。
     */
    public void start() {
        try (Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build()) {

            LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .parser(new DefaultParser())
                .completer(new StringsCompleter(
                    "/help", "/clear", "/exit", "/status",
                    "/model", "/provider", "/sessions", "/plan", "/memory"
                ))
                .variable(LineReader.HISTORY_FILE,
                    java.nio.file.Paths.get(System.getProperty("user.home"), ".ca_history"))
                .build();

            printWelcome();

            while (running) {
                try {
                    String line = reader.readLine(PROMPT);

                    if (line == null) {
                        System.out.println();
                        break;
                    }

                    String input = line.trim();
                    if (input.isEmpty()) {
                        continue;
                    }

                    if (commandParser.isCommand(input)) {
                        handleCommand(input);
                        continue;
                    }

                    // ── 用户输入 → Agent 处理 ──
                    if (providerRegistry.getCurrentModel() == null) {
                        System.out.println("\u001B[31m错误: 模型未配置。请先配置 API Key 或使用 /provider 切换\u001B[0m");
                        continue;
                    }

                    System.out.println("\u001B[90m⏳ AI 思考中...\u001B[0m");

                    long startTime = System.currentTimeMillis();
                    AgentLoop.AgentResponse response = agentLoop.execute(input);
                    long elapsed = System.currentTimeMillis() - startTime;

                    System.out.println();
                    if (response.text() != null && !response.text().isBlank()) {
                        System.out.println(response.text());
                    }
                    System.out.println();

                    printStats(elapsed, response);

                } catch (EndOfFileException e) {
                    break;
                } catch (UserInterruptException e) {
                    System.out.println("\n\u001B[90m已中断\u001B[0m");
                }
            }
        } catch (IOException e) {
            LOG.error("终端初始化失败", e);
            System.err.println("错误: 无法初始化终端 - " + e.getMessage());
        }
    }

    private void handleCommand(String input) {
        CommandParser.CommandResult result = commandParser.execute(input);
        if (result.message() != null && !result.message().isBlank()) {
            System.out.println(result.message());
        }
        if (result.action() != null) {
            result.action().run();
        }
    }

    /**
     * 创建命令处理器，含 /provider 和 /model 运行时热切换。
     */
    private CommandParser createCommandParser() {
        Map<String, CommandParser.CommandHandler> handlers = new LinkedHashMap<>();

        handlers.put("help", args -> {
            StringBuilder sb = new StringBuilder();
            sb.append("\u001B[1m可用命令:\u001B[0m\n");
            sb.append("  /help                  显示此帮助\n");
            sb.append("  /clear                 清除屏幕\n");
            sb.append("  /exit                  退出程序\n");
            sb.append("  /status                显示当前状态\n");
            sb.append("  /provider              列出所有 Provider\n");
            sb.append("  /provider <name>       切换到指定 Provider\n");
            sb.append("  /model <name>          切换当前 Provider 的模型\n");
            sb.append("  /plan                  进入计划模式（先计划后执行）\n");
            sb.append("  /plan execute          退出计划模式，开始执行\n");
            sb.append("  /tasks                 显示任务列表\n");
            sb.append("  /tasks clear           清除所有任务\n");
            sb.append("  /sessions              显示历史会话列表\n");
            return new CommandParser.CommandResult(true, sb.toString(), null);
        });

        handlers.put("clear", args -> {
            System.out.print("\033[H\033[2J");
            System.out.flush();
            return new CommandParser.CommandResult(true, null, null);
        });

        handlers.put("exit", args -> {
            running = false;
            return new CommandParser.CommandResult(true, "再见！", () -> running = false);
        });
        handlers.put("quit", args -> handlers.get("exit").handle(args));

        handlers.put("status", args -> {
            String status = String.format("""
                \u001B[1m当前状态:\u001B[0m
                  工作目录: %s
                  Provider: %s
                  模型: %s
                  Base URL: %s
                  可用工具: %d
                  计划模式: %s
                  任务数: %d
                  Token 使用: %d in / %d out
                  工具调用: %d
                """,
                agentContext.getWorkingDirectory(),
                providerRegistry.getActiveProvider(),
                providerRegistry.getActiveModel(),
                config.getBaseUrl(),
                toolRegistry.size(),
                planManager.isPlanMode() ? "\u001B[33mON\u001B[0m" : "OFF",
                taskManager.size(),
                agentContext.getInputTokens(),
                agentContext.getOutputTokens(),
                agentContext.getTotalToolCalls()
            );
            return new CommandParser.CommandResult(true, status, null);
        });

        // ── /provider 命令 ──
        handlers.put("provider", args -> {
            if (args == null || args.isBlank()) {
                // 列出所有 Provider
                StringBuilder sb = new StringBuilder("\u001B[1m可用 Provider:\u001B[0m\n");
                for (String name : providerRegistry.getProviderNames()) {
                    String marker = name.equals(providerRegistry.getActiveProvider())
                        ? " \u001B[32m← 当前\u001B[0m" : "";
                    sb.append("  ").append(providerRegistry.describeProvider(name)).append(marker).append("\n");
                }
                return new CommandParser.CommandResult(true, sb.toString(), null);
            }

            // 解析 provider name (支持 --session 标记)
            String providerName = args.trim();
            String result = providerRegistry.switchTo(providerName, null);
            if (result.startsWith("错误")) {
                return new CommandParser.CommandResult(false, result, null);
            }
            // 更新 HUD
            agentContext = new AgentContext(
                agentContext.getWorkingDirectory(),
                null,
                providerRegistry.getActiveModel()
            );
            return new CommandParser.CommandResult(true, result, null);
        });

        // ── /model 命令 ──
        handlers.put("model", args -> {
            if (args == null || args.isBlank()) {
                return new CommandParser.CommandResult(true,
                    "当前模型: " + providerRegistry.getActiveModel()
                    + "\n使用 /model <name> 切换模型（如 /model gpt-4o-mini）", null);
            }

            String modelName = args.trim();
            String result = providerRegistry.switchTo(
                providerRegistry.getActiveProvider(), modelName);
            if (result.startsWith("错误")) {
                return new CommandParser.CommandResult(false, result, null);
            }
            return new CommandParser.CommandResult(true, result, null);
        });

        // ── /tasks 命令 ──
        handlers.put("tasks", args -> {
            if (args != null && args.trim().equalsIgnoreCase("clear")) {
                taskManager.clear();
                return new CommandParser.CommandResult(true, "所有任务已清除。", null);
            }
            var all = taskManager.getAllTasks();
            if (all.isEmpty()) {
                return new CommandParser.CommandResult(true, "暂无任务。使用 TaskCreate 工具创建任务。", null);
            }
            StringBuilder sb = new StringBuilder("\u001B[1m任务列表:\u001B[0m\n");
            for (Task t : all) {
                sb.append(String.format("  [%s] %s — %s%n",
                    t.getId(), t.getDescription(),
                    t.getStatus()));
            }
            sb.append("共 ").append(all.size()).append(" 个任务");
            return new CommandParser.CommandResult(true, sb.toString(), null);
        });

        // ── /plan 命令 ──
        handlers.put("plan", args -> {
            if (args != null && args.trim().equalsIgnoreCase("execute")) {
                String result = planManager.exitPlanMode(agentContext);
                agentLoop.refreshSystemPrompt(); // 立即刷新系统提示词
                return new CommandParser.CommandResult(true, result + "\n你可以继续描述需求。", null);
            }
            if (planManager.isPlanMode()) {
                return new CommandParser.CommandResult(true,
                    "当前已在计划模式。描述需求后 Agent 将先制定计划。", null);
            }
            String result = planManager.enterPlanMode(agentContext);
            agentLoop.refreshSystemPrompt(); // 立即刷新系统提示词
            return new CommandParser.CommandResult(true, result, null);
        });
        // ── /memory 命令 ──
        handlers.put("memory", args -> {
            String query = args != null ? args.trim() : "";
            if (query.isBlank()) {
                var mems = memoryStore.getProjectMemories(agentContext.getProjectName(), 10);
                if (mems.isEmpty()) {
                    return new CommandParser.CommandResult(true, "暂无记忆。Agent 使用 MemorySave 工具保存重要信息。", null);
                }
                StringBuilder sb = new StringBuilder("\u001B[1m当前项目的记忆:\u001B[0m\n");
                for (var m : mems) {
                    sb.append(String.format("  [%s] [%s] %s%n",
                        m.getType(), m.getId(),
                        m.getContent().length() > 80 ? m.getContent().substring(0, 80) + "..." : m.getContent()));
                }
                return new CommandParser.CommandResult(true, sb.toString(), null);
            }
            var results = memoryStore.search(agentContext.getProjectName(), null, query, 10);
            if (results.isEmpty()) {
                return new CommandParser.CommandResult(true, "未找到匹配的记忆。", null);
            }
            StringBuilder sb = new StringBuilder("\u001B[1m搜索记忆结果:\u001B[0m\n");
            for (var m : results) {
                sb.append(String.format("  [%s] [%s] %s%n", m.getType(), m.getId(), m.getContent()));
            }
            return new CommandParser.CommandResult(true, sb.toString(), null);
        });
        handlers.put("sessions", args -> {
            var sessions = SessionManager.listSessions();
            if (sessions.isEmpty()) {
                return new CommandParser.CommandResult(true, "暂无历史会话。", null);
            }
            StringBuilder sb = new StringBuilder("\u001B[1m历史会话:\u001B[0m\n");
            for (int i = 0; i < Math.min(sessions.size(), 10); i++) {
                var s = sessions.get(i);
                sb.append(String.format("  %s  %s  %s  (%d 条消息)%n",
                    s.sessionId(), s.projectName(), s.modelName(), s.messageCount()));
            }
            return new CommandParser.CommandResult(true, sb.toString(), null);
        });

        return new CommandParser(handlers);
    }

    private void printWelcome() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║  " + APP_NAME + "    ║");
        System.out.println("  ║  基于 LangChain4j 的 AI 编程助手    ║");
        System.out.println("  ╚══════════════════════════════════════╝");
        System.out.println();
        System.out.println("  工作目录: " + agentContext.getWorkingDirectory());
        System.out.println("  Provider: " + providerRegistry.getActiveProvider());
        System.out.println("  模型: " + providerRegistry.getActiveModel());
        System.out.println("  输入 /help 查看帮助, /exit 退出");
        System.out.println();
    }

    private void printStats(long elapsedMs, AgentLoop.AgentResponse response) {
        System.out.printf("\u001B[90m⏱ %.1fs  | 🔤 %din / %dout  | 🛠 %d 次工具调用\u001B[0m%n",
            elapsedMs / 1000.0,
            response.inputTokens(),
            response.outputTokens(),
            response.totalToolCalls());
    }
}
