# 墨码 (MoMa) 架构设计文档

> 版本: 1.0.0-SNAPSHOT | 最后更新: 2026-06-01

## 目录

1. [项目概述](#1-项目概述)
2. [四层架构](#2-四层架构)
3. [依赖注入容器](#3-依赖注入容器)
4. [Agent 循环](#4-agent-循环)
5. [工具系统](#5-工具系统)
6. [Provider 热切换](#6-provider-热切换)
7. [缓存系统](#7-缓存系统)
8. [并发框架](#8-并发框架)
9. [安全系统](#9-安全系统)
10. [技能系统](#10-技能系统)
11. [任务与计划模式](#11-任务与计划模式)
12. [记忆系统](#12-记忆系统)
13. [数据流全景](#13-数据流全景)

---

## 1. 项目概述

墨码 (MoMa) 是一个基于 **LangChain4j** 的终端 AI 编程助手，采用 **perceive-think-act** Agent 闭环架构。用户通过终端 REPL 与 AI 交互，AI 可调用 23 个工具来感知和操作代码库。

### 核心设计原则

- **零外部依赖框架**：自研 DI 容器，不依赖 Spring/Guice
- **策略模式优先**：缓存、Provider 等核心机制均采用策略模式
- **关注点分离**：四层架构（CLI → Controller → Service → Repository）
- **运行时灵活性**：Provider 热切换、Redis/本地缓存自动降级

---

## 2. 四层架构

项目严格遵循四层架构，每层职责明确，上层依赖下层。

```
┌─────────────────────────────────────────────────────────┐
│  CLI 层 (com.moma.cli)                                  │
│  CliApp / CommandParser                                 │
│  职责: 终端交互、命令分发、用户输入路由                    │
├─────────────────────────────────────────────────────────┤
│  控制器层 (com.moma.controller)                          │
│  ProviderController / PlanController / TaskController   │
│  MemoryController / StatusController                    │
│  职责: 处理 /command 斜杠命令                            │
├─────────────────────────────────────────────────────────┤
│  服务层 (com.moma.service)                              │
│  AgentService / ToolOrchestrationService                │
│  MessageService / SessionService / CacheService          │
│  职责: 业务逻辑编排                                      │
├─────────────────────────────────────────────────────────┤
│  数据访问层 (com.moma.repository)                        │
│  MemoryRepository / SessionRepository / TaskRepository  │
│  职责: 数据持久化封装                                    │
└─────────────────────────────────────────────────────────┘
```

### 2.1 CLI 层 (`cli/`)

**核心文件**:
- `CliApp.java` — JLine REPL 交互循环，`@Component` 通过 DI 容器管理
- `CommandParser.java` — 斜杠命令解析器，支持 `/command [args]` 格式

**工作流程**:
1. 启动时通过 `@PostConstruct init()` 初始化安全规则、模型和命令解析器
2. 主循环调用 `reader.readLine(PROMPT)` 获取用户输入
3. 斜杠命令 → `CommandParser.execute()` → 分发给对应 `CommandController`
4. 普通文本 → `AgentLoop.execute()` → Agent 处理
5. 每次 Agent 响应后显示统计（时间、Token、工具调用次数）

**命令注册机制**:
- `CommandParser` 持有 `Map<String, CommandHandler>` 处理器映射
- 各 `CommandController` 通过 `registerHandlers()` 将自己的处理器注册到 map
- 支持 Tab 补全：`/help`, `/clear`, `/exit`, `/status`, `/model`, `/provider`, `/sessions`, `/plan`, `/memory`

### 2.2 控制器层 (`controller/`)

| 控制器 | 命令 | 功能 |
|--------|------|------|
| `ProviderController` | `/provider`, `/model` | 列出/切换 Provider 和模型 |
| `PlanController` | `/plan`, `/plan execute` | 进入/退出计划模式 |
| `TaskController` | `/tasks`, `/tasks clear` | 查看/管理任务列表 |
| `MemoryController` | `/memory` | 查看和管理记忆 |
| `StatusController` | `/status` | 查看系统状态（模型、工具、任务等） |

所有控制器继承 `CommandController` 抽象基类，实现模板方法模式。

### 2.3 服务层 (`service/`)

| 服务 | 职责 |
|------|------|
| `AgentService` | 封装 AgentLoop 循环，提供 execute/refreshSystemPrompt 接口 |
| `ToolOrchestrationService` | 工具编排：只读工具并发执行，写工具串行执行 |
| `MessageService` | 消息历史管理，支持裁剪（保留最近 50 条） |
| `SessionService` | 会话管理 |
| `CacheService` | 缓存业务逻辑 |

### 2.4 数据访问层 (`repository/`)

| Repository | 封装的数据源 | 功能 |
|------------|------------|------|
| `MemoryRepository` | `MemoryStore` | 记忆的 CRUD |
| `SessionRepository` | `SessionManager` | 会话的持久化 |
| `TaskRepository` | `TaskManager` | 任务的持久化 |

---

## 3. 依赖注入容器

### 3.1 概述

`ApplicationContext.java` (587 行) 是一个自研的轻量级 DI 容器，不依赖任何第三方框架。整体架构如下：

```
┌─────────────────────────────────────────────┐
│  ApplicationContext                          │
│                                              │
│  beanDefinitions: Map<String, BeanDefinition>│
│  singletonBeans: Map<String, Object>         │
│  beansInCreation: Set<String>               │
│  propertySource: Map<String, String>         │
│                                              │
│  register() / registerPackage()              │
│  refresh() → 两阶段初始化                     │
│  getBean(name) / getBean(type)               │
└─────────────────────────────────────────────┘
```

### 3.2 支持的注解

| 注解 | 位置 | 功能 |
|------|------|------|
| `@Component` | 类 | 标记为组件，自动注册为 Bean |
| `@Configuration` | 类 | 标记为配置类，内含 `@Bean` 方法 |
| `@Bean` | 方法 | 在配置类中定义 Bean 创建逻辑 |
| `@Inject` | 构造器/字段/方法 | 依赖注入点 |
| `@Value("${key:default}")` | 字段/参数 | 属性占位符注入 |
| `@Primary` | 类 | 解决类型注入时的歧义 |
| `@PostConstruct` | 方法 | Bean 初始化回调 |

### 3.3 初始化流程 (refresh)

```
refresh()
  │
  ├── 阶段1: 处理配置类
  │   ├── 创建 @Configuration 类实例
  │   ├── 扫描 @Bean 方法, 注册 Bean 定义
  │   └── 调用 @Bean 方法创建实例
  │
  └── 阶段2: 创建其他单例 Bean
      ├── 遍历所有未创建的 Bean 定义
      ├── getBean() → createBeanInstance()
      │   ├── 优先使用 @Bean 工厂方法
      │   └── 否则通过构造器创建
      │       ├── 构造器注入（支持 @Inject）
      │       ├── 字段注入（@Inject）
      │       ├── setter 注入（@Inject 方法）
      │       └── @Value 注入
      └── 执行 @PostConstruct 回调
```

### 3.4 关键特性

- **循环依赖检测**: 通过 `beansInCreation` 集合跟踪当前创建链
- **@Primary 歧义解决**: 类型注入时多个候选 → 选 @Primary → 选名称匹配
- **@Value 占位符**: 支持 `${key:default}` 语法，自动类型转换
- **作用域**: 支持 `singleton` 和 `prototype`

---

## 4. Agent 循环

### 4.1 perceive-think-act 闭环

```
用户输入
    │
    ▼
┌───────────────────────────────────────────────┐
│ PERCEIVE: 收集上下文                           │
│ SystemPrompt + UserMessage + 历史消息 → messages │
└───────────────────┬───────────────────────────┘
                    │
                    ▼
┌───────────────────────────────────────────────┐
│ THINK: 调用 LLM (modelSupplier.get().generate()) │
│                                                │
│  ├── 返回 text → 输出给用户, 结束               │
│  │                                              │
│  └── 返回 tool_use → ACT                        │
└───────────────────┬───────────────────────────┘
                    │
                    ▼
┌───────────────────────────────────────────────┐
│ ACT: 执行工具调用                               │
│                                                │
│  分组: 只读(Read/Glob/Grep) → 并发执行          │
│        写(Write/Edit/Bash) → 串行执行            │
│                                                │
│  每个工具结果 → ToolExecutionResultMessage      │
│  → 追加到 messages[] → 继续 THINK              │
└───────────────────────────────────────────────┘
```

### 4.2 AgentLoop 实现 (agent/AgentLoop.java)

**关键参数**:
- `MAX_ITERATIONS = 20` — 单次用户输入最多 20 次 think-act 迭代
- `MAX_MESSAGE_COUNT = 50` — 消息历史最多保留 50 条
- `TOOL_TIMEOUT_SECONDS = 120` — 工具执行超时时间
- 使用 `Executor.newVirtualThreadPerTaskExecutor()` 虚拟线程执行器

**兼容性处理**:
- `parseJsonToolCalls()` — 兼容 Qwen 等以 JSON 文本输出工具调用（非原生 tool_calls 协议）的模型
- 支持从 ` ```json ... ``` ` 代码块或裸 JSON 中解析工具调用

**消息裁剪策略**:
- 当 `messages.size() > MAX_MESSAGE_COUNT` 时
- 从第二条消息（保留系统提示词）开始
- 找到第一个 `UserMessage`，删除它及其后 3 条消息

### 4.3 工具编排 (ToolOrchestrationService)

```
executeTools(requests)
    │
    ├── 分组: 只读 vs 写
    │   ├── ReadTool, GlobTool, GrepTool → 只读
    │   └── 其余 → 写
    │
    ├── 只读工具: 并发执行 (CompletableFuture.allOf)
    │   ├── 虚拟线程池执行
    │   └── 120 秒超时
    │
    └── 写工具: 串行执行
        └── 逐个执行，避免竞态
```

---

## 5. 工具系统

### 5.1 工具接口 (`tool/Tool.java`)

```java
public interface Tool<I, O> {
    String name();                    // 工具名（LLM 调用标识）
    String description();             // 描述（LLM 理解用途）
    String inputSchema();             // JSON Schema（参数约束）
    O execute(I input, AgentContext context);  // 执行逻辑
    boolean isReadOnly();             // 只读标志（并发安全）
    default boolean isEnabled();      // 是否启用
    I parseInput(String jsonInput);   // JSON → 输入对象
    String formatOutput(O output);    // 输出 → JSON 字符串
}
```

### 5.2 工具注册中心 (`tool/ToolRegistry.java`)

- 持有 `ConcurrentHashMap<String, Tool>`
- 集成 `HardDenyManager` 安全检查 (`getToolChecked()`)
- 提供 `getToolSpecifications()` 转换为 LangChain4j 格式
- 支持运行时注册/取消注册

### 5.3 23 个工具总表

| Phase | 工具 | 只读 | 功能 |
|-------|------|------|------|
| 1 | `ReadTool` | ✓ | 读取文件（行范围） |
| 1 | `WriteTool` | ✗ | 写入/覆盖文件 |
| 1 | `GlobTool` | ✓ | 通配符文件搜索 |
| 2 | `EditTool` | ✗ | 精准编辑（文本匹配/行号替换，自动 .bak 备份） |
| 2 | `GrepTool` | ✓ | 正则内容搜索（支持文件过滤） |
| 2 | `BashTool` | ✗ | Shell 命令执行（超时 + hard_deny） |
| 2 | `LspTool` | ✓ | LSP 诊断分析 |
| - | `HtmlOutputTool` | ✗ | HTML 报告生成 |
| 4 | `TaskCreateTool` | ✗ | 创建子任务 |
| 4 | `TaskListTool` | ✓ | 列出所有任务 |
| 4 | `TaskUpdateTool` | ✗ | 更新任务状态 |
| 4 | `TaskGetTool` | ✓ | 查看任务详情 |
| - | `EnterPlanModeTool` | ✗ | 进入计划模式 |
| - | `ExitPlanModeTool` | ✗ | 退出计划模式 |
| 5 | `GitDiffTool` | ✓ | Git 差异查看 |
| 5 | `GitStatusTool` | ✓ | Git 状态查看 |
| 5 | `GitCommitTool` | ✗ | Git 提交 |
| - | `SkillTool` | ✗ | 激活技能 |
| 6 | `MemorySaveTool` | ✗ | 保存记忆 |
| 6 | `MemorySearchTool` | ✓ | 搜索记忆 |
| - | `GhPrCreateTool` | ✗ | GitHub PR 创建 |
| 7 | `GhPrListTool` | ✓ | GitHub PR 列表 |
| 7 | `GhIssueListTool` | ✓ | GitHub Issue 列表 |

---

## 6. Provider 热切换

### 6.1 架构

```
┌─────────────────────────────────────────────────┐
│  ProviderRegistry                                │
│                                                  │
│  providers: Map<String, ModelProvider>           │
│  configs: Map<String, ModelConfig>               │
│  currentModel: ChatLanguageModel (volatile)      │
│  activeProvider / activeModel (volatile)         │
│                                                  │
│  switchTo(name, modelName) → 重建模型实例         │
│  getCurrentModel() → Supplier 每次迭代获取最新    │
└─────────────────────────────────────────────────┘
```

### 6.2 热切换机制

- `AgentLoop` 通过 `Supplier<ChatLanguageModel>` 获取模型实例
- 每次 Agent 迭代调用 `modelSupplier.get()` 获取最新模型
- `/provider <name>` 命令：
  1. 查找 Provider 实现（自动注册未注册的 Provider）
  2. 构建新配置（可指定模型名）
  3. 调用 `provider.createModel(config)` 创建新 `ChatLanguageModel`
  4. 更新 `currentModel`（volatile 保证可见性）
  5. 下一轮 Agent 迭代自动生效

### 6.3 配置加载

```json
{
  "providers": {
    "deepseek": {
      "baseUrl": "https://api.deepseek.com",
      "apiKey": "sk-...",
      "models": ["deepseek-chat", "deepseek-coder"]
    },
    "openai": {
      "baseUrl": "https://api.openai.com/v1",
      "apiKey": "sk-...",
      "models": ["gpt-4o", "gpt-4o-mini"]
    }
  }
}
```

---

## 7. 缓存系统

### 7.1 策略模式设计

```
┌─────────────────────────────────────────┐
│  CacheManager (接口)                     │
│  get() / set() / delete() / clear()     │
│  isAvailable() / getStats()             │
└──────────────────────┬──────────────────┘
                       │
           ┌───────────┴───────────┐
           │                       │
           ▼                       ▼
┌──────────────────┐    ┌──────────────────────┐
│ RedisCacheManager │    │ LocalCacheManager     │
│ (Jedis 连接池)    │    │ (ConcurrentHashMap)   │
│ 可用时优先        │    │ 惰性过期 + 清空策略   │
└──────────────────┘    └──────────────────────┘
```

### 7.2 自动降级逻辑 (CacheConfig.cacheManager())

```
1. 尝试创建 RedisCacheManager(host, port, password, timeout, maxTotal, maxIdle)
2. 若 redis.isAvailable() == true → 返回 RedisCacheManager
3. 若 Redis 连接失败 → 日志警告 + 降级到 LocalCacheManager
4. LocalCacheManager 默认: maxSize=1000, defaultTtl=300s
```

### 7.3 缓存切面 (CacheAspect)

提供 `around(key, supplier, type, ttl)` 方法，实现"先查缓存 → 未命中执行 → 结果缓存"的环绕模式。在 `CacheService` 中手动调用。

---

## 8. 并发框架

### 8.1 线程池管理器 (ThreadPoolManager)

4 个专用线程池，每个独立命名、独立配置：

| 线程池 | 核心线程 | 最大线程 | 队列 | TTL | 用途 |
|--------|---------|---------|------|-----|------|
| compute | CPU 核心数 | CPU 核心数 | SynchronousQueue | 不超时 | 计算密集型任务 |
| io | CPU×2 | CPU×4 | LinkedBlockingQueue(1000) | 60s | I/O 密集型任务 |
| cache | 4 | 8 | LinkedBlockingQueue(100) | 30s | 缓存操作 |
| event | 2 | 4 | LinkedBlockingQueue(200) | 30s | 事件处理 |

- 所有线程池使用 `CallerRunsPolicy` 饱和策略
- 线程命名格式：`<poolName>-<counter>`（如 compute-1, io-5）
- JVM Shutdown Hook 优雅关闭
- 支持运行时获取 `PoolStats`（活跃数、队列深度、完成任务数）

### 8.2 异步执行器 (AsyncExecutor)

- 封装 `ThreadPoolManager`，将普通 `Future` 包装为 `CompletableFuture`
- 支持带超时的异步任务（利用 Java 21 `orTimeout()`）
- 提供 `submit(Callable, poolName)` / `submit(Callable, poolName, Duration)` / `runAsync(Runnable, poolName)`

### 8.3 事件总线 (EventBus)

- 类型安全：按 `Class<?>` 匹配事件类型
- 双向分发：`publish()` 同步、`publishAsync()` 异步
- 线程安全：`ConcurrentHashMap` + `CopyOnWriteArrayList`
- 支持运行时订阅/取消

---

## 9. 安全系统

### 9.1 HardDenyManager

集中式安全规则引擎，在工具执行前拦截危险操作。

**规则格式**: `"ToolName(patten)"` 或 `"ToolName(*)"`

**9 条默认规则**:
| 规则 | 拦截目标 |
|------|---------|
| `Bash(rm -rf /)` | 删除根目录 |
| `Bash(rm -rf /*)` | 删除根目录（通配符） |
| `Bash(sudo rm)` | sudo 删除 |
| `Bash(dd if=/dev/zero)` | 磁盘擦写 |
| `Bash(mkfs.)` | 格式化文件系统 |
| `Bash(:(){ :\|:& };:)` | fork 炸弹 |
| `Bash(wget http://)` | HTTP 下载 |
| `Bash(curl http://)` | HTTP 下载 |
| `Bash(chmod 777)` | 权限放宽 |

### 9.2 执行流程

```
ToolRegistry.getToolChecked(name, inputJson)
    └── HardDenyManager.enforce(tool, inputJson)
        └── 遍历所有规则
            ├── 匹配 → 抛出 ToolException("安全拦截: ...")
            └── 不匹配 → 继续执行
```

---

## 10. 技能系统

### 10.1 技能模型 (Skill)

```java
public record Skill(
    String name,          // 技能名称
    String description,   // 简述
    String prompt,        // 注入到系统提示词的指引文本
    List<String> allowedTools  // 允许使用的工具白名单
)
```

### 10.2 4 个内置技能

| 技能 | 适用场景 | 允许的工具 |
|------|---------|-----------|
| `code-review` | 代码审查（质量、安全、性能） | Read, Glob, Grep, GitDiff, GitStatus |
| `test-generation` | 生成单元测试 | Read, Glob, Write, Edit |
| `refactoring` | 代码重构 | Read, Write, Edit, Glob, Grep, Bash, Git* |
| `bug-fix` | Bug 定位和修复 | Read, Glob, Grep, Edit, GitDiff, GitStatus |

### 10.3 技能激活

- Agent 通过 `SkillTool` 激活技能
- 激活后，系统提示词中注入技能指引文本
- 未允许的工具被禁用（`isEnabled()` 返回 false）

---

## 11. 任务与计划模式

### 11.1 任务模型 (Task)

```java
public record Task(
    String id,                    // UUID
    String description,           // 任务描述
    TaskStatus status,            // pending → in_progress → completed
    List<String> dependencies,    // 依赖的任务 ID 列表
    String result,                // 执行结果
    long createdAt,               // 创建时间戳
    long updatedAt                // 更新时间戳
)
```

### 11.2 任务管理器 (TaskManager)

- 支持设置任务依赖、从依赖关系推断任务顺序
- `findNextReady()` — 找出所有依赖已满足的待办任务
- 任务持久化在 `~/.ca/tasks/tasks-{sessionId}.json`

### 11.3 计划模式

- `/plan` — 进入计划模式。系统提示词中注入计划指引，引导 Agent 先输出计划、创建任务、等待确认
- `/plan execute` — 退出计划模式，开始逐步执行
- `PlanManager` 维护计划模式状态（布尔标志）
- Agent 通过 `EnterPlanModeTool` / `ExitPlanModeTool` 操作计划模式

---

## 12. 记忆系统

### 12.1 记忆模型 (MemoryEntry)

```java
public enum Type { FACT, PREFERENCE, DECISION, REFERENCE }

public class MemoryEntry {
    String id;                    // UUID
    Type type;                    // 记忆类型
    String content;               // 记忆内容
    String project;               // 关联项目
    String tags;                  // 逗号分隔的标签
    long createdAt;               // 创建时间
    long accessedAt;              // 最后访问时间（LRU 淘汰用）
}
```

### 12.2 存储

- 位置：`~/.ca/memory/entries.json`
- 上限：200 条，超出时淘汰最久未访问条目（LRU）
- 跨会话持久化
- Agent 通过 `MemorySaveTool` / `MemorySearchTool` 操作记忆

---

## 13. 数据流全景

```
启动:
  CodeAssistant.main()
    → ConfigLoader.load()          加载配置
    → ApplicationContext           初始化 DI 容器
        → DiConfig 注册所有 @Bean
        → refresh() 创建所有单例
    → CliApp.start()               启动 REPL

用户输入:
  "帮我修改这个函数"
    → CliApp (非斜杠命令)
    → AgentLoop.execute(input)
      → 第 1 次 THINK: LLM 决定调用 ReadTool 读取文件
      → ACT: ReadTool 执行 → 结果追加到消息
      → 第 2 次 THINK: LLM 决定调用 EditTool 修改
      → ACT: EditTool 执行 → 结果追加到消息
      → 第 3 次 THINK: LLM 输出总结文本
      → 返回用户

斜杠命令:
  "/provider deepseek"
    → CommandParser.execute()
    → ProviderController.handleProvider()
    → ProviderRegistry.switchTo("deepseek", null)
    → 返回切换结果
```
