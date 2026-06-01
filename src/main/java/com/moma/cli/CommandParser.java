package com.moma.cli;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * 斜杠命令解析器。
 * 支持 /command 和 /command --flag 格式。
 * 对应 MiniClaude 的 CommandRegistry。
 */
public class CommandParser {

    private final Map<String, CommandHandler> handlers;

    public CommandParser(Map<String, CommandHandler> handlers) {
        this.handlers = handlers;
    }

    /** 判断输入是否为斜杠命令 */
    public boolean isCommand(String input) {
        return input != null && input.startsWith("/");
    }

    /** 解析并执行命令 */
    public CommandResult execute(String input) {
        if (input == null || !input.startsWith("/")) {
            return new CommandResult(false, "不是有效的命令", null);
        }

        // 移除开头的 /
        String trimmed = input.substring(1).trim();

        // 分割命令名和参数
        String[] parts = trimmed.split("\\s+", 2);
        String commandName = parts[0].toLowerCase();
        String args = parts.length > 1 ? parts[1] : "";

        CommandHandler handler = handlers.get(commandName);
        if (handler == null) {
            List<String> available = handlers.keySet().stream()
                .sorted().toList();
            return new CommandResult(false,
                "未知命令: /" + commandName + "\n可用命令: " + available, null);
        }

        try {
            return handler.handle(args);
        } catch (Exception e) {
            return new CommandResult(false,
                "命令执行失败: " + e.getMessage(), null);
        }
    }

    /** 获取所有命令名 */
    public List<String> getCommandNames() {
        return handlers.keySet().stream().sorted().toList();
    }

    /** 命令执行结果 */
    public record CommandResult(
        boolean success,
        String message,
        Runnable action  // 可选的操作（如退出）
    ) {}

    /** 命令处理器 */
    @FunctionalInterface
    public interface CommandHandler {
        CommandResult handle(String args);
    }
}
