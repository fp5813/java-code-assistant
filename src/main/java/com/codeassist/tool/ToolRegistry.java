package com.codeassist.tool;

import com.codeassist.security.HardDenyManager;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 工具注册中心。
 * 对应 MiniClaude 的 {@code getTools()} + {@code assembleToolPool()} 模式。
 */
public class ToolRegistry {

    private static final Logger LOG = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, Tool<?, ?>> tools = new ConcurrentHashMap<>();
    private HardDenyManager hardDenyManager;

    /** 设置 HardDeny 管理器 */
    public void setHardDenyManager(HardDenyManager hardDenyManager) {
        this.hardDenyManager = hardDenyManager;
    }

    /** 注册一个工具 */
    public void register(Tool<?, ?> tool) {
        Tool<?, ?> existing = tools.put(tool.name(), tool);
        if (existing != null) {
            LOG.warn("Tool '{}' 被覆盖注册", tool.name());
        } else {
            LOG.debug("已注册工具: {}", tool.name());
        }
    }

    /** 取消注册 */
    public void unregister(String name) {
        tools.remove(name);
    }

    /** 按名称获取工具 */
    public Tool<?, ?> getTool(String name) {
        return tools.get(name);
    }

    /**
     * 获取工具并执行 HardDeny 安全检查。
     *
     * @throws ToolException 如果被规则禁止
     */
    public Tool<?, ?> getToolChecked(String name, String inputJson) throws ToolException {
        Tool<?, ?> tool = tools.get(name);
        if (tool == null) return null;
        if (hardDenyManager != null) {
            hardDenyManager.enforce(tool, inputJson);
        }
        return tool;
    }

    /** 获取所有启用的工具 */
    public List<Tool<?, ?>> getEnabledTools() {
        List<Tool<?, ?>> result = new ArrayList<>();
        for (Tool<?, ?> tool : tools.values()) {
            if (tool.isEnabled()) {
                result.add(tool);
            }
        }
        return result;
    }

    /** 转换为 LangChain4j 的 ToolSpecification 列表 */
    public List<ToolSpecification> getToolSpecifications() {
        List<ToolSpecification> specs = new ArrayList<>();
        for (Tool<?, ?> tool : getEnabledTools()) {
            specs.add(ToolSpecification.builder()
                .name(tool.name())
                .description(tool.description())
                .parameters(parseJsonSchema(tool.inputSchema()))
                .build());
        }
        return specs;
    }

    private JsonObjectSchema parseJsonSchema(String schemaJson) {
        try {
            return JsonSchemaParser.parse(schemaJson);
        } catch (Exception e) {
            LOG.warn("Failed to parse JSON Schema: {}", e.getMessage());
            return JsonObjectSchema.builder().build();
        }
    }

    public HardDenyManager getHardDenyManager() { return hardDenyManager; }
    public int size() { return tools.size(); }
}
