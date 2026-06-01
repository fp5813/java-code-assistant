package com.moma.tool;

import com.moma.agent.AgentContext;

/**
 * 工具接口。
 * 对应 MiniClaude 的 {@code buildTool()} 模式。
 * 每个工具封装一个原子操作（读文件、执行命令等），
 * 在 Agent 的 perceive-think-act 循环中被 LLM 调用。
 *
 * @param <I> 输入类型
 * @param <O> 输出类型
 */
public interface Tool<I, O> {

    /** 工具名称（唯一标识，LLM 通过此名称调用） */
    String name();

    /** 工具描述（LLM 理解工具用途） */
    String description();

    /** 输入参数的 JSON Schema（描述字段名、类型、约束） */
    String inputSchema();

    /** 执行工具逻辑 */
    O execute(I input, AgentContext context) throws ToolException;

    /** 工具是否为只读（安全相关：只读工具可并发执行） */
    boolean isReadOnly();

    /** 工具是否启用 */
    default boolean isEnabled() { return true; }

    /** 将输入 JSON 字符串解析为输入对象 */
    I parseInput(String jsonInput) throws ToolException;

    /** 将输出对象序列化为 JSON 字符串（供 LLM 读取） */
    String formatOutput(O output);
}
