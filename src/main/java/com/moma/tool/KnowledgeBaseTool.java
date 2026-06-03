package com.moma.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.moma.agent.AgentContext;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * 参考知识库搜索工具。查询 Claude Code、Hermes、OpenCode 等项目的架构知识。
 *
 * <p>支持操作：</p>
 * <ul>
 *   <li>{@code search} — 搜索知识（默认）</li>
 *   <li>{@code save} — 追加自定义知识条目到本地知识库</li>
 * </ul>
 */
public class KnowledgeBaseTool implements Tool<KnowledgeBaseTool.Input, String> {

    public static final String NAME = "KnowledgeSearch";
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_OUTPUT_CHARS = 4000;

    /** 懒加载的知识库 */
    private Map<String, Object> knowledgeCache;
    private Map<String, Object> customKnowledgeCache;

    /** 本地自定义知识库路径 */
    private static final String CUSTOM_KB_FILE = ".ca/knowledge/custom.json";

    public record Input(
        String query,     // 搜索关键词（action=search）
        String category,  // "project" / "pattern" / "all"
        String action,    // "search"(默认) / "save"
        String entryJson  // action=save 时的新知识条目 JSON
    ) {}

    @Override
    public String name() { return NAME; }

    @Override
    public String description() {
        return "搜索参考架构知识库或保存自定义知识。操作: search（默认，搜索知识）, save（追加自定义知识条目到本地知识库）。";
    }

    @Override
    public String inputSchema() {
        return """
        {
            "type": "object",
            "properties": {
                "action": {
                    "type": "string",
                    "enum": ["search", "save"],
                    "description": "操作: search（搜索知识）, save（保存自定义知识条目）"
                },
                "query": {
                    "type": "string",
                    "description": "搜索关键词（action=search 时必需）"
                },
                "category": {
                    "type": "string",
                    "enum": ["project", "pattern", "all"],
                    "description": "搜索范围: project（参考项目）, pattern（架构模式）, all（全部）"
                },
                "entryJson": {
                    "type": "string",
                    "description": "新知识条目的 JSON（action=save 时必需）。格式: {\\"projects\\": {...}} 或 {\\"patterns\\": {...}}"
                }
            },
            "required": ["action"]
        }
        """;
    }

    @Override
    public String execute(Input input, AgentContext context) throws ToolException {
        String action = input.action() != null ? input.action() : "search";

        if ("save".equals(action)) {
            return handleSave(input, context);
        }

        // 默认: search
        return handleSearch(input);
    }

    /**
     * 处理搜索操作。
     */
    private String handleSearch(Input input) throws ToolException {
        if (input.query() == null || input.query().isBlank()) {
            throw new ToolException("搜索关键词不能为空");
        }

        var knowledge = loadKnowledge();
        String category = input.category() != null ? input.category() : "all";
        String query = input.query().toLowerCase();

        StringBuilder sb = new StringBuilder();
        sb.append("=== 知识搜索: \"").append(input.query()).append("\" ===\n\n");

        int matchCount = 0;

        // 搜索项目知识
        if (("all".equals(category) || "project".equals(category))
            && knowledge.containsKey("projects")) {
            @SuppressWarnings("unchecked")
            var projects = (Map<String, Object>) knowledge.get("projects");
            for (var entry : projects.entrySet()) {
                if (matchesSearch(entry.getValue(), query)) {
                    matchCount++;
                    sb.append(formatProjectEntry(entry.getKey(), entry.getValue()));
                    if (sb.length() > MAX_OUTPUT_CHARS) break;
                }
            }
        }

        // 搜索模式知识
        if (("all".equals(category) || "pattern".equals(category))
            && knowledge.containsKey("patterns")) {
            @SuppressWarnings("unchecked")
            var patterns = (Map<String, Object>) knowledge.get("patterns");
            for (var entry : patterns.entrySet()) {
                if (matchesSearch(entry.getValue(), query)) {
                    matchCount++;
                    sb.append(formatPatternEntry(entry.getKey(), entry.getValue()));
                    if (sb.length() > MAX_OUTPUT_CHARS) break;
                }
            }
        }

        if (matchCount == 0) {
            sb.append("未找到与 \"").append(input.query()).append("\" 相关的知识。\n\n");
            sb.append("可以尝试以下关键词:\n");
            sb.append("- 参考项目: claude, hermes, opencode\n");
            sb.append("- 架构模式: tool_orchestration, context_management, di_container, self_learning\n");
        } else {
            sb.append("\n---\n共匹配 ").append(matchCount).append(" 条结果。");
        }

        return sb.toString();
    }

    /**
     * 处理保存操作 — 追加自定义知识条目到本地文件。
     */
    @SuppressWarnings("unchecked")
    private String handleSave(Input input, AgentContext context) throws ToolException {
        if (input.entryJson() == null || input.entryJson().isBlank()) {
            throw new ToolException("保存知识需要提供 entryJson 参数。\n格式: {\"projects\": {\"key\": {...}}} 或 {\"patterns\": {\"key\": {...}}}");
        }

        try {
            // 解析新知识
            Map<String, Object> newEntry = MAPPER.readValue(input.entryJson(), new TypeReference<>() {});
            if (newEntry.isEmpty()) {
                throw new ToolException("entryJson 不能为空对象");
            }

            // 加载已有自定义知识
            Map<String, Object> custom = loadCustomKnowledge();

            // 合并
            custom.putAll(newEntry);

            // 保存到文件
            saveCustomKnowledge(custom);

            // 清除缓存使下次搜索使用最新数据
            knowledgeCache = null;
            customKnowledgeCache = custom;

            return "✅ 知识条目已保存到本地知识库。\n保存的条目数: " + newEntry.size();
        } catch (ToolException e) {
            throw e;
        } catch (Exception e) {
            throw new ToolException("保存知识失败: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadKnowledge() throws ToolException {
        if (knowledgeCache != null) return knowledgeCache;

        try {
            InputStream is = getClass().getClassLoader()
                .getResourceAsStream("knowledge/references.json");
            if (is == null) {
                knowledgeCache = new LinkedHashMap<>();
            } else {
                knowledgeCache = MAPPER.readValue(is, new TypeReference<>() {});
            }

            // 合并自定义知识
            Map<String, Object> custom = loadCustomKnowledge();
            if (!custom.isEmpty()) {
                for (var entry : custom.entrySet()) {
                    if (knowledgeCache.containsKey(entry.getKey())) {
                        var existing = (Map<String, Object>) knowledgeCache.get(entry.getKey());
                        existing.putAll((Map<String, Object>) entry.getValue());
                    } else {
                        knowledgeCache.put(entry.getKey(), entry.getValue());
                    }
                }
            }

            return knowledgeCache;
        } catch (Exception e) {
            knowledgeCache = new LinkedHashMap<>();
            return knowledgeCache;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> loadCustomKnowledge() {
        if (customKnowledgeCache != null) return customKnowledgeCache;

        try {
            Path customPath = Paths.get(System.getProperty("user.home"), CUSTOM_KB_FILE);
            if (Files.exists(customPath)) {
                String json = Files.readString(customPath, StandardCharsets.UTF_8);
                customKnowledgeCache = MAPPER.readValue(json, new TypeReference<>() {});
            } else {
                customKnowledgeCache = new LinkedHashMap<>();
            }
        } catch (Exception e) {
            customKnowledgeCache = new LinkedHashMap<>();
        }
        return customKnowledgeCache;
    }

    private void saveCustomKnowledge(Map<String, Object> data) throws IOException {
        Path customPath = Paths.get(System.getProperty("user.home"), CUSTOM_KB_FILE);
        Files.createDirectories(customPath.getParent());
        String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(data);
        Files.writeString(customPath, json, StandardCharsets.UTF_8);
    }

    @SuppressWarnings("unchecked")
    private boolean matchesSearch(Object entry, String query) {
        if (!(entry instanceof Map)) return false;
        var map = (Map<String, Object>) entry;

        // 搜索 description, key_patterns, approaches, current, learned_from 等字段
        for (String key : map.keySet()) {
            Object value = map.get(key);
            if (value instanceof String s && s.toLowerCase().contains(query)) return true;
            if (value instanceof List list) {
                for (Object item : list) {
                    if (item instanceof String s && s.toLowerCase().contains(query)) return true;
                }
            }
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private String formatProjectEntry(String key, Object value) {
        if (!(value instanceof Map)) return "";
        var map = (Map<String, Object>) value;
        StringBuilder sb = new StringBuilder();

        sb.append("## 参考项目: ").append(map.getOrDefault("name", key)).append("\n\n");
        if (map.containsKey("description")) {
            sb.append(map.get("description")).append("\n\n");
        }

        // 关键模式
        if (map.containsKey("key_patterns")) {
            sb.append("**关键模式:**\n");
            for (Object p : (List<Object>) map.get("key_patterns")) {
                sb.append("- ").append(p).append("\n");
            }
            sb.append("\n");
        }

        // 借鉴之处
        if (map.containsKey("learned_from")) {
            sb.append("**已借鉴的设计:**\n");
            for (Object p : (List<Object>) map.get("learned_from")) {
                sb.append("- ").append(p).append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n\n");
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private String formatPatternEntry(String key, Object value) {
        if (!(value instanceof Map)) return "";
        var map = (Map<String, Object>) value;
        StringBuilder sb = new StringBuilder();

        sb.append("## 架构模式: ").append(key.replace("_", " ")).append("\n\n");
        if (map.containsKey("description")) {
            sb.append(map.get("description")).append("\n\n");
        }

        // 可选方案
        if (map.containsKey("approaches")) {
            sb.append("**可选方案:**\n");
            for (Object a : (List<Object>) map.get("approaches")) {
                sb.append("- ").append(a).append("\n");
            }
            sb.append("\n");
        }

        // 当前实现
        if (map.containsKey("current")) {
            sb.append("**当前实现:** ").append(map.get("current")).append("\n\n");
        }

        // 改进方向
        if (map.containsKey("improvement_ideas")) {
            sb.append("**改进方向:**\n");
            for (Object idea : (List<Object>) map.get("improvement_ideas")) {
                sb.append("- ").append(idea).append("\n");
            }
            sb.append("\n");
        }

        sb.append("---\n\n");
        return sb.toString();
    }

    @Override
    public boolean isReadOnly() { return true; }

    @Override
    public Input parseInput(String jsonInput) throws ToolException {
        try {
            JsonNode n = MAPPER.readTree(jsonInput);
            String action = n.has("action") ? n.get("action").asText() : "search";
            String query = n.has("query") ? n.get("query").asText() : "";
            String category = n.has("category") ? n.get("category").asText() : "all";
            String entryJson = n.has("entryJson") ? n.get("entryJson").asText() : null;
            return new Input(query, category, action, entryJson);
        } catch (Exception e) {
            throw new ToolException("解析输入失败: " + e.getMessage());
        }
    }

    @Override
    public String formatOutput(String output) { return output; }
}
