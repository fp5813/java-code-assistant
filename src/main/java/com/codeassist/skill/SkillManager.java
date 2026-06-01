package com.codeassist.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 技能管理器。
 * 管理内置技能和自定义技能的注册和加载。
 */
public class SkillManager {

    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public SkillManager() {
        registerBuiltinSkills();
    }

    /** 注册技能 */
    public void register(Skill skill) {
        skills.put(skill.name(), skill);
    }

    /** 按名称查找技能 */
    public Optional<Skill> getSkill(String name) {
        return Optional.ofNullable(skills.get(name));
    }

    /** 获取所有技能 */
    public List<Skill> getAllSkills() {
        return List.copyOf(skills.values());
    }

    /** 注册内置技能 */
    private void registerBuiltinSkills() {
        register(new Skill("code-review",
            "审查代码质量、安全性和最佳实践",
            """
            ## 技能: 代码审查
            你正在进行代码审查。请关注:
            1. 正确性 — 逻辑是否正确，边界情况是否处理
            2. 安全性 — 是否存在安全漏洞（注入、XSS、权限等）
            3. 性能 — 是否有明显的性能问题
            4. 可维护性 — 命名、注释、代码结构是否清晰
            5. 规范 — 是否遵循编码规范和架构约定
            
            对于每个问题，明确指出: 文件 + 行号 + 问题说明 + 改进建议。
            """,
            List.of("Read", "Glob", "Grep", "GitDiff", "GitStatus")));

        register(new Skill("test-generation",
            "为代码生成单元测试",
            """
            ## 技能: 测试生成
            你的任务是为指定代码生成全面的单元测试。
            遵循的原则:
            1. 覆盖正常路径和边界情况
            2. 包含异常处理测试
            3. 使用 Mock 隔离外部依赖
            4. 测试命名清晰: should_xxx_when_xxx
            5. 每个测试只测一个关注点
            """,
            List.of("Read", "Glob", "Write", "Edit")));

        register(new Skill("refactoring",
            "代码重构：优化结构而不改变外部行为",
            """
            ## 技能: 代码重构
            你正在进行代码重构。请遵循:
            1. 不改变外部行为（保持接口兼容）
            2. 小步提交，每次重构后运行测试验证
            3. 优先使用工具支持的重构（重命名、提取方法等）
            4. 重构后更新相关文档和注释
            
            每步重构请先说明目标，再执行修改。
            """,
            List.of("Read", "Write", "Edit", "Glob", "Grep", "Bash", "GitDiff", "GitStatus", "GitCommit")));

        register(new Skill("bug-fix",
            "定位并修复代码缺陷",
            """
            ## 技能: Bug 修复
            你在进行 Bug 排查和修复。请遵循:
            1. 先理解问题现象和复现步骤
            2. 用最小复现定位根因
            3. 修复前评估影响范围
            4. 修复后防止同类问题再次出现
            """,
            List.of("Read", "Glob", "Grep", "Edit", "GitDiff", "GitStatus")));
    }
}
