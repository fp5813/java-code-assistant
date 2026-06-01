# CODEBUDDY.md

This file provides guidance to CodeBuddy Code when working with code in this repository.

## Commands

| Command | Description |
|---------|-------------|
| `mvn compile` | 编译项目 |
| `mvn package -DskipTests` | 打包为 fat JAR（含所有依赖） |
| `mvn test` | 运行测试 |
| `mvn clean compile` | 清理并编译 |
| `java -jar target/java-code-assistant.jar` | 运行项目（需先配置 API Key） |
| `start.bat` | Windows 快捷启动 |

## 配置

支持两个配置源（优先级由高到低）：
1. 项目根目录 `.ca/settings.json`
2. 用户主目录 `.claude/settings.json`（兼容 Claude Code）
3. 项目根目录 `.env` 文件

支持多 Provider 配置（在 `providers` 块中定义），运行时通过 `/provider` 命令热切换。

完整配置示例 (`settings.example.json`):
```json
{
  "env": {
    "ANTHROPIC_BASE_URL": "https://api.deepseek.com",
    "ANTHROPIC_AUTH_TOKEN": "sk-your-api-key-here",
    "ANTHROPIC_MODEL": "deepseek-chat"
  },
  "model": "deepseek-chat",
  "providers": {
    "openai": {
      "baseUrl": "https://api.openai.com/v1",
      "apiKey": "sk-...",
      "models": ["gpt-4o", "gpt-4o-mini"],
      "description": "OpenAI API"
    },
    "deepseek": {
      "baseUrl": "https://api.deepseek.com",
      "apiKey": "sk-...",
      "models": ["deepseek-chat", "deepseek-coder"],
      "description": "DeepSeek API"
    }
  }
}
```

运行时可切换 Provider/Model:
```
/provider              # 列出所有可用 Provider
/provider deepseek     # 切换到 DeepSeek
/model gpt-4o-mini     # 当前 Provider 内切换模型
```

## 架构概览

基于 LangChain4j 的 AI 编程助手，核心为 perceive-think-act 闭环。

```
src/main/java/com/codeassist/
├── CodeAssistant.java         # 主入口
├── cli/
│   ├── CliApp.java            # JLine REPL 交互循环
│   └── CommandParser.java     # 斜杠命令解析 (/help, /exit, /plan)
├── agent/
│   ├── AgentLoop.java         # perceive-think-act 循环 + 工具编排
│   ├── AgentContext.java      # 代理上下文（含 TaskManager、PlanMode）
│   ├── SystemPrompt.java      # 系统提示词模板（支持计划模式）
│   └── SessionManager.java    # 会话持久化和历史管理
├── security/
│   ├── HardDenyRule.java      # 安全规则模型（"ToolName(pattern)" 格式）
│   └── HardDenyManager.java   # 集中式安全规则引擎
├── task/
│   ├── Task.java              # 任务模型（id, status, dependencies）
│   ├── TaskManager.java       # 任务管理器（CRUD + 持久化）
│   ├── TaskCreateTool.java    # 创建子任务
│   ├── TaskListTool.java      # 列出任务
│   ├── TaskUpdateTool.java    # 更新任务状态
│   └── TaskGetTool.java       # 查看任务详情
├── plan/
│   ├── PlanManager.java       # 计划模式状态管理
│   ├── EnterPlanModeTool.java # 进入计划模式（Agent 先规划后执行）
│   └── ExitPlanModeTool.java  # 退出计划模式
├── skill/
│   ├── Skill.java             # 技能模型（名称 + 提示词 + 工具约束）
│   ├── SkillManager.java      # 技能管理器（内置 4 个技能）
│   └── SkillTool.java         # 激活技能工具
├── plan/
│   ├── PlanManager.java       # 计划模式状态管理
│   ├── EnterPlanModeTool.java # 进入计划模式（Agent 先规划后执行）
│   └── ExitPlanModeTool.java  # 退出计划模式
├── tool/
│   ├── Tool.java              # 工具泛型接口
│   ├── ToolRegistry.java      # 工具注册中心
│   ├── ToolResult.java        # 工具执行结果
│   ├── ToolException.java     # 工具异常
│   ├── JsonSchemaParser.java  # JSON Schema 解析器
│   ├── ReadTool.java          # 读取文件（行范围）
│   ├── WriteTool.java         # 写入/覆盖文件
│   ├── GlobTool.java          # 通配符搜索文件
│   ├── EditTool.java          # 精准编辑（文本匹配/行号替换）
│   ├── GrepTool.java          # 内容搜索（正则 + 文件过滤）
│   ├── BashTool.java          # Shell 执行（超时 + hard_deny）
│   ├── GitDiffTool.java       # Git 差异查看
│   ├── GitStatusTool.java     # Git 状态查看
│   ├── GitCommitTool.java     # Git 提交
│   ├── HtmlOutputTool.java    # HTML 报告生成
│   └── LspTool.java           # LSP 诊断分析
├── memory/
│   ├── MemoryEntry.java       # 记忆模型
│   ├── MemoryStore.java       # 记忆存储
│   ├── MemorySaveTool.java    # 保存记忆
│   └── MemorySearchTool.java  # 搜索记忆
├── lsp/
│   └── LspClient.java         # LSP 客户端
├── model/
│   ├── ModelProvider.java     # 模型提供商接口
│   ├── OpenAiProvider.java    # OpenAI 兼容 API 实现
│   ├── ProviderConfig.java    # Provider 配置 POJO
│   └── ProviderRegistry.java  # Provider 注册中心（运行时热切换）
└── config/
    ├── AppConfig.java         # 配置模型
    └── ConfigLoader.java      # 配置加载器
```

### Agent Loop 流程（含工具编排）

```
User Input
    │
    ▼
┌──────────────────────────────────────────────────────────┐
│  PERCEIVE: 收集输入 + 历史消息 + 上下文                    │
│  SystemPrompt + UserMessage → messages[]                  │
│                                                          │
│  THINK: 调用 LLM (modelSupplier.get().generate(...))  │
│    （Supplier 模式支持运行时 Provider 热切换）           │
│    ├── 返回 text → 输出给用户, 结束                        │
│    └── 返回 tool_use → ACT                               │
│                                                          │
│  ACT: 工具编排（参照 MiniClaude ToolOrchestration）        │
│    ├── 只读工具（Read/Glob/Grep）：并发执行                 │
│    └── 写工具（Write/Edit/Bash）：串行执行                 │
│                                                          │
│    每个工具结果 → ToolResultMessage → 追加到 messages[]    │
│    → 继续 THINK                                           │
└──────────────────────────────────────────────────────────┘
```

### 工具模式

每个工具实现 `Tool<I, O>` 接口：
- `name()` / `description()` — 供 LLM 识别的元信息
- `inputSchema()` — JSON Schema 描述输入参数
- `parseInput(json)` — 解析 JSON 字符串为 Java 对象
- `execute(input, context)` — 执行核心逻辑
- `formatOutput(output)` — 将结果格式化为 JSON 字符串
- `isReadOnly()` — 只读工具可并发执行

### 关键技术

- **Runtime**: Java 21
- **框架**: LangChain4j 0.36.2 (core + open-ai)
- **CLI**: JLine 3 (REPL + 多行输入 + 历史)
- **构建**: Maven + shade-plugin (fat JAR)

### Provider 热切换机制

参照 MiniClaude 的 `/provider` 命令模式：

1. **ProviderRegistry** 管理所有已注册 Provider
2. 从 `settings.json` 的 `providers` 块加载多 Provider 配置
3. **AgentLoop 通过 `Supplier<ChatLanguageModel>` 获取模型**，每次迭代获取最新实例
4. `/provider <name>` 切换时重建 `ChatLanguageModel`，下一轮 Agent 迭代自动生效
5. 配置源格式与 Claude Code / MiniClaude 兼容

### 安全系统 (hard_deny)

集中式安全规则引擎，在工具执行前拦截：
- 规则格式: `"ToolName(pattern)"` 或 `"ToolName(*)"`（禁止所有调用）
- 默认包含 9 条安全规则（rm -rf /、sudo rm、fork bomb 等）
- 集成在 `ToolRegistry.getToolChecked()` 中，**所有工具调用强制执行**
- BashTool 移除内置 hard_deny 检查，统一由全局引擎管理

### Git 集成

| 工具 | 功能 |
|------|------|
| `GitDiff` | 查看工作区/暂存区差异，支持指定路径和提交范围 |
| `GitStatus` | 查看文件状态（已修改/已暂存/未跟踪）|
| `GitCommit` | 暂存变更（可选 --all）并创建提交 |

所有 Git 工具共享 `GitDiffTool.execGit()` 方法，统一处理进程执行和输出截断。

### 技能系统 (Skills)

Agent 可主动选择技能来调整行为模式。内置 4 个技能：

| 技能 | 适用场景 | 约束工具 |
|------|---------|---------|
| `code-review` | 代码审查：正确性、安全、性能、可维护性 | Read, Glob, Grep, Git* |
| `test-generation` | 生成单元测试 | Read, Glob, Write, Edit |
| `refactoring` | 重构：优化结构，不改变外部行为 | Read, Write, Edit, Git* |
| `bug-fix` | Bug 定位和修复 | Read, Glob, Grep, Edit, Git* |

Agent 通过 `Skill` 工具激活技能：激活后系统提示词中注入技能指引。

### 任务系统

Agent 使用工具分解复杂请求为可追踪的子任务：

```
TaskCreate(description, [deps])  → 创建任务，返回 taskId
TaskList()                       → 列出所有任务及其状态
TaskGet(taskId)                  → 查看单个任务详情
TaskUpdate(taskId, status/result)→ 更新任务状态或标记完成/失败
```

任务持久化在 `~/.ca/tasks/tasks-{sessionId}.json`。

### 计划模式

对应 MiniClaude 的 EnterPlanMode/ExitPlanMode：
- `/plan` — 进入计划模式。Agent 先输出计划方案，创建任务，用户确认后逐步执行
- `/plan execute` — 退出计划模式，开始执行
- 计划模式下系统提示词插入计划模式指引, 引导 Agent 先规划后行动

### 记忆系统 (Memory)

跨会话保存重要上下文信息：

| 工具 | 功能 |
|------|------|
| `MemorySave(type, content, tags)` | 保存记忆（类型: fact/preference/decision/reference）|
| `MemorySearch(keyword, type, tags)` | 按关键词/类型/标签搜索记忆 |

存储位置: `~/.ca/memory/entries.json`，上限 200 条，超限时淘汰最久未访问条目。

### HTML 输出

`HtmlOutput(title, bodyHtml, filename)` 生成带样式的 HTML 报告并自动在浏览器打开。
支持表格、徽章、代码高亮等样式。输出目录: `.ca-html-output/`。

### LSP 诊断

`Lsp(filePath)` 启动语言服务器并获取代码诊断信息（错误/警告）。
支持文件类型: .java (jdtls)、.ts/.js (typescript-language-server)、.py (pylsp)。
通过 JSON-RPC 协议实现 LSP 客户端通信。需在系统中安装相应的语言服务器。

### 当前工具总表 (21 个)

| Phase | 工具列表 |
|-------|---------|
| 1 | Read, Write, Glob |
| 2 | Edit, Grep, Bash |
| 3 | (Provider 热切换) |
| 4 | TaskCreate, TaskList, TaskUpdate, TaskGet, EnterPlanMode, ExitPlanMode |
| 5 | GitDiff, GitStatus, GitCommit, Skill, (HardDeny 安全层) |
| 6 | MemorySave, MemorySearch, HtmlOutput, Lsp |
