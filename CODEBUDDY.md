# CODEBUDDY.md

This file provides guidance to CodeBuddy Code when working with code in this repository.

## Commands

| Command | Description |
|---------|-------------|
| `mvn compile` | 编译项目 |
| `mvn clean` | 清理构建产物 |
| `mvn clean compile` | 清理并编译 |
| `mvn package -DskipTests` | 打包为 fat JAR（含所有依赖） |
| `mvn test` | 运行所有测试 |
| `mvn test -Dtest=ClassName` | 运行单个测试类 |
| `mvn exec:java -Dexec.mainClass="com.moma.CodeAssistant"` | 从源码运行 |
| `java -jar target/moma.jar` | 运行墨码（需先配置 API Key） |
| `start.bat` | Windows 快捷启动 |

## 配置

支持三个配置源（优先级由高到低）：
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

## 启动流程

`CodeAssistant.main()` 启动顺序：

1. 加载配置（`ConfigLoader.load()`）
2. 初始化 DI 容器（`ApplicationContext` 扫描 `com.moma` 包，扫描 `@Component`/`@Configuration` 注解，创建 Bean、注入依赖、执行 `@PostConstruct`）
3. 启动 JLine REPL 交互循环（`CliApp.start()`）

所有控制器（`CommandController` 子类）在容器启动时自动注册到 `CommandParser`。AgentLoop 仅在首次用户输入时懒初始化。

## 架构概览

基于 LangChain4j 的 AI 编程助手，核心为 perceive-think-act 闭环。项目使用自定义注解驱动的轻量级 DI 容器替代 Spring。

### 目录结构

```
src/main/java/com/moma/
├── CodeAssistant.java         # 主入口（配置加载 + DI 容器初始化 + REPL 启动）
├── cli/
│   ├── CliApp.java            # JLine REPL 交互循环（支持多行输入、历史记录）
│   └── CommandParser.java     # 斜杠命令解析 (/help, /exit, /plan)
├── agent/
│   ├── AgentLoop.java         # perceive-think-act 循环 + 工具编排
│   ├── AgentContext.java      # 代理上下文（含 TaskManager、PlanMode）
│   ├── SystemPrompt.java      # 系统提示词模板（支持计划模式）
│   └── SessionManager.java    # 会话持久化和历史管理
├── service/
│   ├── ToolOrchestrationService.java  # 工具编排（只读并发、写入串行）
│   ├── AgentService.java      # Agent 生命周期管理
│   ├── MessageService.java    # 消息构建和格式化
│   ├── SessionService.java    # 会话管理
│   └── CacheService.java      # 缓存服务封装
├── controller/
│   ├── CommandController.java    # CLI 命令控制器抽象基类
│   ├── ProviderController.java   # /provider, /model 命令
│   ├── PlanController.java       # /plan, /plan execute 命令
│   ├── TaskController.java       # /tasks, /tasks clear 命令
│   ├── MemoryController.java     # /memory 命令
│   ├── StatusController.java     # /status 命令
│   └── CommandController.java    # /help, /clear, /exit 命令
├── tool/
│   ├── Tool.java              # 工具泛型接口
│   ├── ToolRegistry.java      # 工具注册中心（含 HardDeny 安全检查）
│   ├── ToolResult.java        # 工具执行结果
│   ├── ToolException.java     # 工具异常
│   ├── JsonSchemaParser.java  # JSON Schema 解析器（LLM -> Java 对象）
│   ├── ReadTool.java          # 读取文件（行范围）
│   ├── WriteTool.java         # 写入/覆盖文件
│   ├── GlobTool.java          # 通配符搜索文件
│   ├── EditTool.java          # 精准编辑（文本匹配/行号替换 + .bak 备份）
│   ├── GrepTool.java          # 内容搜索（正则 + 文件过滤）
│   ├── BashTool.java          # Shell 执行（超时 + hard_deny）
│   ├── GitDiffTool.java       # Git 差异查看
│   ├── GitStatusTool.java     # Git 状态查看
│   ├── GitCommitTool.java     # Git 提交
│   ├── HtmlOutputTool.java    # HTML 报告生成
│   ├── LspTool.java           # LSP 诊断分析
│   ├── GhPrCreateTool.java    # 创建 GitHub PR
│   ├── GhPrListTool.java      # 列出 GitHub PR
│   ├── GhIssueListTool.java   # 列出 GitHub Issue
│   └── GhCommand.java         # gh CLI 执行共享方法
├── di/
│   ├── ApplicationContext.java    # 轻量级 DI 容器（包扫描、@Inject、@Value、循环依赖检测）
│   ├── Component.java             # @Component 注解
│   ├── Configuration.java         # @Configuration 注解
│   ├── Bean.java                  # @Bean 注解
│   ├── Inject.java                # @Inject 注解
│   ├── Value.java                 # @Value("${key:default}") 注解
│   ├── PostConstruct.java         # @PostConstruct 初始化回调
│   ├── Primary.java               # @Primary 类型歧义处理
│   ├── BeanDefinition.java        # Bean 定义元信息
│   └── ComponentScanner.java      # 包扫描器
├── config/
│   ├── AppConfig.java         # 配置模型 POJO
│   ├── ConfigLoader.java      # 配置加载器（settings.json -> .env -> 默认值）
│   ├── DiConfig.java          # DI 配置类（定义基础设施 Bean 的创建逻辑）
│   ├── CacheConfig.java       # 缓存配置
│   └── ThreadPoolConfig.java  # 线程池配置
├── cache/
│   ├── CacheManager.java      # 缓存管理器接口（策略模式）
│   ├── Cacheable.java         # 缓存注解
│   ├── CacheAspect.java       # 缓存切面
│   ├── LocalCacheManager.java # Caffeine 本地缓存实现
│   └── RedisCacheManager.java # Redis 缓存实现（自动降级到本地缓存）
├── concurrent/
│   ├── EventBus.java             # 事件总线（组件间松耦合通信，支持同步/异步分发）
│   ├── ThreadPoolManager.java    # 线程池管理器（虚拟线程 + 平台线程）
│   ├── ThreadPoolConfigData.java # 线程池配置数据
│   ├── AsyncExecutor.java        # 异步执行器（Future 模式）
│   └── AsyncToolExecutor.java    # 异步工具执行器（超时控制、结果预取、取消令牌）
├── context/
│   ├── ContextManager.java       # 上下文管理器（Token 估算、消息裁剪、摘要压缩）
│   └── ContextWindowRegistry.java# 模型上下文窗口注册表
├── repository/
│   ├── SessionRepository.java    # 会话数据持久化
│   ├── TaskRepository.java       # 任务数据持久化
│   └── MemoryRepository.java     # 记忆数据持久化
├── memory/
│   ├── MemoryEntry.java       # 记忆模型
│   ├── MemoryStore.java       # 记忆存储
│   ├── MemorySaveTool.java    # 保存记忆（Agent 工具）
│   └── MemorySearchTool.java  # 搜索记忆（Agent 工具）
├── plan/
│   ├── PlanManager.java       # 计划模式状态管理
│   ├── EnterPlanModeTool.java # 进入计划模式（Agent 先规划后执行）
│   └── ExitPlanModeTool.java  # 退出计划模式
├── skill/
│   ├── Skill.java             # 技能模型（名称 + 提示词 + 工具约束）
│   ├── SkillManager.java      # 技能管理器（内置 4 个技能）
│   └── SkillTool.java         # 激活技能工具
├── lsp/
│   └── LspClient.java         # LSP 客户端（JSON-RPC 协议）
├── model/
│   ├── ModelProvider.java     # 模型提供商接口
│   ├── OpenAiProvider.java    # OpenAI 兼容 API 实现
│   ├── ProviderConfig.java    # Provider 配置 POJO
│   └── ProviderRegistry.java  # Provider 注册中心（运行时热切换）
└── security/
    ├── HardDenyRule.java      # 安全规则模型（"ToolName(pattern)" 格式）
    └── HardDenyManager.java   # 集中式安全规则引擎

src/test/java/com/moma/
├── cache/LocalCacheManagerTest.java
├── concurrent/ThreadPoolManagerTest.java
├── context/ContextManagerTest.java
└── di/ApplicationContextTest.java
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
│  ACT: ToolOrchestrationService 编排工具执行               │
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
- **CLI**: JLine 3.27 (REPL + 多行输入 + 历史)
- **构建**: Maven + shade-plugin (fat JAR)
- **JSON**: Jackson 2.18 (databind + yaml)
- **日志**: SLF4J + Logback 1.5
- **测试**: JUnit 5.11
- **Redis**: Jedis 5.2（可选，不可用时自动降级）

### DI 容器

项目使用自定义轻量级 DI 容器 `ApplicationContext`，无需 Spring 依赖：

- **注解驱动**: `@Component`、`@Configuration`、`@Bean`、`@Inject`、`@Value("${key:default}")`
- **注入方式**: 构造器注入 + 字段注入
- **特性**: `@Primary` 处理类型歧义、`@PostConstruct` 初始化回调、`@Value` 占位符替换（支持默认值）、包扫描、循环依赖检测
- **入口**: `DiConfig` 相当于 Spring `@Configuration`，定义基础设施 Bean 的创建逻辑

### Provider 热切换机制

参照 MiniClaude 的 `/provider` 命令模式：

1. **ProviderRegistry** 管理所有已注册 Provider
2. 从 `settings.json` 的 `providers` 块加载多 Provider 配置
3. **AgentLoop 通过 `Supplier<ChatLanguageModel>` 获取模型**，每次迭代获取最新实例
4. `/provider <name>` 切换时重建 `ChatLanguageModel`，下一轮 Agent 迭代自动生效
5. 配置源格式与 Claude Code / MiniClaude 兼容

### 上下文管理（ContextManager）

AgentLoop 每次收到用户输入后触发，防止上下文窗口溢出：

1. 估算当前消息列表的 Token 总数（含工具定义开销，基于 `OpenAiTokenizer`）
2. 若使用率 > 65% → 基于 Token 数裁剪最早的非关键消息，目标 50%
3. 若使用率 > 80% → 调用 LLM 将最早的消息压缩为摘要

### 缓存系统

`CacheManager` 接口（策略模式），两种实现：
- **LocalCacheManager**: Caffeine 本地缓存
- **RedisCacheManager**: Jedis 客户端，Redis 不可用时自动降级到本地缓存

`@Cacheable` 注解 + `CacheAspect` 切面实现声明式缓存。

### 事件系统

`EventBus` 为组件间松耦合通信提供支持：
- `subscribe(Class<T>, Consumer<T>)` — 按类型订阅
- `publish(T)` — 同步分发
- 使用 `CopyOnWriteArrayList` 线程安全存储订阅者

### 命令分派

CLI 斜杠命令通过 `CommandController` 分层管理：
- `CommandController` 抽象基类（按 `prefix` 分组）
- 子类在容器初始化时自动注册处理器到 `CommandParser`
- 各控制器：ProviderController、PlanController、TaskController、MemoryController、StatusController 等

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

### GitHub 集成

依赖系统安装 [GitHub CLI (`gh`)](https://cli.github.com/)。

| 工具 | 功能 |
|------|------|
| `GhPrCreate` | 创建 GitHub Pull Request（基于当前分支） |
| `GhPrList` | 列出仓库 PR（支持过滤状态/作者） |
| `GhIssueList` | 列出仓库 Issue（支持过滤状态/标签） |

共享方法 `GhCommand.execGh()` 统一执行 `gh` 命令，处理输出截断和错误报告。

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

### 当前工具总表 (26 个)

| Phase | 工具列表 |
|-------|---------|
| 1 | Read, Write, Glob |
| 2 | Edit, Grep, Bash |
| 3 | (Provider 热切换) |
| 4 | TaskCreate, TaskList, TaskUpdate, TaskGet, EnterPlanMode, ExitPlanMode |
| 5 | GitDiff, GitStatus, GitCommit, Skill, (HardDeny 安全层) |
| 6 | MemorySave, MemorySearch, HtmlOutput, Lsp |
| 7 | GhPrCreate, GhPrList, GhIssueList |
