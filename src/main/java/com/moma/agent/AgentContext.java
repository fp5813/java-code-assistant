package com.moma.agent;

import com.moma.model.ModelProvider;
import com.moma.task.TaskManager;

import java.nio.file.Paths;

/**
 * Agent 执行上下文。
 * 包含 Agent 运行所需的环境信息，
 * 在 perceive-think-act 循环的 "perceive" 阶段收集。
 */
public class AgentContext {

    private final String workingDirectory;
    private final String currentModel;
    private TaskManager taskManager;
    private boolean planMode;

    /** Token 使用统计 */
    private int inputTokens;
    private int outputTokens;
    private int totalToolCalls;

    public AgentContext(String workingDirectory, ModelProvider modelProvider, String currentModel) {
        this.workingDirectory = workingDirectory;
        this.currentModel = currentModel;
    }

    /** 当前工作目录 */
    public String getWorkingDirectory() { return workingDirectory; }

    /** 项目名称 */
    public String getProjectName() {
        return Paths.get(workingDirectory).getFileName().toString();
    }

    /** 当前使用的模型名称 */
    public String getCurrentModel() { return currentModel; }

    // ─── Task Manager ───

    public TaskManager getTaskManager() { return taskManager; }

    public void setTaskManager(TaskManager taskManager) { this.taskManager = taskManager; }

    // ─── Plan Mode ───

    public boolean isPlanMode() { return planMode; }

    public void setPlanMode(boolean planMode) { this.planMode = planMode; }

    // ─── 统计 ───

    public void recordTokens(int input, int output) {
        this.inputTokens += input;
        this.outputTokens += output;
    }

    public void recordToolCall() { this.totalToolCalls++; }

    public int getInputTokens() { return inputTokens; }
    public int getOutputTokens() { return outputTokens; }
    public int getTotalToolCalls() { return totalToolCalls; }
}
