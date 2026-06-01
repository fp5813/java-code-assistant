package com.moma.controller;

import com.moma.cli.CommandParser;
import com.moma.di.Inject;
import com.moma.task.Task;
import com.moma.task.TaskManager;

import java.util.Map;

/**
 * 处理 /tasks 和 /tasks clear 命令的控制器。
 */
public class TaskController extends CommandController {

    private final TaskManager taskManager;

    @Inject
    public TaskController(TaskManager taskManager) {
        super("tasks");
        this.taskManager = taskManager;
    }

    @Override
    public void registerHandlers(Map<String, CommandParser.CommandHandler> handlers) {
        // ── /tasks 命令 ──
        handlers.put("tasks", args -> {
            if (args != null && args.trim().equalsIgnoreCase("clear")) {
                taskManager.clear();
                return new CommandParser.CommandResult(true, "所有任务已清除。", null);
            }
            var all = taskManager.getAllTasks();
            if (all.isEmpty()) {
                return new CommandParser.CommandResult(true, "暂无任务。使用 TaskCreate 工具创建任务。", null);
            }
            StringBuilder sb = new StringBuilder("\u001B[1m任务列表:\u001B[0m\n");
            for (Task t : all) {
                sb.append(String.format("  [%s] %s — %s%n",
                    t.getId(), t.getDescription(),
                    t.getStatus()));
            }
            sb.append("共 ").append(all.size()).append(" 个任务");
            return new CommandParser.CommandResult(true, sb.toString(), null);
        });
    }
}
