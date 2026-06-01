package com.moma.repository;

import com.moma.di.Component;
import com.moma.di.Inject;
import com.moma.task.Task;
import com.moma.task.TaskManager;

import java.util.List;

/**
 * 任务数据访问层。包装 TaskManager。
 */
@Component
public class TaskRepository {

    private final TaskManager taskManager;

    @Inject
    public TaskRepository(TaskManager taskManager) {
        this.taskManager = taskManager;
    }

    public Task create(String description, List<String> dependencies) {
        return taskManager.createTask(description, dependencies);
    }

    public Task findById(String id) {
        return taskManager.getTask(id);
    }

    public List<Task> findAll() {
        return taskManager.getAllTasks();
    }

    public void update(String id, String status, String result) {
        Task.Status taskStatus;
        try {
            taskStatus = Task.Status.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            return;
        }
        taskManager.updateStatus(id, taskStatus);
        if (result != null && !result.isBlank()) {
            taskManager.updateResult(id, result);
        }
    }

    public void deleteAll() {
        taskManager.clear();
    }

    public int count() {
        return taskManager.size();
    }
}
