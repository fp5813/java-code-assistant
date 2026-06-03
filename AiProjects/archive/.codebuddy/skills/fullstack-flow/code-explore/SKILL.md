---
name: fullstack-flow/code-explore
description: 代码探路（fullstack-flow 子技能）— 只读分析 BUG 或需求，不修改任何文件。使用 MCP 工具链（codegraph + mysql-archive）调查代码结构、调用链、数据库表结构。建议并行启动多个子代理提升效率。
allowed-tools:
  - Read
  - Grep
  - Glob
  - Agent
user-invocable: false
---

# 代码探路

自动提取关键词进行只读代码分析。

## 关键词提取策略

!`echo "◆ 最近BUG报告:" && ls -t bug-reports/BUG-*.md 2>/dev/null | head -1`

!`echo "◆ 最近修改记录:" && ls -t "docs/修改记录"/*.md 2>/dev/null | head -1`

从以下来源自动提取关键词（按优先级）：
1. **对话上下文** — 用户刚提到的 BUG 描述、功能需求、报错信息
2. **最新 BUG 报告** — `bug-reports/` 目录最近的文件
3. **最近修改记录** — `docs/修改记录/` 目录最近的记录

历史记录由主流程 Step 1 直接检查（`ls docs/修改记录/` + Read），不作为子代理任务。

## 并行探路流程

> ⭐ **推荐**：使用 Agent 工具并行启动 4 个子代理，分别执行不同探路任务，然后合并结果。

```
Main Agent (提取关键词)
  ├── Agent A: 代码结构 → codegraph_context + codegraph_node + codegraph_trace
  ├── Agent B: 数据库结构 → mysql-archive describe_table
  ├── Agent C: 影响分析 → codegraph_impact + codegraph_explore
  └── Agent D: 项目文档 → Read docs/INDEX.md + 探路报告/业务规则/修改记录
          ↓ 并行执行
Main Agent (合并结果)
```

### Agent A: 代码结构探路

```
1. codegraph_context(task="{BUG/功能描述}", maxNodes=20, includeCode=true)
   → 一次性获取：入口点 + 关联符号 + 关键代码片段

2. codegraph_node(symbol="{关键方法}", includeCode=true)
   → 获取单符号的 trail（调用者/被调用者）+ 完整源码

3. codegraph_trace(from="{Controller}", to="{Mapper}")
   → 验证 Controller → Service → Mapper 完整路径
```

### Agent B: 数据库结构查询

```
1. mysql-archive list_tables
   → 列出所有表，确认目标表名准确

2. mysql-archive describe_table table="{目标表名}"
   → 获取完整表结构：字段名、类型、NULL、默认值、备注
```

### Agent C: 影响范围分析

```
1. codegraph_impact(symbol="{关键类/方法名}")
   → 分析影响范围

2. codegraph_explore(query="{相关符号名}", maxFiles=8)
   → 批量获取多个相关符号源码
```

### Agent D: 查阅项目文档

```
1. Read docs/INDEX.md
   → 定位相关文档目录

2. Read docs/探路报告/INDEX.md
   → 复用同类探路报告

3. Read docs/业务规则/{模块}/
   → 理解相关业务约束

4. Read docs/修改记录/ 最近记录
   → 了解最近相关修改
```

## 串行探路流程（备选）

当并行子代理不合适时（如简单问题），也可以串行执行：

```
1. codegraph_context(task="{描述}", includeCode=true)     ← 首要工具
2. mysql-archive describe_table table="{表名}"             ← 查表结构
3. codegraph_node(symbol="{入口}", includeCode=true)      ← 深入关键节点
```

## 输出格式

```markdown
### 关键文件
- path/to/File.java:行号 — 说明
- path/to/Component.vue:行号 — 说明

### 调用链
A → B → C (Controller → Service → DAO)

### 数据库结构
| 表名 | 字段 | 类型 | 备注 |
|------|------|------|------|

### 影响范围评估
- 🔴 高风险：涉及公共组件、多模块 API
- 🟡 中风险：局部 Service 方法
- 🟢 低风险：仅单文件内部逻辑
```
