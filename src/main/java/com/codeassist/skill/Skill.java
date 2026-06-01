package com.codeassist.skill;

import java.util.List;

/**
 * 技能定义。
 * 技能 = 提示词模板 + 约束的工具集。
 * 对应 MiniClaude 的 skill 系统。
 *
 * @param name        技能名称（LLM 可见）
 * @param description 技能描述
 * @param prompt      附加的系统提示词（指导 Agent 行为模式）
 * @param allowedTools 允许使用的工具列表（空 = 不限）
 */
public record Skill(
    String name,
    String description,
    String prompt,
    List<String> allowedTools
) {}
