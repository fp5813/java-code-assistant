package com.moma.tool;

import com.moma.agent.AgentContext;
import com.moma.config.DiConfig;
import com.moma.controller.MomaDevController;
import com.moma.di.ApplicationContext;
import com.moma.learning.PatternLearner;
import com.moma.memory.MemoryStore;
import com.moma.skill.SkillManager;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证新增的 5 个工具 + MomaDevController + moma-dev 技能是否正常注册。
 */
public class MomaNewToolsTest {

    private ApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new ApplicationContext();
        Map<String, String> props = new HashMap<>();
        props.put("api.timeout.seconds", "300");
        props.put("api.max.retries", "3");
        ctx.setPropertySource(props);
        ctx.register(DiConfig.class);
        ctx.refresh();
    }

    // ─────────────────── 工具注册验证 ───────────────────

    @Test
    void allFiveNewToolsRegistered() {
        ToolRegistry registry = ctx.getBean(ToolRegistry.class);
        assertNotNull(registry, "ToolRegistry 不应为 null");

        assertNotNull(registry.getTool("MomaLog"), "MomaLog 工具未注册");
        assertNotNull(registry.getTool("MomaMonitor"), "MomaMonitor 工具未注册");
        assertNotNull(registry.getTool("SaveExperience"), "SaveExperience 工具未注册");
        assertNotNull(registry.getTool("PatternLearn"), "PatternLearn 工具未注册");
        assertNotNull(registry.getTool("KnowledgeSearch"), "KnowledgeSearch 工具未注册");

        assertTrue(registry.size() >= 28, "工具总数应 ≥ 28，实际: " + registry.size());
    }

    @Test
    void newToolsAreReadOnly() {
        ToolRegistry registry = ctx.getBean(ToolRegistry.class);

        assertTrue(registry.getTool("MomaLog").isReadOnly(), "MomaLog 应该是只读工具");
        assertTrue(registry.getTool("MomaMonitor").isReadOnly(), "MomaMonitor 应该是只读工具");
        assertTrue(registry.getTool("PatternLearn").isReadOnly(), "PatternLearn 应该是只读工具");
        assertTrue(registry.getTool("KnowledgeSearch").isReadOnly(), "KnowledgeSearch 应该是只读工具");
        assertFalse(registry.getTool("SaveExperience").isReadOnly(), "SaveExperience 应该是写工具");
    }

    // ─────────────────── 技能系统验证 ───────────────────

    @Test
    void momaDevSkillRegistered() {
        SkillManager skillManager = ctx.getBean(SkillManager.class);
        assertNotNull(skillManager, "SkillManager 不应为 null");

        var skills = skillManager.getAllSkills();
        assertEquals(5, skills.size(), "应该有 5 个技能（含 moma-dev）");

        var momaDevOpt = skillManager.getSkill("moma-dev");
        assertTrue(momaDevOpt.isPresent(), "moma-dev 技能未注册");

        var momaDev = momaDevOpt.get();
        assertEquals("moma-dev", momaDev.name());
        assertTrue(momaDev.description().contains("墨码"));
        assertTrue(momaDev.prompt().contains("Tool<I,O>"));
        assertTrue(momaDev.allowedTools().contains("MomaLog"));
        assertTrue(momaDev.allowedTools().contains("KnowledgeSearch"));
    }

    // ─────────────────── 日志工具测试 ───────────────────

    @Test
    void momaLogToolHandlesMissingLogFile() throws ToolException {
        MomaLogTool tool = new MomaLogTool();
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");
        String result = tool.execute(new MomaLogTool.Input("tail", null, 10), context);
        assertNotNull(result);
    }

    @Test
    void momaLogToolErrorsAction() throws ToolException {
        MomaLogTool tool = new MomaLogTool();
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");
        String result = tool.execute(new MomaLogTool.Input("errors", null, null), context);
        assertNotNull(result);
    }

    // ─────────────────── 监控工具测试 ───────────────────

    @Test
    void momaMonitorToolSummary() throws ToolException {
        MomaMonitorTool tool = new MomaMonitorTool();
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");
        context.recordTokens(100, 50);
        context.recordToolCall();
        context.recordToolCall();

        String result = tool.execute(new MomaMonitorTool.Input("summary"), context);
        assertNotNull(result);
        assertTrue(result.contains("MoMa"));
        assertTrue(result.contains("100"));
        assertTrue(result.contains("2"));
    }

    @Test
    void momaMonitorToolTokens() throws ToolException {
        MomaMonitorTool tool = new MomaMonitorTool();
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");
        context.recordTokens(500, 300);

        String result = tool.execute(new MomaMonitorTool.Input("tokens"), context);
        assertNotNull(result);
        assertTrue(result.contains("500"));
        assertTrue(result.contains("300"));
    }

    @Test
    void momaMonitorToolJvm() throws ToolException {
        MomaMonitorTool tool = new MomaMonitorTool();
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");

        String result = tool.execute(new MomaMonitorTool.Input("jvm"), context);
        assertNotNull(result);
        assertTrue(result.contains("JVM"));
        assertTrue(result.contains("堆内存"));
        assertTrue(result.contains("线程"));
    }

    // ─────────────────── 经验保存工具测试 ───────────────────

    @Test
    void saveExperienceToolSavesToMemory() throws ToolException {
        MemoryStore memoryStore = ctx.getBean(MemoryStore.class);
        SaveExperienceTool tool = new SaveExperienceTool(memoryStore);
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");

        String result = tool.execute(new SaveExperienceTool.Input(
            "bugfix", "修复 NPE 异常", "在 AgentLoop.java:123 修复了 null 检查", "总是检查返回值是否为 null"
        ), context);
        assertNotNull(result);
        assertTrue(result.contains("已保存"));

        var results = memoryStore.search("moma", null, "NPE", 10);
        assertTrue(results.size() > 0, "应能搜索到刚保存的经验");
    }

    @Test
    void saveExperienceToolRejectsEmptyTitle() {
        MemoryStore memoryStore = ctx.getBean(MemoryStore.class);
        SaveExperienceTool tool = new SaveExperienceTool(memoryStore);
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");

        assertThrows(ToolException.class, () -> {
            tool.execute(new SaveExperienceTool.Input("bugfix", "", "detail", "lesson"), context);
        }, "空标题应抛出异常");
    }

    // ─────────────────── 知识库测试 ───────────────────

    @Test
    void knowledgeSearchToolFindsClaudeCode() throws ToolException {
        KnowledgeBaseTool tool = new KnowledgeBaseTool();
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");

        String result = tool.execute(new KnowledgeBaseTool.Input("claude", "all", "search", null), context);
        assertNotNull(result);
        assertTrue(result.contains("Claude Code") || result.contains("claude"),
            "应能搜索到 Claude Code: " + result);
    }

    @Test
    void knowledgeSearchToolFindsPattern() throws ToolException {
        KnowledgeBaseTool tool = new KnowledgeBaseTool();
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");

        String result = tool.execute(new KnowledgeBaseTool.Input("context management", "pattern", "search", null), context);
        assertNotNull(result);
        assertTrue(result.contains("context"), "应搜索到 context: " + result);
    }

    @Test
    void knowledgeSearchToolNoMatch() throws ToolException {
        KnowledgeBaseTool tool = new KnowledgeBaseTool();
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");

        String result = tool.execute(new KnowledgeBaseTool.Input("xyz_not_exist_123", "all", "search", null), context);
        assertNotNull(result);
        assertTrue(result.contains("未找到") || result.contains("关键词"), "无匹配应有提示: " + result);
    }

    // ─────────────────── 命令控制器验证 ───────────────────

    @Test
    void momaDevControllerRegistered() {
        @SuppressWarnings("unchecked")
        List<Object> controllers = ctx.getBean(List.class);
        assertNotNull(controllers);

        boolean found = controllers.stream().anyMatch(c -> c instanceof MomaDevController);
        assertTrue(found, "MomaDevController 应该被注册到控制器列表");
    }

    // ─────────────────── PatternLearner 验证 ───────────────────

    @Test
    void patternLearnerCodebase() {
        PatternLearner learner = ctx.getBean(PatternLearner.class);
        assertNotNull(learner, "PatternLearner Bean 不应该为 null");

        String result = learner.learnFromCodebase();
        assertNotNull(result);
        assertTrue(result.contains("包结构"), "应包含包结构信息: " + result);
    }

    // ─────────────────── 活跃技能功能验证 ───────────────────

    @Test
    void activeSkillStateManagement() {
        AgentContext context = new AgentContext(System.getProperty("user.dir"), null, "test");
        assertNull(context.getActiveSkill(), "初始状态 activeSkill 应为 null");

        context.setActiveSkill("moma-dev");
        assertEquals("moma-dev", context.getActiveSkill());

        context.setActiveSkill(null);
        assertNull(context.getActiveSkill());
    }
}
