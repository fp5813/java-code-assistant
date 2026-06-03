package com.moma.agent;

import com.moma.context.ContextManager;
import com.moma.skill.SkillManager;
import com.moma.tool.Tool;
import com.moma.tool.ToolException;
import com.moma.tool.ToolRegistry;
import com.moma.tool.ToolResult;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.*;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.output.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 核心 Agent 循环：perceive → think → act。
 *
 * <p>对应 MiniClaude 的 {@code queryLoop()} 模式。</p>
 *
 * <p>工具编排策略（参照 MiniClaude 的 ToolOrchestration）:</p>
 * <ul>
 *   <li>只读工具（Read/Glob/Grep）：并发执行，互不干扰</li>
 *   <li>写工具（Write/Edit/Bash）：串行执行，避免竞态</li>
 * </ul>
 */
public class AgentLoop {

    private static final Logger LOG = LoggerFactory.getLogger(AgentLoop.class);

    /** 模型提供者（支持运行时热切换，每次迭代获取最新模型） */
    private final Supplier<ChatLanguageModel> modelSupplier;
    private final ToolRegistry toolRegistry;
    private final AgentContext context;
    private final List<ChatMessage> messages;
    private final ContextManager contextManager;
    private final SkillManager skillManager;

    /** 工具执行的线程池 */
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    private static final int MAX_ITERATIONS = 20;
    private static final int TOOL_TIMEOUT_SECONDS = 120;

    /** 工具调用格式纠正提示 */
    private static final String TOOL_RETRY_PROMPT = """
        [系统提示] 你的上一次回复似乎需要调用工具，但格式不符合要求。
        请严格按照以下格式输出工具调用（使用 JSON 格式）：

        ```json
        {
          "name": "工具名",
          "arguments": {
            "参数名": "参数值"
          }
        }
        ```

        可用的工具列表包括: Read, Write, Edit, Grep, Glob, Bash, TaskCreate,
        TaskList, TaskGet, TaskUpdate, Skill, MemorySave, MemorySearch,
        MomaLog, MomaMonitor, PatternLearn, KnowledgeSearch, GitDiff, GitStatus。
        请重新输出需要调用的工具。
        """;

    /**
     * 使用固定模型构造。
     */
    public AgentLoop(ChatLanguageModel model, ToolRegistry toolRegistry,
                     AgentContext context, ContextManager contextManager,
                     SkillManager skillManager) {
        this(() -> model, toolRegistry, context, contextManager, skillManager);
    }

    /**
     * 使用模型提供者构造（支持运行时热切换）。
     */
    public AgentLoop(Supplier<ChatLanguageModel> modelSupplier, ToolRegistry toolRegistry,
                     AgentContext context, ContextManager contextManager,
                     SkillManager skillManager) {
        this.modelSupplier = modelSupplier;
        this.toolRegistry = toolRegistry;
        this.context = context;
        this.contextManager = contextManager;
        this.skillManager = skillManager;
        this.messages = new ArrayList<>();
        refreshSystemPrompt();
    }

    /**
     * 刷新系统提示词（用于 plan mode 切换后更新）。
     */
    public void refreshSystemPrompt() {
        if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage) {
            messages.set(0, new SystemMessage(SystemPrompt.build(context, skillManager)));
        } else {
            messages.add(0, new SystemMessage(SystemPrompt.build(context, skillManager)));
        }
    }

    /**
     * 执行一次用户输入。
     */
    public AgentResponse execute(String userInput) {
        messages.add(new UserMessage(userInput));
        trimHistory();

        for (int iteration = 1; iteration <= MAX_ITERATIONS; iteration++) {

            LOG.debug("Agent loop iteration {}/{}: {} messages",
                iteration, MAX_ITERATIONS, messages.size());

            // 刷新系统提示词（使 plan mode 切换生效）
            refreshSystemPrompt();

            List<ToolSpecification> toolSpecs = toolRegistry.getToolSpecifications();

            Response<AiMessage> response;
            try {
                ChatLanguageModel model = modelSupplier.get();
                if (model == null) {
                    return new AgentResponse("错误: 模型未配置。请使用 /provider 命令切换 Provider。",
                        context.getInputTokens(), context.getOutputTokens(),
                        context.getTotalToolCalls());
                }
                response = model.generate(messages, toolSpecs);
            } catch (Exception e) {
                LOG.error("LLM 调用失败", e);
                return new AgentResponse("抱歉，AI 模型调用失败: " + e.getMessage(),
                    context.getInputTokens(), context.getOutputTokens(),
                    context.getTotalToolCalls());
            }

            AiMessage aiMessage = response.content();
            messages.add(aiMessage);

            if (response.tokenUsage() != null) {
                context.recordTokens(
                    response.tokenUsage().inputTokenCount(),
                    response.tokenUsage().outputTokenCount()
                );
            }

            if (!aiMessage.hasToolExecutionRequests()) {
                String text = aiMessage.text();
                if (text == null || text.isBlank()) {
                    return new AgentResponse("(无文本回复)",
                        context.getInputTokens(), context.getOutputTokens(),
                        context.getTotalToolCalls());
                }

                // ── 兼容 Qwen 等模型的 JSON 文本工具调用格式 ──
                // 模型以 JSON 文本形式输出工具调用（而非原生 tool_calls 协议）
                List<ToolExecutionRequest> parsedRequests = parseJsonToolCalls(text);
                if (!parsedRequests.isEmpty()) {
                    LOG.info("从 JSON 文本中解析出 {} 个工具调用", parsedRequests.size());
                    // 用解析出的请求替换原消息（移除 JSON 文本消息）
                    executeToolRequests(parsedRequests);
                    continue; // 继续 think-act 循环
                }

                // ── 重试：文本中可能包含工具调用但解析失败 ──
                // 仅在仍有迭代空间且文本看起来像工具调用时重试
                if (iteration < MAX_ITERATIONS - 1 && looksLikeToolCall(text)) {
                    LOG.info("文本疑似工具调用但解析失败，尝试请求 LLM 重试 (迭代 {})", iteration);
                    messages.add(new UserMessage(TOOL_RETRY_PROMPT));
                    continue;
                }

                // ── 普通文本回复 ──
                return new AgentResponse(text,
                    context.getInputTokens(), context.getOutputTokens(),
                    context.getTotalToolCalls());
            }

            // ── ACT: 执行原生 tool_calls ──
            executeToolRequests(aiMessage.toolExecutionRequests());
            // 继续循环
        }

        return new AgentResponse("已达到最大工具调用次数 (" + MAX_ITERATIONS + ")，请简化请求。",
            context.getInputTokens(), context.getOutputTokens(),
            context.getTotalToolCalls());
    }

    /**
     * 并发执行多个工具调用。
     */
    private Map<String, ToolResult> executeConcurrently(List<ToolExecutionRequest> requests) {
        Map<String, ToolResult> results = new ConcurrentHashMap<>();
        List<CompletableFuture<Void>> futures = new ArrayList<>();

        for (ToolExecutionRequest req : requests) {
            CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                ToolResult result = executeTool(req);
                results.put(req.id(), result);
            }, executor);
            futures.add(future);
        }

        try {
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(TOOL_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            LOG.warn("并发工具执行超时 ({}s)", TOOL_TIMEOUT_SECONDS);
            for (ToolExecutionRequest req : requests) {
                results.putIfAbsent(req.id(),
                    ToolResult.failure("工具执行超时 (" + TOOL_TIMEOUT_SECONDS + "s)", 0));
            }
        } catch (Exception e) {
            LOG.error("并发工具执行失败", e);
        }

        return results;
    }

    /**
     * 执行单个工具调用。
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    private ToolResult executeTool(ToolExecutionRequest request) {
        String toolName = request.name();
        String args = request.arguments();

        LOG.info("执行工具: {} (参数长度: {})", toolName, args.length());

        Tool tool;
        try {
            tool = toolRegistry.getToolChecked(toolName, args);
        } catch (ToolException e) {
            return ToolResult.failure(e.getMessage(), 0);
        }
        if (tool == null) {
            return ToolResult.failure("未知工具: " + toolName, 0);
        }

        long startTime = System.currentTimeMillis();
        try {
            Object input = tool.parseInput(args);
            Object output = tool.execute(input, context);
            long duration = System.currentTimeMillis() - startTime;
            String formatted = tool.formatOutput(output);
            return ToolResult.success(formatted, duration);
        } catch (ToolException e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.warn("工具 {} 执行失败: {}", toolName, e.getMessage());
            return ToolResult.failure(e.getMessage(), duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            LOG.error("工具 {} 执行异常: {}", toolName, e.getMessage(), e);
            return ToolResult.failure("内部错误: " + e.getMessage(), duration);
        }
    }

    /**
     * 从模型文本回复中解析 JSON 格式的工具调用。
     * 兼容 Qwen 等以文本形式输出工具调用（而非原生 tool_calls 协议）的模型。
     *
     * <p>支持多种 JSON 格式变体：</p>
     * <ul>
     *   <li>```json ``` 代码块中的完整工具调用</li>
     *   <li>裸 JSON 对象：{@code {"name":"Tool","arguments":{...}}}</li>
     *   <li>非标准分隔符：{@code json{...}} 或 {@code <function_call>{...}}</li>
     *   <li>多行缩进 arguments（跨行匹配嵌套花括号）</li>
     *   <li>单引号 key：{@code {'name':'Tool','arguments':{...}}}</li>
     * </ul>
     */
    public static List<ToolExecutionRequest> parseJsonToolCalls(String text) {
        if (text == null || text.isBlank()) return List.of();

        List<ToolExecutionRequest> results = new ArrayList<>();
        int idCounter = 0;

        // ── 策略 1: ```json ... ``` 代码块 ──
        Pattern codeBlockPattern = Pattern.compile(
            "```(?:json)?\\s*\\{\\s*[\"']name[\"']\\s*:\\s*[\"'](\\w+)[\"']\\s*,?\\s*[\"']arguments[\"']\\s*:\\s*(\\{(?:[^{}]|\\{[^{}]*\\})*\\})\\s*\\}\\s*```",
            Pattern.DOTALL);
        Matcher m = codeBlockPattern.matcher(text);
        while (m.find()) {
            String toolName = m.group(1);
            String argsJson = cleanArgsJson(m.group(2));
            results.add(buildRequest(toolName, argsJson, idCounter++));
        }

        // ── 策略 2: 裸 JSON 对象（跨行，贪婪匹配） ──
        if (results.isEmpty()) {
            Pattern barePattern = Pattern.compile(
                "\\{\\s*[\"']name[\"']\\s*:\\s*[\"'](\\w+)[\"']\\s*,?\\s*[\"']arguments[\"']\\s*:\\s*(\\{(?:[^{}]|\\{[^{}]*\\}|\\[[^\\]]*\\])*\\})\\s*\\}",
                Pattern.DOTALL);
            Matcher bm = barePattern.matcher(text);
            while (bm.find()) {
                String toolName = bm.group(1);
                String argsJson = cleanArgsJson(bm.group(2));
                results.add(buildRequest(toolName, argsJson, idCounter++));
            }
        }

        // ── 策略 3: 非标准包装器 ──
        // json{...}, <function_call>{...}, <tool_call>{...}
        if (results.isEmpty()) {
            Pattern wrapperPattern = Pattern.compile(
                "(?:json|<function_call>|<tool_call>)\\s*\\{\\s*[\"']name[\"']\\s*:\\s*[\"'](\\w+)[\"']\\s*,?\\s*[\"']arguments[\"']\\s*:\\s*(\\{(?:[^{}]|\\{[^{}]*\\})*\\})\\s*\\}",
                Pattern.DOTALL);
            Matcher wm = wrapperPattern.matcher(text);
            while (wm.find()) {
                String toolName = wm.group(1);
                String argsJson = cleanArgsJson(wm.group(2));
                results.add(buildRequest(toolName, argsJson, idCounter++));
            }
        }

        // ── 策略 4: 宽松匹配（只看 name 行附近） ──
        // Qwen 有时会在注释后输出 JSON
        if (results.isEmpty()) {
            Pattern loosePattern = Pattern.compile(
                "\"name\"\\s*:\\s*\"(\\w+)\"[^{]*\"arguments\"\\s*:\\s*(\\{(?:[^{}]|\\{[^{}]*\\})*\\})",
                Pattern.DOTALL);
            Matcher lm = loosePattern.matcher(text);
            while (lm.find()) {
                String toolName = lm.group(1);
                // 验证是已知工具名（过滤误匹配）
                if (toolName.matches("[A-Z][a-zA-Z]+")) {
                    String argsJson = cleanArgsJson(lm.group(2));
                    results.add(buildRequest(toolName, argsJson, idCounter++));
                }
            }
        }

        return results;
    }

    private static ToolExecutionRequest buildRequest(String toolName, String argsJson, int id) {
        String idStr = "qwen-" + id;
        if (argsJson != null && !argsJson.isEmpty() && !argsJson.startsWith("{")) {
            argsJson = "{}";
        }
        return ToolExecutionRequest.builder()
            .id(idStr)
            .name(toolName)
            .arguments(argsJson != null ? argsJson : "{}")
            .build();
    }

    private static String cleanArgsJson(String json) {
        if (json == null) return "{}";
        json = json.trim();
        // 移除尾部逗号
        if (json.endsWith(",")) json = json.substring(0, json.length() - 1).trim();
        // 确保是有效 JSON 对象
        if (!json.startsWith("{")) json = "{" + json;
        if (!json.endsWith("}")) json = json + "}";
        return json;
    }

    /**
     * 执行工具请求列表（含编排：只读并发，写串行）。
     */
    private void executeToolRequests(List<ToolExecutionRequest> requests) {
        // 分组：只读工具并发，写工具串行
        List<ToolExecutionRequest> readOnlyRequests = new ArrayList<>();
        List<ToolExecutionRequest> writeRequests = new ArrayList<>();

        for (ToolExecutionRequest req : requests) {
            Tool<?, ?> tool = toolRegistry.getTool(req.name());
            if (tool != null && tool.isReadOnly()) {
                readOnlyRequests.add(req);
            } else {
                writeRequests.add(req);
            }
        }

        // ── 只读工具（并发） ──
        if (!readOnlyRequests.isEmpty()) {
            Map<String, ToolResult> readResults = executeConcurrently(readOnlyRequests);
            for (ToolExecutionRequest req : readOnlyRequests) {
                ToolResult result = readResults.get(req.id());
                context.recordToolCall();
                String feedback = result.success()
                    ? result.toMessageContent()
                    : buildFailureFeedback(req, result);
                messages.add(new ToolExecutionResultMessage(
                    req.id(), req.name(), feedback));
            }
        }

        // ── 写工具（串行） ──
        for (ToolExecutionRequest req : writeRequests) {
            ToolResult result = executeTool(req);
            context.recordToolCall();
            String feedback = result.success()
                ? result.toMessageContent()
                : buildFailureFeedback(req, result);
            messages.add(new ToolExecutionResultMessage(
                req.id(), req.name(), feedback));
        }
    }

    /**
     * 构建工具调用失败的结构化反馈信息。
     * 帮助 LLM 理解失败原因并修正下一次调用。
     */
    private String buildFailureFeedback(ToolExecutionRequest req, ToolResult result) {
        Tool<?, ?> tool = toolRegistry.getTool(req.name());
        StringBuilder fb = new StringBuilder();
        fb.append("[工具调用失败]\n");
        fb.append("工具: ").append(req.name()).append("\n");

        String errMsg = result.errorMsg();
        fb.append("错误: ").append(errMsg).append("\n");

        // ── 分析失败原因并给出针对性建议 ──
        if (tool == null) {
            fb.append("\n原因: 工具 '" + req.name() + "' 不存在。\n");
            fb.append("建议: 请使用当前可用的工具。使用 ToolSearch 查看可用工具列表。\n");
        } else if (errMsg.contains("解析输入失败") || errMsg.contains("JSON") || errMsg.contains("parse")) {
            fb.append("\n原因: 参数格式不正确，JSON 解析失败。\n");
            fb.append("建议: 请按照以下 schema 重新提供参数:\n");
            fb.append("```json\n");
            fb.append(tool.inputSchema());
            fb.append("\n```\n");
            fb.append("输入参数: ").append(req.arguments()).append("\n");
        } else if (errMsg.contains("未知") || errMsg.contains("不存在") || errMsg.contains("not found")) {
            fb.append("\n原因: 提供的参数值无效或资源不存在。\n");
            fb.append("建议: 请检查参数值是否正确，例如文件名、路径、标识符等。\n");
        } else if (errMsg.contains("超时") || errMsg.contains("timeout")) {
            fb.append("\n原因: 工具执行超时。\n");
            fb.append("建议: 请减少操作范围或使用更简洁的查询重新尝试。\n");
        } else if (errMsg.contains("hard_deny") || errMsg.contains("安全")) {
            fb.append("\n原因: 该操作被安全规则拦截。\n");
            fb.append("建议: 请使用安全的替代方案，不要执行被禁止的危险命令。\n");
        } else {
            fb.append("\n原因: 未知内部错误。\n");
            fb.append("建议: 请尝试使用不同的参数或操作重新调用此工具。\n");
        }

        fb.append("\n请修正后重新尝试。");
        return fb.toString();
    }

    /**
     * 裁剪消息历史。
     * 使用 ContextManager 基于 Token 数的智能策略：
     * - Token 使用率 &gt; 65%：从最早的消息组开始裁剪，直到 Token 数 ≤ 窗口的 50%
     * - Token 使用率 &gt; 80%：调用 LLM 将最早的消息压缩为摘要，回退到裁剪
     */
    private void trimHistory() {
        if (messages.size() <= 1) return;

        ChatLanguageModel model = modelSupplier.get();
        String modelName = context.getCurrentModel();

        if (model == null) {
            // 模型不可用时使用简单的消息数裁剪
            trimByCount();
            return;
        }

        List<ChatMessage> optimized = contextManager.checkAndOptimize(
            messages, modelName, model);

        // 更新原列表
        if (optimized != messages) {
            messages.clear();
            messages.addAll(optimized);
        }
    }

    /**
     * 基于消息数量的简单裁剪（模型不可用时降级方案）。
     */
    private void trimByCount() {
        int maxCount = 50;
        while (messages.size() > maxCount) {
            for (int i = 1; i < messages.size(); i++) {
                if (messages.get(i) instanceof UserMessage) {
                    int removeEnd = Math.min(i + 3, messages.size());
                    messages.subList(i, removeEnd).clear();
                    break;
                }
            }
        }
    }

    /**
     * 判断文本是否可能包含未正确解析的工具调用。
     * 特征：包含 "name" 键、花括号、或工具相关的关键字。
     */
    public static boolean looksLikeToolCall(String text) {
        if (text == null || text.isBlank()) return false;
        int len = text.length();
        // 太长的文本不太可能是单一工具调用
        if (len > 3000) return false;

        String lower = text.toLowerCase();
        // 检查是否包含 JSON 特征
        boolean hasJsonHint = text.contains("{") && (text.contains("\"name\"") || text.contains("'name'"));
        if (hasJsonHint) return true;

        // 检查是否提到工具调用相关词但没给 JSON
        boolean mentionsTool = lower.contains("tool") || lower.contains("function")
            || lower.contains("调用") || lower.contains("使用工具");
        if (mentionsTool && len < 500) return true;

        return false;
    }

    public List<ChatMessage> getMessageHistory() {
        return List.copyOf(messages);
    }

    public record AgentResponse(
        String text,
        int inputTokens,
        int outputTokens,
        int totalToolCalls
    ) {
        public AgentResponse(String text) {
            this(text, 0, 0, 0);
        }
    }
}
