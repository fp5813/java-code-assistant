package com.moma.context;

import com.moma.agent.AgentContext;
import com.moma.tool.ToolRegistry;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 上下文窗口管理集成测试。
 * 验证 ContextWindowRegistry 的模型匹配、ContextManager 的 Token 估算、
 * 基于 Token 数的消息裁剪和摘要压缩回退逻辑。
 */
class ContextManagerTest {

    private ContextWindowRegistry registry;
    private ToolRegistry toolRegistry;
    private ContextManager contextManager;

    /** 测试用小型上下文窗口（4K），确保裁剪/压缩可被触发 */
    private static final String TEST_MODEL = "test-model";
    private static final int TEST_CONTEXT_WINDOW = 4096;

    @BeforeEach
    void setUp() {
        registry = new ContextWindowRegistry();
        // 注册一个测试用的小窗口模型，方便触发裁剪
        registry.register(TEST_MODEL, TEST_CONTEXT_WINDOW);
        toolRegistry = new ToolRegistry();
        contextManager = new ContextManager(registry, toolRegistry);
    }

    // ──────────────────────────────────────────────
    // ContextWindowRegistry 模型匹配测试
    // ──────────────────────────────────────────────

    @Test
    void testRegistryExactMatch() {
        assertEquals(32768, registry.getContextWindow("qwen2.5-coder:7b"));
    }

    @Test
    void testRegistryWildcardMatch() {
        assertEquals(32768, registry.getContextWindow("qwen2.5-coder:latest"));
        assertEquals(32768, registry.getContextWindow("qwen2.5-coder:14b"));
    }

    @Test
    void testRegistryCloudModel() {
        assertEquals(128000, registry.getContextWindow("gpt-4o"));
        assertEquals(128000, registry.getContextWindow("gpt-4o-mini"));
        assertEquals(65536, registry.getContextWindow("deepseek-chat"));
        assertEquals(200000, registry.getContextWindow("claude-sonnet-4-20250514"));
    }

    @Test
    void testRegistryDefaultForUnknown() {
        assertEquals(8192, registry.getContextWindow("unknown-model-123"));
    }

    @Test
    void testRegistryNullSafe() {
        assertEquals(8192, registry.getContextWindow(null));
        assertEquals(8192, registry.getContextWindow(""));
    }

    @Test
    void testSafeMaxTokens() {
        // 使用注册的测试模型验证
        int testWindow = registry.getContextWindow(TEST_MODEL);
        int testSafeMax = registry.getSafeMaxTokens(TEST_MODEL);
        assertEquals(TEST_CONTEXT_WINDOW, testWindow);
        assertEquals((int) (TEST_CONTEXT_WINDOW * 0.85), testSafeMax);
    }

    // ──────────────────────────────────────────────
    // Token 估算测试
    // ──────────────────────────────────────────────

    @Test
    void testEstimateTokensEmptyList() {
        assertEquals(0, contextManager.estimateTokens(List.of(), TEST_MODEL));
    }

    @Test
    void testEstimateTokensSingleMessage() {
        List<ChatMessage> messages = List.of(
            new SystemMessage("你好，世界")
        );
        int tokens = contextManager.estimateTokens(messages, TEST_MODEL);
        assertTrue(tokens > 0, "Token 估算应返回正值");
    }

    @Test
    void testEstimateTokensMultipleMessages() {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("你是一个 AI 编程助手"));
        for (int i = 0; i < 5; i++) {
            messages.add(new UserMessage("用户问题 " + i));
            messages.add(new AiMessage("AI 回答 " + i));
        }
        int tokens = contextManager.estimateTokens(messages, TEST_MODEL);
        assertTrue(tokens > 0, "多条消息的 Token 估算应返回正值");
    }

    @Test
    void testGetContextWindow() {
        int window = contextManager.getContextWindow(TEST_MODEL);
        assertEquals(TEST_CONTEXT_WINDOW, window);
    }

    @Test
    void testGetUsageRatioLow() {
        List<ChatMessage> messages = List.of(
            new SystemMessage("简短消息")
        );
        double ratio = contextManager.getUsageRatio(messages, TEST_MODEL);
        assertTrue(ratio < 0.01, "短消息的使用率应接近 0");
    }

    @Test
    void testGetUsageRatioHigh() {
        // 创建足够多的消息来达到高使用率
        List<ChatMessage> messages = createLongConversation(40);
        double ratio = contextManager.getUsageRatio(messages, TEST_MODEL);
        assertTrue(ratio > 0.5, "长对话的使用率应超过 50%");
    }

    // ──────────────────────────────────────────────
    // 消息裁剪测试
    // ──────────────────────────────────────────────

    @Test
    void testTrimMessagesSmallList() {
        // 消息很少时不裁剪
        List<ChatMessage> messages = new ArrayList<>(List.of(
            new SystemMessage("系统提示词"),
            new UserMessage("你好")
        ));
        List<ChatMessage> result = contextManager.trimMessages(messages, TEST_MODEL);
        assertEquals(2, result.size(), "短消息列表不应被裁剪");
    }

    @Test
    void testTrimMessagesPreservesSystemPrompt() {
        // 创建足够消息触发裁剪（测试窗口 4K，65% ≈ 2.6K）
        List<ChatMessage> messages = createLongConversation(30);
        SystemMessage systemMsg = (SystemMessage) messages.get(0);
        String originalText = systemMsg.text();

        List<ChatMessage> result = contextManager.trimMessages(messages, TEST_MODEL);

        assertInstanceOf(SystemMessage.class, result.get(0), "第一条消息应为系统提示词");
        assertEquals(originalText, ((SystemMessage) result.get(0)).text(), "系统提示词内容应保持不变");
    }

    @Test
    void testTrimMessagesReducesCount() {
        // 创建长对话触发裁剪
        List<ChatMessage> messages = createLongConversation(40);

        int beforeCount = messages.size();
        List<ChatMessage> result = contextManager.trimMessages(messages, TEST_MODEL);

        assertTrue(result.size() < beforeCount, "裁剪后消息数应减少");
        assertTrue(result.size() >= 3, "裁剪后至少保留系统提示词 + 最近消息");
    }

    @Test
    void testTrimMessagesKeepsRecentMessages() {
        // 创建长对话，验证最近的消息被保留
        List<ChatMessage> messages = createLongConversation(40);

        // 记录最后几条消息的内容
        String lastUserMsg = ((UserMessage) messages.get(messages.size() - 2)).singleText();
        String lastAiMsg = ((AiMessage) messages.get(messages.size() - 1)).text();

        List<ChatMessage> result = contextManager.trimMessages(messages, TEST_MODEL);

        // 验证最后的消息组被保留
        boolean lastUserFound = false;
        boolean lastAiFound = false;
        for (ChatMessage msg : result) {
            if (msg instanceof UserMessage um && lastUserMsg.equals(um.singleText())) {
                lastUserFound = true;
            }
            if (msg instanceof AiMessage am && lastAiMsg.equals(am.text())) {
                lastAiFound = true;
            }
        }
        assertTrue(lastUserFound, "最近的用户消息应被保留");
        assertTrue(lastAiFound, "最近的 AI 回复应被保留");
    }

    // ──────────────────────────────────────────────
    // 摘要压缩回退测试
    // ──────────────────────────────────────────────

    @SuppressWarnings("DataFlowIssue")
    @Test
    void testCompressMessagesFallbackToTrimWhenModelNull() {
        // 当模型为 null 时，compressMessages 应回退到 trim
        List<ChatMessage> messages = createLongConversation(40);

        List<ChatMessage> result = contextManager.compressMessages(messages, null, TEST_MODEL);

        // 回退到 trim 后消息数应减少
        assertTrue(result.size() < messages.size(),
            "模型为 null 时 compressMessages 应回退到裁剪");
    }

    // ──────────────────────────────────────────────
    // 集成测试：checkAndOptimize
    // ──────────────────────────────────────────────

    @Test
    void testCheckAndOptimizeSmallConversation() {
        // 短对话不应该触发裁剪或压缩
        List<ChatMessage> messages = new ArrayList<>(List.of(
            new SystemMessage("系统提示词"),
            new UserMessage("你好"),
            new AiMessage("你好！有什么可以帮助你的吗？")
        ));

        // checkAndOptimize 需要非 null 的 model，短对话直接走 ratio < 65% 路径
        List<ChatMessage> result = contextManager.checkAndOptimize(messages, TEST_MODEL, null);

        assertEquals(messages.size(), result.size(), "短对话不应触发裁剪");
    }

    @Test
    void testCheckAndOptimizeLongConversationTriggersTrim() {
        // 长对话应触发裁剪（即使 model 为 null，compress 回退到 trim）
        List<ChatMessage> messages = createLongConversation(40);

        List<ChatMessage> result = contextManager.checkAndOptimize(messages, TEST_MODEL, null);

        // 应成功回退到裁剪，消息数减少
        assertTrue(result.size() < messages.size(),
            "长对话应触发裁剪或回退后的 trim");
        assertTrue(result.size() >= 3,
            "裁剪后至少保留系统提示词 + 最近消息组");
    }

    @Test
    void testFullPipelineWithSmallContextWindow() {
        // 完整管道测试：400K 的小窗口验证裁剪触发
        ContextWindowRegistry smallRegistry = new ContextWindowRegistry();
        smallRegistry.register("tiny-model", 512);
        ContextManager tinyManager = new ContextManager(smallRegistry, toolRegistry);

        List<ChatMessage> messages = createLongConversation(20);
        int beforeCount = messages.size();

        List<ChatMessage> result = tinyManager.trimMessages(messages, "tiny-model");

        assertTrue(result.size() < beforeCount, "小窗口时裁剪应减少消息数");
        assertEquals(result.get(0), messages.get(0), "系统提示词应保持不变");
    }

    // ──────────────────────────────────────────────
    // 工具方法
    // ──────────────────────────────────────────────

    /**
     * 创建一轮对话消息。
     */
    private static ChatMessage[] createConversationRound(int index, String topic) {
        return new ChatMessage[]{
            new UserMessage("第 " + index + " 轮问题：请帮我修改 " + topic + " 文件的第 " + (index * 10) + " 行，"
                + "将变量名从 oldName 改为 newName，并确保所有引用同步更新。"
                + "同时检查相关的测试文件是否需要同步修改。"),
            new AiMessage("好的，我来修改 " + topic + " 文件。\n\n"
                + "1. 首先读取文件内容，定位第 " + (index * 10) + " 行\n"
                + "2. 使用 EditTool 修改变量名\n"
                + "3. 搜索所有引用该变量的位置\n"
                + "4. 同步修改相关文件\n\n"
                + "已完成修改，共更新了 " + (index % 3 + 1) + " 个文件。\n"
                + "修改内容概要：\n"
                + "- 变量名 oldName → newName\n"
                + "- 添加了空值检查\n"
                + "- 更新了对应的单元测试")
        };
    }

    /**
     * 创建指定轮数的长对话。
     */
    private static List<ChatMessage> createLongConversation(int rounds) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new SystemMessage("你是一个 AI 编程助手。"
            + "你可以使用各种工具来感知和操作代码库。"
            + "工具包括 ReadTool、WriteTool、EditTool、GlobTool、GrepTool、BashTool 等。"
            + "只读工具可并发执行，写工具需串行执行。"
            + "当前工作目录: /test/project"));

        String[] topics = {"UserService.java", "OrderController.java",
            "PaymentGateway.java", "AuthFilter.java", "DataRepository.java"};

        for (int i = 0; i < rounds; i++) {
            String topic = topics[i % topics.length];
            ChatMessage[] round = createConversationRound(i, topic);
            messages.add(round[0]); // UserMessage
            messages.add(round[1]); // AiMessage
        }

        return messages;
    }
}
