package com.moma.task;

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
 * 任务管理器。
 * 管理 Agent 任务的创建、查询、更新和持久化。
 * 任务文件存储在 {user.home}/.ca/tasks/ 目录下。
 */
public class TaskManager {

    private static final Logger LOG = LoggerFactory.getLogger(TaskManager.class);

    private static final Path TASKS_DIR = Paths.get(
        System.getProperty("user.home"), ".ca", "tasks");

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    private final Map<String, Task> tasks = new ConcurrentHashMap<>();
    private final String sessionId;

    public TaskManager(String sessionId) {
        this.sessionId = sessionId;
        try {
            Files.createDirectories(TASKS_DIR);
        } catch (IOException e) {
            LOG.warn("无法创建任务目录: {}", e.getMessage());
        }
        loadFromDisk();
    }

    // ─── 增删改查 ───

    public Task createTask(String description, List<String> dependencies) {
        Task task = new Task(description, dependencies);
        tasks.put(task.getId(), task);
        saveToDisk();
        LOG.debug("创建任务: {} — {}", task.getId(), description);
        return task;
    }

    public Task createTask(String description) {
        return createTask(description, null);
    }

    public Task getTask(String id) {
        return tasks.get(id);
    }

    public List<Task> getAllTasks() {
        List<Task> list = new ArrayList<>(tasks.values());
        list.sort(Comparator.comparingLong(Task::getCreatedAt));
        return list;
    }

    public List<Task> getTasksByStatus(Task.Status status) {
        return tasks.values().stream()
            .filter(t -> t.getStatus() == status)
            .sorted(Comparator.comparingLong(Task::getCreatedAt))
            .collect(Collectors.toList());
    }

    public List<Task> getRunnableTasks() {
        return tasks.values().stream()
            .filter(t -> t.isRunnable(this))
            .sorted(Comparator.comparingLong(Task::getCreatedAt))
            .collect(Collectors.toList());
    }

    public boolean updateStatus(String id, Task.Status status) {
        Task task = tasks.get(id);
        if (task == null) return false;
        task.setStatus(status);
        saveToDisk();
        return true;
    }

    public boolean updateResult(String id, String result) {
        Task task = tasks.get(id);
        if (task == null) return false;
        task.setResult(result);
        task.setStatus(Task.Status.COMPLETED);
        saveToDisk();
        return true;
    }

    public boolean updateError(String id, String error) {
        Task task = tasks.get(id);
        if (task == null) return false;
        task.setErrorMessage(error);
        task.setStatus(Task.Status.FAILED);
        saveToDisk();
        return true;
    }

    public boolean deleteTask(String id) {
        Task removed = tasks.remove(id);
        if (removed != null) {
            saveToDisk();
            return true;
        }
        return false;
    }

    /** 清除所有任务 */
    public void clear() {
        tasks.clear();
        saveToDisk();
    }

    /** 任务数量 */
    public int size() { return tasks.size(); }

    // ─── 持久化 ───

    private Path getStorageFile() {
        return TASKS_DIR.resolve("tasks-" + sessionId + ".json");
    }

    private void saveToDisk() {
        try {
            MAPPER.writeValue(getStorageFile().toFile(), new ArrayList<>(tasks.values()));
        } catch (IOException e) {
            LOG.warn("保存任务失败: {}", e.getMessage());
        }
    }

    private void loadFromDisk() {
        Path file = getStorageFile();
        if (!Files.exists(file)) return;
        try {
            List<Task> loaded = MAPPER.readValue(file.toFile(), new TypeReference<List<Task>>() {});
            for (Task task : loaded) {
                tasks.put(task.getId(), task);
            }
            LOG.debug("已加载 {} 个任务", loaded.size());
        } catch (IOException e) {
            LOG.warn("加载任务失败: {}", e.getMessage());
        }
    }
}
