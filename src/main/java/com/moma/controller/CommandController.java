package com.moma.controller;

import com.moma.cli.CommandParser;

/**
 * CLI 命令控制器抽象基类。
 * 对应 Spring MVC 的 @Controller 模式。
 * 子类通过 registerHandlers() 注册斜杠命令处理器。
 */
public abstract class CommandController {

    private final String prefix;

    protected CommandController(String prefix) {
        this.prefix = prefix;
    }

    public String getPrefix() { return prefix; }

    /**
     * 注册此控制器的命令处理器到 handlers map。
     * 子类在此方法中调用 handlers.put(name, handler)。
     */
    public abstract void registerHandlers(java.util.Map<String, CommandParser.CommandHandler> handlers);
}
