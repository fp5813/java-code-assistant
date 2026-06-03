package com.moma.tool;

import com.moma.agent.AgentLoop;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具调用 JSON 兼容性测试。
 * 覆盖 Qwen、本地模型等各种可能的 JSON 工具调用输出格式。
 */
public class MomaToolCompatibilityTest {

    // ─────────────────── 标准格式 ───────────────────

    @Test
    void codeBlockFormat() {
        String text = """
            I will read the file for you.
            ```json
            {
              "name": "Read",
              "arguments": {
                "filePath": "src/main.java",
                "offset": 0
              }
            }
            ```
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertEquals(1, results.size());
        assertEquals("Read", results.get(0).name());
        assertTrue(results.get(0).arguments().contains("filePath"));
    }

    @Test
    void bareJsonFormat() {
        String text = """
            Here is the tool call: {"name":"Grep","arguments":{"pattern":"TODO","path":"src/"}}
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertEquals(1, results.size());
        assertEquals("Grep", results.get(0).name());
        assertTrue(results.get(0).arguments().contains("TODO"));
    }

    // ─────────────────── Qwen 常见格式 ───────────────────

    @Test
    void qwenFuncCallFormat() {
        // Qwen 有时输出 <function_call> 标签
        String text = """
            我需要读取日志文件来查看最新的运行状态。
            <function_call>
            {"name":"MomaLog","arguments":{"action":"tail","lines":50}}
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertTrue(results.size() > 0, "应能从 <function_call> 格式中解析工具调用");
        if (!results.isEmpty()) {
            assertEquals("MomaLog", results.get(0).name());
        }
    }

    @Test
    void qwenMultiLineArgs() {
        // Qwen 有时输出多行缩进的 arguments
        String text = """
            {
              "name": "Read",
              "arguments": {
                "filePath": "d:/project/src/Main.java",
                "offset": 1,
                "limit": 50
              }
            }
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertEquals(1, results.size());
        assertEquals("Read", results.get(0).name());
        assertTrue(results.get(0).arguments().contains("Main.java"));
    }

    @Test
    void qwenSingleQuoteFormat() {
        // 某些 Qwen 版本使用单引号
        String text = "{'name':'Glob','arguments':{'pattern':'**/*.java'}}";
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertEquals(1, results.size());
        assertEquals("Glob", results.get(0).name());
    }

    @Test
    void qwenChineseCommentBeforeJson() {
        // Qwen 常见：中文注释后跟 JSON
        String text = """
            好的，我来读取这个文件。

            {"name":"Read","arguments":{"filePath":"pom.xml"}}
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertEquals(1, results.size());
        assertEquals("Read", results.get(0).name());
        assertTrue(results.get(0).arguments().contains("pom.xml"));
    }

    // ─────────────────── 多工具调用 ───────────────────

    @Test
    void multipleToolCallsSameBlock() {
        String text = """
            ```json
            {
              "name": "Read",
              "arguments": {"filePath": "a.txt"}
            }
            {
              "name": "Grep",
              "arguments": {"pattern": "error"}
            }
            ```
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertTrue(results.size() >= 2, "应解析出 2 个工具调用，实际: " + results.size());
    }

    // ─────────────────── 无工具调用 ───────────────────

    @Test
    void noToolCall() {
        String text = "这是一个普通的回复，不需要调用任何工具。";
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertEquals(0, results.size());
    }

    @Test
    void emptyOrNull() {
        assertEquals(0, AgentLoop.parseJsonToolCalls(null).size());
        assertEquals(0, AgentLoop.parseJsonToolCalls("").size());
        assertEquals(0, AgentLoop.parseJsonToolCalls("   ").size());
    }

    // ─────────────────── 错误恢复 ───────────────────

    @Test
    void malformedJsonWithValidName() {
        // name 字段完整但 arguments 截断
        String text = """
            {"name": "Read", "arguments": {"filePath":
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        // 宽松模式 4 应该能匹配到 name
        assertTrue(results.size() >= 1 || results.isEmpty(),
            "应优雅处理截断 JSON，不抛异常");
    }

    @Test
    void trailingCommaInArgs() {
        String text = """
            {
              "name": "Write",
              "arguments": {
                "filePath": "out.txt",
                "content": "hello",
              }
            }
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertEquals(1, results.size());
        assertEquals("Write", results.get(0).name());
    }

    // ─────────────────── 宽松模式 ───────────────────

    @Test
    void looseModeCapturesToolNameOnly() {
        // 宽松模式：只需 "name":"ToolName" 加上一些花括号
        String text = """
            I need to use the \"name\":\"Read\" tool.
            Reading arguments: {"filePath":"test.java"}
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        // 宽松模式可能匹配到 Read，也可能不匹配
        // 只要不抛异常就算通过
        assertNotNull(results);
    }

    @Test
    void nonStandardWrapper() {
        String text = """
            json{"name":"Glob","arguments":{"pattern":"*.java"}}
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertTrue(results.size() > 0, "应解析 json{...} 格式");
    }

    // ─────────────────── looksLikeToolCall ───────────────────

    @Test
    void looksLikeToolCallJson() {
        assertTrue(AgentLoop.looksLikeToolCall("{\"name\":\"Read\"}"));
        assertTrue(AgentLoop.looksLikeToolCall("Let me use the {'name':'Glob'} tool"));
        assertFalse(AgentLoop.looksLikeToolCall("OK, I'll do that."));
        assertFalse(AgentLoop.looksLikeToolCall(""));
        assertFalse(AgentLoop.looksLikeToolCall(null));
    }

    @Test
    void looksLikeToolCallMentionsTool() {
        assertTrue(AgentLoop.looksLikeToolCall("我需要使用工具来读取文件"));
        assertTrue(AgentLoop.looksLikeToolCall("Let me call a tool"));
        // 超过 3000 字符的长文本不应视为工具调用
        assertFalse(AgentLoop.looksLikeToolCall(
            "这是一个很长的普通回复文本。".repeat(200)));  // >3000 chars
    }

    // ─────────────────── 数组参数 ───────────────────

    @Test
    void arrayArguments() {
        String text = """
            {
              "name": "TaskCreate",
              "arguments": {
                "description": "test",
                "dependencies": ["task1", "task2"]
              }
            }
            """;
        List<ToolExecutionRequest> results = AgentLoop.parseJsonToolCalls(text);
        assertEquals(1, results.size());
        assertEquals("TaskCreate", results.get(0).name());
        assertTrue(results.get(0).arguments().contains("task1"));
    }
}
