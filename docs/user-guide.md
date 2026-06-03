# 墨码 (MoMa) 用户手册

> 版本: 1.0.0-SNAPSHOT | 最后更新: 2026-06-01

墨码 (MoMa) 是一款终端 AI 编程助手，通过自然语言交互帮助你完成软件开发任务。

---

## 目录

1. [快速开始](#1-快速开始)
2. [配置](#2-配置)
3. [命令参考](#3-命令参考)
4. [使用示例](#4-使用示例)
5. [常见问题](#5-常见问题)

---

## 1. 快速开始

### 1.1 安装

**前提条件**：需要 JDK 21+ 和 Maven 3.8+。

```bash
# 1. 克隆项目
git clone <项目地址>
cd moma

# 2. 编译并打包
mvn package -DskipTests

# 3. 配置 API Key
cp .env.example .env
```

编辑 `.env` 文件，填入你的 API Key：

```bash
ANTHROPIC_AUTH_TOKEN=sk-your-api-key-here
ANTHROPIC_BASE_URL=https://api.deepseek.com
ANTHROPIC_MODEL=deepseek-chat
```

### 1.2 启动

```bash
java -jar target/moma.jar
```

Windows 用户也可以双击 `start.bat`。

启动后你会看到：

```
  ╔══════════════════════════════════════╗
  ║  墨码 (MoMa) v1.0.0                 ║
  ║  以 AI 为笔，挥洒自如地编写代码     ║
  ╚══════════════════════════════════════╝

  工作目录: /path/to/your/project
  Provider: openai-compatible
  模型: deepseek-chat
  输入 /help 查看帮助, /exit 退出
>
```

### 1.3 基础使用

在 `>` 提示符后输入你的需求，AI 会自动处理：

```
> 读取当前目录下的所有 Java 文件
```

输入 `/help` 查看所有可用命令。

---

## 2. 配置

### 2.1 配置源（优先级由高到低）

1. **项目根目录 `.ca/settings.json`** — 项目级配置
2. **用户主目录 `~/.claude/settings.json`** — 兼容 Claude Code
3. **项目根目录 `.env` 文件** — 环境变量文件
4. **系统环境变量** `ANTHROPIC_*` 系列变量

### 2.2 多 Provider 配置

支持多个 AI 服务商，运行时通过命令热切换：

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
    },
    "kiro": {
      "baseUrl": "https://api.kiro.dev/v1",
      "apiKey": "sk-...",
      "models": ["claude-sonnet-4-20250514", "claude-haiku-3-5-20241022"],
      "description": "Kiro AI (Claude 模型)"
    }
  }
}
```

### 2.3 配置文件示例

完整配置请参考项目根目录的 `settings.example.json`。

---

## 3. 命令参考

### 3.1 内置命令

| 命令 | 说明 |
|------|------|
| `/help` | 显示帮助信息 |
| `/clear` | 清除屏幕 |
| `/exit` 或 `/quit` | 退出程序 |

### 3.2 Provider 管理

| 命令 | 说明 |
|------|------|
| `/provider` | 列出所有可用 Provider |
| `/provider <name>` | 切换到指定 Provider |
| `/model <name>` | 在当前 Provider 内切换模型 |

示例：
```
> /provider
  可用 Provider:
    openai: OpenAI API
      Base URL: https://api.openai.com/v1
      Models: gpt-4o, gpt-4o-mini
    deepseek: DeepSeek API
      Base URL: https://api.deepseek.com
      Models: deepseek-chat, deepseek-coder

> /provider deepseek
  已切换到 Provider: deepseek, 模型: deepseek-chat
```

### 3.3 计划模式

| 命令 | 说明 |
|------|------|
| `/plan` | 进入计划模式，Agent 先输出计划再执行 |
| `/plan execute` | 退出计划模式，开始执行 |

计划模式下，Agent 会：
1. 输出详细的执行计划
2. 为每个步骤创建任务
3. 等待用户确认
4. 逐步执行并更新任务状态

### 3.4 任务管理

| 命令 | 说明 |
|------|------|
| `/tasks` | 显示所有任务及其状态 |
| `/tasks clear` | 清除所有任务 |

### 3.5 系统信息

| 命令 | 说明 |
|------|------|
| `/status` | 显示系统状态（当前 Provider/模型、工具数量、任务数等） |
| `/sessions` | 显示历史会话列表 |
| `/memory` | 查看和管理记忆 |

---

## 4. 使用示例

### 4.1 代码阅读

```
> 读取 src/main/java/com/moma/CodeAssistant.java 的前 20 行
```

### 4.2 代码修改

```
> 给 UserService 类添加一个 findById 方法，参数为 Long id，返回 Optional<User>
```

### 4.3 文件搜索

```
> 搜索项目中所有使用了 @Component 注解的类
```

### 4.4 Git 操作

```
> 显示当前 git 状态
> 查看最近的 5 次提交差异
> 提交所有修改，消息为"修复登录 bug"
```

### 4.5 代码审查

```
> 帮我审查 src/main/java/com/moma/agent/AgentLoop.java 的代码质量
```

### 4.6 生成测试

```
> 为 ToolRegistry 类生成单元测试
```

### 4.7 使用计划模式处理复杂任务

```
> /plan
> 我需要：
   1. 读取 UserService.java 了解当前实现
   2. 添加一个分页查询方法
   3. 编写对应的单元测试
```

### 4.8 切换模型

```
> /model deepseek-coder  # 切换到 DeepSeek Coder 模型
> /provider openai       # 切换到 OpenAI Provider
> /model gpt-4o-mini     # 使用更经济的模型
```

---

## 5. 常见问题

### 5.1 "错误: 未配置 API Key"

```
╔══════════════════════════════════════════╗
║  错误: 未配置 API Key                    ║
╠══════════════════════════════════════════╣
║  请执行以下步骤:                          ║
║  1. cp .env.example .env                 ║
║  2. 编辑 .env 填入 API Key                ║
║  或: cp settings.example.json .ca/settings.json ║
╚══════════════════════════════════════════╝
```

请按照提示配置 API Key。

### 5.2 "错误: 模型未配置"

说明当前未选择任何 AI 模型。请确保：
1. 已正确配置 API Key
2. 使用 `/provider` 命令选择可用的 Provider

### 5.3 "AI 模型调用失败"

可能原因：
- API Key 无效
- 网络连接问题
- 选择的模型不可用

尝试：
1. ` /status` 检查当前 Provider 配置
2. 使用 `/provider` 切换到其他 Provider

### 5.4 计划模式下 Agent 行为异常

- 使用 `/plan` 进入计划模式后，请确保描述清楚你的需求
- Agent 输出计划后，等待其创建任务并确认
- 使用 `/plan execute` 让 Agent 开始执行

### 5.5 如何退出

- 输入 `/exit` 或 `/quit`
- 按 `Ctrl+D` (EOF)
- 按 `Ctrl+C` 中断当前操作（不会退出程序）

### 5.6 Token 统计说明

每次 Agent 响应后会显示统计信息：
```
⏱ 12.5s  | 🔤 1520in / 340out  | 🛠 5 次工具调用
```
- `⏱` — 总耗时（秒）
- `in/out` — 输入/输出 Token 数
- `🛠` — 工具调用次数
