---
name: archive-dev/phases/phase-6-record
description: "修改记录：输出修改记录到 docs/修改记录/，包含前后代码对比和回滚方案（不使用 git 命令）。"
version: 1.1.0
tags: [archive, codebuddy-only, record, documentation]
role: codebuddy-recorder
model: deepseek-v4-flash
tools: [Read, Grep]
references: []
---

# Phase 6: 修改记录

> 修改记录模板详见 `docs/修改记录/TEMPLATE.md`。本文件仅说明与模板的差异点。

## 职责

**输入**：修改后的代码（Phase 5 产出）  
**输出**：`docs/修改记录/YYYY-MM-DD-{功能简述}.md`（按 `TEMPLATE.md` 格式）  
**模式**：只读（除新建记录文件外）

## 流程

### Step -1: 读取工作流状态

1. Read `.codebuddy/workflow/state.yaml`，验证 `phase.current == "phase-6-record"`
2. 验证前置制品存在：`artifacts.code_changes` 非空，`artifacts.gate_3.status == "passed"`
3. 设置 `phase.status = "in_progress"`, `phase.started_at = 当前时间`

### Step 0: 上下文检查（可选）

Phase 6 生成的修改记录含完整代码 diff（20-40K tokens），是上下文增长最大的阶段。建议：
- 运行 `/context` 检查当前上下文使用率
- 如已超过 100K，执行 `/compact 保留探路摘要+AC清单+修改范围` 后继续

### Step 1: 列出所有修改

列出所有修改文件（前端 + 后端），分隔每文件内的独立修改点，记录位置（file:line）

### Step 2: 记录前后代码对比

每点记录修改前 / 修改后代码（含上下文，可直接替换）

### Step 3: 生成回滚方案

每点对应一步，格式"找到文件→定位行→替换为原始代码"，**不使用 git 命令**

### Step 4: 纳入审核结论并写入

**纳入 Phase 5.5 审核结论**：将文档代码审核结果追加到修改记录的末尾章节。写入 `docs/修改记录/YYYY-MM-DD-{功能简述}.md`。

### Step 5: 追加验证变更日志（新增）

如本次开发过程中存在 Phase 6 之后的验证→回退→修复循环，在修改记录末尾追加"验证变更日志"章节：

```markdown
## 验证变更日志

| 轮次 | 发现 | 修复 | 回退阶段 |
|------|------|------|---------|
| 1 | {用户测试发现的第一个问题} | {具体修复内容} | Phase 5 |
| 2 | {发现更复杂的问题（如再次修改无反应）} | {修复内容} | Phase 5 |
```

无验证循环时跳过此步。

### Step 6: 更新修改记录索引

在 `docs/修改记录/INDEX.md` 表**顶部**插入新行。格式：

```
| {日期} | [{简述}](./{文件名}) | {一行摘要} |
```

### Step 6: 复盘触发检查（Phase 6.7 入口）

在修改记录末尾追加"复盘触发检查"章节。快速评估是否需要进入 Phase 6.7 复盘回顾：

```markdown
---
## 复盘触发检查
| 问题 | 判定 |
|------|------|
| Q1: 是否为 BUG 修复？ | [ ] 是 / [ ] 否 |
| Q2: Phase 5.5 是否有 ⚠️ 建议项？ | [ ] 是 / [ ] 否 |
| Q3: 是否有门控失败重试（Phase 2.5/4.5/5.5）？ | [ ] 是 / [ ] 否 |
| Q4: 本次修改是否引入了新的设计模式或约定？ | [ ] 是 / [ ] 否 |
**判定**：任一"是"→ 继续 Phase 6.7 复盘回顾 | 全部"否"→ 直接进入 Phase 6.5
**触发原因**：{说明触发来源}
---
```

注意：即使四问全"否"，如果满足强制触发条件（用户要求/大规模重构/重复 BUG），也必须进入 Phase 6.7。

## 输出结构

```markdown
# 修改记录：{功能简述}
## 基本信息（日期/类型/关联文档）
## 修改详情
### 修改1：{文件名} - {功能}
**位置**：`{路径}:{行号范围}`
**修改前**：```{语言}  // 代码 ```  **修改后**：``` {语言} // 代码 ```
**原因**：{说明}
### 关键技术点（可选）
| 关键点 | 说明 |
### 回滚方案
| 序号 | 文件 | 操作 | 方法 |
|------|------|------|------|
| 1 | XxxController.java | 修改 | 见回滚：XxxController.java |
### 回滚：{文件名}
**当前代码**：``` // 修改后 ```  **应还原为**：``` // 原始 ```
**操作**：定位到第 N 行，替换为"修改前"代码。
```

## 自检

- [ ] 按 TEMPLATE.md 格式编写
- [ ] 每点有修改前/后代码对比（可复制替换）
- [ ] 所有修改文件已列出（前端+后端）
- [ ] 回滚方案覆盖每个修改点，不使用 git
- [ ] 复盘触发检查已执行（四问判定明确）
- [ ] 判定为"进入复盘"时已标注触发原因

## 约束

- 绝不修改源文件。不使用 git 命令（用"定位行→替换"方式）。精确行号。

### Phase 出口

1. 更新 `artifacts.change_record.path`, `artifacts.change_record.updated_at`
2. Phase 出口：
   - `phase.status = "completed"`, `phase.completed_at = 当前时间`
   - `progress.phases_completed.append("phase-6-record")`
   - 复盘触发时：`artifacts.retrospect.triggered = true`, `artifacts.retrospect.trigger_reason = "复盘四问触发"`, `phase.current = "phase-6.7-retrospect"`, `phase.status = "pending"`
   - 复盘未触发时：`phase.current = "phase-6.5-rule-sync"`, `phase.status = "pending"`
   - `metrics_snapshot.phase_durations[phase-6-record] = 耗时分钟数`
3. 更新 `session.last_activity = 当前时间`
