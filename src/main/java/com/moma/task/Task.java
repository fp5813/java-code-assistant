package com.moma.task;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 任务模型。
 * 用于 Agent 分解复杂请求为可执行的子任务。
 * 对应 MiniClaude 的 Task 系统。
 */
public class Task {

    public enum Status {
        @JsonProperty("pending") PENDING,
        @JsonProperty("in_progress") IN_PROGRESS,
        @JsonProperty("completed") COMPLETED,
        @JsonProperty("failed") FAILED,
        @JsonProperty("blocked") BLOCKED
    }

    private String id;
    private String description;
    private Status status;
    private List<String> dependencies; // 依赖的任务 ID
    private String result;
    private String errorMessage;
    private long createdAt;
    private long updatedAt;

    public Task() {} // Jackson

    public Task(String description, List<String> dependencies) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.description = description;
        this.status = Status.PENDING;
        this.dependencies = dependencies != null ? new ArrayList<>(dependencies) : new ArrayList<>();
        this.createdAt = Instant.now().toEpochMilli();
        this.updatedAt = this.createdAt;
    }

    public Task(String description) {
        this(description, null);
    }

    // ─── getters / setters ───

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) {
        this.status = status;
        this.updatedAt = Instant.now().toEpochMilli();
    }

    public List<String> getDependencies() { return dependencies; }
    public void setDependencies(List<String> dependencies) { this.dependencies = dependencies; }

    public String getResult() { return result; }
    public void setResult(String result) {
        this.result = result;
        this.updatedAt = Instant.now().toEpochMilli();
    }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        this.updatedAt = Instant.now().toEpochMilli();
    }

    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }

    public long getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(long updatedAt) { this.updatedAt = updatedAt; }

    /** 是否可以被执行（无未完成的依赖） */
    public boolean isRunnable(TaskManager manager) {
        if (status != Status.PENDING && status != Status.FAILED) {
            return false;
        }
        for (String depId : dependencies) {
            Task dep = manager.getTask(depId);
            if (dep == null || dep.getStatus() != Status.COMPLETED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String toString() {
        return String.format("[%s] %s — %s%s",
            id, description, status,
            dependencies.isEmpty() ? "" : " (依赖: " + String.join(", ", dependencies) + ")");
    }
}
