package com.moma.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 记忆存储器。
 * 跨会话持久化 Agent 记忆，支持按项目/标签/类型检索。
 * 存储在 ~/.ca/memory/entries.json。
 */
public class MemoryStore {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryStore.class);

    private static final Path MEMORY_FILE = Paths.get(
        System.getProperty("user.home"), ".ca", "memory", "entries.json");

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<String, MemoryEntry> entries = new ConcurrentHashMap<>();

    /** 最大记忆条目数 */
    private static final int MAX_ENTRIES = 200;

    public MemoryStore() {
        try {
            Files.createDirectories(MEMORY_FILE.getParent());
        } catch (IOException e) {
            LOG.warn("无法创建记忆目录: {}", e.getMessage());
        }
        load();
    }

    /** 保存一条记忆 */
    public MemoryEntry save(MemoryEntry.Type type, String content, String project, String tags) {
        MemoryEntry entry = new MemoryEntry(type, content, project, tags);
        entries.put(entry.getId(), entry);
        trim();
        save();
        LOG.debug("保存记忆: {} — {}", entry.getId(), content.substring(0, Math.min(50, content.length())));
        return entry;
    }

    /** 按 ID 获取 */
    public Optional<MemoryEntry> get(String id) {
        MemoryEntry e = entries.get(id);
        if (e != null) e.markAccessed();
        return Optional.ofNullable(e);
    }

    /** 搜索记忆（按项目/标签/内容关键词） */
    public List<MemoryEntry> search(String project, String tagQuery, String keyword, int limit) {
        return entries.values().stream()
            .filter(e -> project == null || project.isBlank() || project.equals(e.getProject()))
            .filter(e -> tagQuery == null || tagQuery.isBlank()
                || (e.getTags() != null && e.getTags().toLowerCase().contains(tagQuery.toLowerCase())))
            .filter(e -> keyword == null || keyword.isBlank()
                || e.getContent().toLowerCase().contains(keyword.toLowerCase()))
            .sorted(Comparator.comparingLong(MemoryEntry::getAccessedAt).reversed())
            .limit(limit > 0 ? limit : 20)
            .collect(Collectors.toList());
    }

    /** 删除一条记忆 */
    public boolean delete(String id) {
        boolean removed = entries.remove(id) != null;
        if (removed) save();
        return removed;
    }

    /** 获取当前项目的相关记忆 */
    public List<MemoryEntry> getProjectMemories(String project, int limit) {
        return search(project, null, null, limit);
    }

    /** 记忆总数 */
    public int size() { return entries.size(); }

    private void trim() {
        if (entries.size() <= MAX_ENTRIES) return;
        // 删除最久未访问的条目
        List<Map.Entry<String, MemoryEntry>> sorted = entries.entrySet().stream()
            .sorted(Comparator.comparingLong(e -> e.getValue().getAccessedAt()))
            .collect(Collectors.toList());
        int toRemove = entries.size() - MAX_ENTRIES;
        for (int i = 0; i < toRemove; i++) {
            entries.remove(sorted.get(i).getKey());
        }
    }

    @SuppressWarnings("unchecked")
    private void load() {
        if (!Files.exists(MEMORY_FILE)) return;
        try {
            List<MemoryEntry> list = MAPPER.readValue(MEMORY_FILE.toFile(),
                new TypeReference<List<MemoryEntry>>() {});
            for (MemoryEntry e : list) {
                entries.put(e.getId(), e);
            }
            LOG.debug("已加载 {} 条记忆", list.size());
        } catch (IOException e) {
            LOG.warn("加载记忆失败: {}", e.getMessage());
        }
    }

    private void save() {
        try {
            MAPPER.writeValue(MEMORY_FILE.toFile(), new ArrayList<>(entries.values()));
        } catch (IOException e) {
            LOG.warn("保存记忆失败: {}", e.getMessage());
        }
    }
}
