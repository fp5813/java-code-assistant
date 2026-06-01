package com.moma.cli;

import com.moma.agent.*;
import com.moma.config.AppConfig;
import com.moma.controller.*;
import com.moma.di.Component;
import com.moma.di.Inject;
import com.moma.di.PostConstruct;
import com.moma.memory.MemoryStore;
import com.moma.model.ModelProvider;
import com.moma.model.OpenAiProvider;
import com.moma.model.ProviderRegistry;
import com.moma.plan.PlanManager;
import com.moma.security.HardDenyManager;
import com.moma.skill.SkillManager;
import com.moma.skill.SkillTool;
import com.moma.task.*;
import com.moma.tool.*;
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
import java.util.List;
import java.util.Map;

/**
 * REPL 交互界面。
 * 基于 JLine 实现终端交互循环，支持多行输入、Tab 补全、历史记录。
 * 通过 DI 容器管理依赖。
 */
@Component
public class CliApp {

    private static final Logger LOG = LoggerFactory.getLogger(CliApp.class);

    private static final String APP_NAME = "\u001B[36m墨码 (MoMa)\u001B[0m";
    private static final String PROMPT = "\u001B[32m>\u001B[0m ";

    private final AppConfig config;
    private final ProviderRegistry providerRegistry;
    private final ToolRegistry toolRegistry;
    private final AgentContext agentContext;
    private final AgentLoop agentLoop;
    private final PlanManager planManager;
    private final TaskManager taskManager;
    private final HardDenyManager hardDenyManager;
    private final SkillManager skillManager;
    private final MemoryStore memoryStore;

    /** 自动注入的所有命令控制器 */
    private final List<CommandController> controllers;

    private CommandParser commandParser;
    private volatile boolean running = true;

    @Inject
    public CliApp(AppConfig config,
                  ProviderRegistry providerRegistry,
                  ToolRegistry toolRegistry,
                  AgentContext agentContext,
                  AgentLoop agentLoop,
                  PlanManager planManager,
                  TaskManager taskManager,
                  HardDenyManager hardDenyManager,
                  SkillManager skillManager,
                  MemoryStore memoryStore,
                  List<CommandController> controllers) {
        this.config = config;
        this.providerRegistry = providerRegistry;
        this.toolRegistry = toolRegistry;
        this.agentContext = agentContext;
        this.agentLoop = agentLoop;
        this.planManager = planManager;
        this.taskManager = taskManager;
        this.hardDenyManager = hardDenyManager;
        this.skillManager = skillManager;
        this.memoryStore = memoryStore;
        this.controllers = controllers;
    }

    @PostConstruct
    public void init() {
        // 初始化安全规则
        hardDenyManager.addDefaultRules();

        // ── 初始化模型 ──
        if (config.getApiKey() != null && !config.getApiKey().isBlank()) {
            ModelProvider.ModelConfig modelConfig = ModelProvider.ModelConfig.builder()
                .baseUrl(config.getBaseUrl())
                .apiKey(config.getApiKey())
                .modelName(config.getModelName())
                .build();
            ChatLanguageModel initialModel = new OpenAiProvider().createModel(modelConfig);
            providerRegistry.initDefault(initialModel, "openai-compatible", config.getModelName());
        }

        // 用 settings.json 中的 providers 配置预热
        if (config.getProviders() != null && !config.getProviders().isEmpty()) {
            String modelName = config.getModelName();
            for (String providerName : config.getProviders().keySet()) {
                if (config.getProviders().get(providerName).getModels() != null &&
                    config.getProviders().get(providerName).getModels().contains(modelName)) {
                    providerRegistry.switchTo(providerName, modelName);
                    break;
                }
            }
        }

        // ── 初始化命令解析器 ──
        this.commandParser = createCommandParser();

        LOG.info("CliApp 初始化完成: {} 个控制器, {} 个工具, {} 个 Provider",
            controllers.size(), toolRegistry.size(), providerRegistry.getProviderNames().size());
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
     * 创建命令处理器，从所有 Controller 收集处理器。
     */
    private CommandParser createCommandParser() {
        Map<String, CommandParser.CommandHandler> handlers = new LinkedHashMap<>();

        // ── 内置命令 ──
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

        // ── 从所有 Controller 收集命令处理器 ──
        for (CommandController controller : controllers) {
            controller.registerHandlers(handlers);
        }

        return new CommandParser(handlers);
    }

    private void printWelcome() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════╗");
        System.out.println("  ║  " + APP_NAME + "    ║");
        System.out.println("  ║  以 AI 为笔，挥洒自如地编写代码    ║");
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
