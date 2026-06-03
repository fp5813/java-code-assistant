---
name: fullstack-flow/phases/phase-1-clarify
description: "描述澄清：探路前交互式 Q&A，确保可提取 ≥3 个具象关键词，避免模糊描述浪费探路资源"
version: 1.1.0
tags: [fullstack, codebuddy-only, clarify, read-only]
role: codebuddy-clarifier
model: deepseek-v4-flash
tools: [Read, Grep, Agent]
---

# Phase 1: 描述澄清

> 探路效果取决于输入质量。模糊的描述 → 浪费探路资源。花 2-5 个问题确保原料充足。

## 职责

**输入**：用户提供的 BUG/功能描述（不限格式）  
**输出**：澄清后完整描述（可提取 ≥3 个关键词用于探路）  
**模式**：交互式 Q&A，多轮交互，每次 ≤ 5 个问题

## 流程

### Step 0: 初始化工作流状态

1. 检查 `.codebuddy/workflow/state.yaml` 是否存在
2. 如果不存在 → 按模板初始化（设置 `session.id`, `task.id` 为当前日期，`workflow.created_at`=当前时间）
3. 如果存在且 `phase.status == in_progress` → 进入恢复模式：
   - 输出恢复摘要（任务名、当前 Phase、已完成 Phase、制品状态）
   - 检查当前 Phase 的前置制品是否存在，缺失时询问用户
4. 设置 `phase.current = "phase-1-clarify"`, `phase.status = "in_progress"`
5. 更新 `session.last_activity = 当前时间`

### Step 1: 评估描述质量

判断是否满足探路最低要求：模块名或功能领域 + ≥3 个具象关键词（方法名/表名/报错/路由）+ 有复现步骤或目标效果描述。

### Step 2: 交互式澄清

优先问能提供**具象关键词**的问题：

| 场景 | 缺失信息 | 推荐问题 |
|------|---------|---------|
| BUG | 模块/页面 | "在哪个模块/页面出现？" |
| BUG | 具体方法 | "报错接口或方法？有无错误日志？" |
| BUG | 复现步骤 | "稳定复现吗？步骤是？" |
| 新功能 | 目标模块 | "属于哪个模块？" |
| 新功能 | 核心实体 | "操作对象？如全宗、案卷" |
| 新功能 | 参考页面 | "有类似页面可参考吗？" |

不讨论"如何修改"（那是 Phase 3/4 的职责），不问技术细节。

### Step 2.5: 验证用户回答（新增）

每次用户回答后，对其中**可验证的具象信息**执行快速验证：

| 信息类型 | 验证方式 | 示例 |
|---------|---------|------|
| 模块/页面路径 | `Glob` 匹配 `src/views/archive/{模块}/` | 用户说"门类管理页面" → Glob 确认 `src/views/archive/system/archiveCategory/` 存在 |
| 方法名/API 路径 | `Grep` 或 `codegraph` 搜索 Controller | 用户说"报错接口是 /system/xxx/list" → Grep Controller 层确认 |
| 表名 | `mysql-archive` 查询表结构，或 Grep Entity 注解 | 用户说"表名是 archive_fonds" → 检查对应的 Entity 或数据库表 |
| 报错信息/日志 | 存疑时 → 标注"(待验证)" | 用户说的报错格式与常见错误不一致时标记 |
| 业务流程 | 存疑时 → 标注"(待验证)" | 与代码现有逻辑矛盾时标记 |

**轻量原则**：
- 每条验证使用 1 个 Grep/Glob 调用即可，不启动探路级别的大规模搜索
- 验证失败（无法找到对应代码/路径）时标注"(待验证)"并追问："我查到 {实际信息}，您确认是 {用户说的} 吗？"
- **一轮连续验证**：用户一次回答中的多条信息，在同一回合内全部验证完，不拖到下一轮提问
- 验证过程对用户透明，不打断对话节奏

### Step 3: 输出澄清结果

```
---
**Phase 1 澄清摘要**

原始描述：{用户原始输入}
澄清后描述：{可用于 codegraph_context 的完整描述}
探路关键词：{≥3 个关键词}
目标模块：{模块名}
---
```

### Step 4: 更新工作流状态

1. 写入 state.yaml：
   - `task.brief`, `task.type`, `task.keywords`, `task.source` — 填入澄清结果
   - `progress.current_step = "Step 3: 输出澄清结果"`
   - `session.last_activity = 当前时间`
2. Phase 出口：
   - `phase.status = "completed"`, `phase.completed_at = 当前时间`
   - `progress.phases_completed.append("phase-1-clarify")`
   - `phase.current = "phase-2-probe"`, `phase.status = "pending"`
   - `metrics_snapshot.phase_durations[phase-1-clarify] = 耗时分钟数`
   - `metrics_snapshot.total_duration_min = 累加值`

## 持久化

Phase 1 输出（澄清摘要）通过对话传递给 Phase 2，Phase 2 生成探路报告时在报告顶部包含 `## 澄清摘要` 章节（`docs/探路报告/YYYY-MM-DD-{简述}.md`）。

## 自检

- [ ] 澄清后 ≥3 个具象关键词
- [ ] 模块/页面名已明确
- [ ] BUG 有复现步骤/报错，功能有目标描述
- [ ] 每轮提问 ≤ 5 个
- [ ] 用户回答中的具象信息已验证（通过 Read/Grep/codegraph/mysql-archive）
- [ ] 未讨论"怎么修"

## 约束

- 每轮最多 5 个问题，不足则基于已有信息探路并标注"(待验证)"
- 不问"如何修改"和技术细节
- 能 1 个问题问清楚的不问第 2 个
