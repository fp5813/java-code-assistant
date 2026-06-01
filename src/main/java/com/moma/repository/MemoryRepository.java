package com.moma.repository;

import com.moma.di.Component;
import com.moma.di.Inject;
import com.moma.memory.MemoryEntry;
import com.moma.memory.MemoryStore;

import java.util.List;
import java.util.Optional;

/**
 * 记忆数据访问层。包装 MemoryStore。
 * 对应 Spring Data Repository 模式。
 */
@Component
public class MemoryRepository {

    private final MemoryStore memoryStore;

    @Inject
    public MemoryRepository(MemoryStore memoryStore) {
        this.memoryStore = memoryStore;
    }

    public MemoryEntry save(MemoryEntry.Type type, String content, String project, String tags) {
        return memoryStore.save(type, content, project, tags);
    }

    public Optional<MemoryEntry> findById(String id) {
        return memoryStore.get(id);
    }

    public List<MemoryEntry> search(String project, String tagQuery, String keyword, int limit) {
        return memoryStore.search(project, tagQuery, keyword, limit);
    }

    public List<MemoryEntry> findByProject(String project, int limit) {
        return memoryStore.getProjectMemories(project, limit);
    }

    public boolean deleteById(String id) {
        return memoryStore.delete(id);
    }

    public int count() {
        return memoryStore.size();
    }
}
