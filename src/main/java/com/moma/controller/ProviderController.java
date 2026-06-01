package com.moma.controller;

import com.moma.cli.CommandParser;
import com.moma.di.Inject;
import com.moma.model.ProviderRegistry;

import java.util.Map;

/**
 * 处理 /provider 和 /model 命令的控制器。
 */
public class ProviderController extends CommandController {

    private final ProviderRegistry providerRegistry;

    @Inject
    public ProviderController(ProviderRegistry providerRegistry) {
        super("provider");
        this.providerRegistry = providerRegistry;
    }

    @Override
    public void registerHandlers(Map<String, CommandParser.CommandHandler> handlers) {
        // ── /provider 命令 ──
        handlers.put("provider", args -> {
            if (args == null || args.isBlank()) {
                // 列出所有 Provider
                StringBuilder sb = new StringBuilder("\u001B[1m可用 Provider:\u001B[0m\n");
                for (String name : providerRegistry.getProviderNames()) {
                    String marker = name.equals(providerRegistry.getActiveProvider())
                        ? " \u001B[32m← 当前\u001B[0m" : "";
                    sb.append("  ").append(providerRegistry.describeProvider(name)).append(marker).append("\n");
                }
                return new CommandParser.CommandResult(true, sb.toString(), null);
            }

            // 解析 provider name
            String providerName = args.trim();
            String result = providerRegistry.switchTo(providerName, null);
            if (result.startsWith("错误")) {
                return new CommandParser.CommandResult(false, result, null);
            }
            return new CommandParser.CommandResult(true, result, null);
        });

        // ── /model 命令 ──
        handlers.put("model", args -> {
            if (args == null || args.isBlank()) {
                return new CommandParser.CommandResult(true,
                    "当前模型: " + providerRegistry.getActiveModel()
                    + "\n使用 /model <name> 切换模型（如 /model gpt-4o-mini）", null);
            }

            String modelName = args.trim();
            String result = providerRegistry.switchTo(
                providerRegistry.getActiveProvider(), modelName);
            if (result.startsWith("错误")) {
                return new CommandParser.CommandResult(false, result, null);
            }
            return new CommandParser.CommandResult(true, result, null);
        });
    }
}
