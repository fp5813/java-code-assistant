package com.moma.controller;

import com.moma.cli.CommandParser;
import com.moma.learning.PatternLearner;
import com.moma.memory.MemoryStore;
import com.moma.skill.SkillManager;

import java.util.Map;

/**
 * 墨码开发命令控制器。
 * 技能管理和自我学习命令。
 */
public class MomaDevController extends CommandController {

    private final SkillManager skillManager;
    private final PatternLearner patternLearner;
    private final MemoryStore memoryStore;

    public MomaDevController(SkillManager skillManager, PatternLearner patternLearner,
                              MemoryStore memoryStore) {
        super("moma-dev");
        this.skillManager = skillManager;
        this.patternLearner = patternLearner;
        this.memoryStore = memoryStore;
    }

    @Override
    public void registerHandlers(Map<String, CommandParser.CommandHandler> handlers) {
        // ── /skills 命令 ──
        handlers.put("skills", args -> {
            if (args != null && !args.isBlank()) {
                // 激活技能: /skills <name>
                var skillOpt = skillManager.getSkill(args.trim());
                if (skillOpt.isEmpty()) {
                    return new CommandParser.CommandResult(true,
                        "未知技能: " + args.trim() + "。使用 /skills (不带参数) 查看可用技能。", null);
                }
                var skill = skillOpt.get();
                return new CommandParser.CommandResult(true,
                    "✅ 技能已就绪: " + skill.name() + "\n"
                    + skill.description() + "\n\n"
                    + skill.prompt(), null);
            }
            // 列出所有技能
            var skills = skillManager.getAllSkills();
            if (skills.isEmpty()) {
                return new CommandParser.CommandResult(true, "  (无可用技能)", null);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("可用技能:\n");
            for (var skill : skills) {
                sb.append(String.format("  %-20s %s\n", skill.name(), skill.description()));
            }
            sb.append("\n使用 /skills <name> 查看技能详情");
            return new CommandParser.CommandResult(true, sb.toString(), null);
        });

        // ── /learn 命令 ──
        handlers.put("learn", args -> {
            String report = patternLearner.summarize();
            return new CommandParser.CommandResult(true, report, null);
        });

        // ── /experience 命令 ──
        handlers.put("experience", args -> {
            String keyword = (args != null && !args.isBlank()) ? args.trim() : null;
            if (keyword == null) {
                return new CommandParser.CommandResult(true,
                    "用法: /experience <关键词>\n搜索已保存的项目开发经验。", null);
            }
            var results = memoryStore.search("moma", null, keyword, 10);
            if (results.isEmpty()) {
                return new CommandParser.CommandResult(true,
                    "未找到与 '" + keyword + "' 相关的经验记忆。", null);
            }
            StringBuilder sb = new StringBuilder();
            sb.append("经验搜索: ").append(keyword).append("\n");
            sb.append("───────────────────────────────\n");
            for (var entry : results) {
                String content = entry.getContent();
                if (content.length() > 200) {
                    content = content.substring(0, 200) + "...";
                }
                sb.append("[").append(entry.getType()).append("] ").append(content).append("\n");
                sb.append("───────────────────────────────\n");
            }
            return new CommandParser.CommandResult(true, sb.toString(), null);
        });
    }
}
