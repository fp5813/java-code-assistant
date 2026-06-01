package com.moma.memory;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;

/**
 * 记忆条目。
 * Agent 跨会话保存的重要上下文信息。
 * 存储在 ~/.ca/memory/ 目录下。
 */
public class MemoryEntry {

    public enum Type {
        @JsonProperty("fact") FACT,        // 客观事实
        @JsonProperty("preference") PREFERENCE,  // 用户偏好
        @JsonProperty("decision") DECISION,     // 架构/设计决策
        @JsonProperty("reference") REFERENCE     // 参考信息
    }

    private String id;
    private Type type;
    private String content;
    private String project;
    private String tags;
    private long createdAt;
    private long accessedAt;

    public MemoryEntry() {}

    public MemoryEntry(Type type, String content, String project, String tags) {
        this.id = java.util.UUID.randomUUID().toString().substring(0, 12);
        this.type = type;
        this.content = content;
        this.project = project;
        this.tags = tags;
        long now = Instant.now().toEpochMilli();
        this.createdAt = now;
        this.accessedAt = now;
    }

    // getters / setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public Type getType() { return type; }
    public void setType(Type type) { this.type = type; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public String getProject() { return project; }
    public void setProject(String project) { this.project = project; }

    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getAccessedAt() { return accessedAt; }
    public void setAccessedAt(long accessedAt) { this.accessedAt = accessedAt; }

    public void markAccessed() { this.accessedAt = Instant.now().toEpochMilli(); }

    @Override
    public String toString() {
        return String.format("[%s] [%s] %s (%s)", type, id, content.length() > 80 ? content.substring(0, 80) + "..." : content, project);
    }
}
