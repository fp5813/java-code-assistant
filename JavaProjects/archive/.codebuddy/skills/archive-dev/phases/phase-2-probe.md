---
name: archive-dev/phases/phase-2-probe
description: "代码探路：并行子代理（codegraph + mysql-archive）探路并生成 L0-L5 结构化探路报告。只读模式。"
version: 2.1.0
tags: [archive, codebuddy-only, probe, read-only]
role: codebuddy-prober
model: deepseek-v4-flash
tools: [Read, Grep, Agent]
references:
  - ../references/codegraph-reference.md
  - ../references/kb-search-reference.md
  - ../references/mcp-tools-summary.md
mcpServers: [mysql-archive]
---

# Phase 2: 代码探路

> 由 Phase 1 提供原料。使用 **codegraph** MCP 主力探路，并行子代理提升效率。

## 职责

**输入**：Phase 1 澄清后的描述（含关键词、目标模块）  
**输出**：`docs/探路报告/YYYY-MM-DD-{功能简述}.md`  
**模式**：只读（绝不 Write/Edit 代码）

## 流程

### Step 1: 提取关键词

从输入中提取探路关键词：BUG → 异常方法名/页面路由/报错信息/表名；需求 → 功能关键词/目标模块/页面。

### Step 2: 并行子代理探路

一次性发送 4 个 Agent 调用（`run_in_background: true`），每个子代理最多 3 次工具调用。

**Agent A: 代码结构探路** — codegraph 建立 L0-L3 调用链
1. `codegraph_context(task, maxNodes=20, includeCode=true)` — 获取入口点 + 关联符号 + 关键代码
2. `codegraph_node(symbol, includeCode=true)` — 补充调用链细节 [可选]
3. `codegraph_trace(from=Controller, to=Mapper)` — 验证完整路径 [可选]

**Agent B: 数据库结构** — mysql-archive 查询表结构
1. `list_tables` — 确认表名
2. `describe_table(table="{目标表名}")` — 获取字段/类型/备注/关联

**Agent C: 影响范围 + 业务规则**
1. `codegraph_impact(symbol)` — 影响范围分析
2. `codegraph_explore(query, maxFiles=8)` — 批量获取源码
3. `codegraph_context(task="扫描 {模块} 业务逻辑模式")` — 识别 if/switch/枚举/权限等

**Agent D: 查阅项目文档**
1. Read `docs/INDEX.md` → 定位目录
2. Read `docs/探路报告/INDEX.md` → 复用同类报告
3. Read `docs/业务规则/{模块}` → 理解业务约束
4. 查最近修改记录

### Step 3: 合并结果 → 生成探路报告

输出到 `docs/探路报告/YYYY-MM-DD-{功能简述}.md`：

| 层级 | 内容 | 必填 |
|------|------|------|
| **L0** 层状导航 | 页面→API→Service→Mapper→DB 完整调用链 | ✅ |
| **L1** 页面入口 | 路由、Vue 组件、API 调用链路 | ✅ |
| **L2** API 入口 | Controller→Service→Mapper 每步精确行号 | ✅ |
| **L3** 实现类索引 | 所有相关类文件路径和行号 | ✅ |
| **L4** 数据库 | 表/字段/值域/关联（涉及 DB 时） | 按需 |
| **L5** 前端 API/组件 | API 文件 + 组件依赖清单（涉及前端时） | 按需 |
| 测试数据样例 | 正常流程 + 边界情况 | 推荐 |
| 影响范围 | codegraph_impact 分析结果 | 推荐 |

**报告不应包含**：修改建议、实施方案（Phase 3/4 的职责）。

## 完整性自检

- [ ] L0-L3 每步有文件:行号，调用链贯通
- [ ] 使用了并行子代理（至少 codegraph + mysql-archive）
- [ ] 已查阅项目文档
- [ ] 影响范围评估已包含
- [ ] 不确定处标注"(待验证)"
- [ ] 不包含修改建议

## 约束

- 绝不写代码或修改建议。每个子代理最多 3 次调用。精确行号。标注"(待验证)"。
