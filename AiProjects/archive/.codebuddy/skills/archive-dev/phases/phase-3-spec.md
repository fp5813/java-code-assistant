---
name: archive-dev/phases/phase-3-spec
description: "规格澄清：读取探路报告，交互式澄清模糊点（≤5问题/轮），输出 What/Goal/Scope/AC 到规格文档。只读模式。"
version: 2.1.0
tags: [archive, codebuddy-only, spec-driven, read-only]
role: codebuddy-specifier
model: deepseek-v4-flash
tools: [Read, Grep]
references:
  - ../references/spec-driven-development.md
---

# Phase 3: 规格澄清

> "先说清楚'做什么 + 为什么'，再动手'怎么做'" — 参见 spec-driven-development.md。

## 职责

**输入**：探路报告（`docs/探路报告/`）  
**输出**：`docs/规格文档/YYYY-MM-DD-{功能简述}.md`  
**模式**：只读（不写代码）

## 流程

### Step -1: 读取工作流状态

1. Read `.codebuddy/workflow/state.yaml`，验证 `phase.current == "phase-3-spec"`
2. 验证前置制品存在：`artifacts.probe_report.path` 对应的文件存在，`artifacts.gate_2_5.status == "passed"`
3. 设置 `phase.status = "in_progress"`, `phase.started_at = 当前时间`

### Step 0: 读取探路报告文件（优先于上下文记忆）

从文件系统读取最新探路报告，确保基于完整内容而非可能已被压缩的上下文摘要：

```bash
Read docs/探路报告/INDEX.md                 # 定位最新报告
Read docs/探路报告/YYYY-MM-DD-{简述}.md      # 读取完整报告内容
```

如探路报告已被自动压缩摘要化，此步骤可恢复完整信息。

### Step 1: 定位并分析探路报告

提取：现象、根因（file:line）、触发条件、涉及文件。用 Grep 补充验证方法签名和调用链。

### Step 2: 回答 4 个问题

1. **What** — 现象？根因？（引用行号）触发条件？
2. **Goal** — 修复后效果？验收标准？
3. **Scope** — 直接修改文件？关联影响？**明确不在范围内的部分**
4. **AC** — 编号（AC01, AC02...），可验证的描述

### Step 2.5: 交互式澄清（遇模糊点时触发）

探路报告标注 `(待验证)`、范围有歧义、多个候选方案时启动。格式：

```
## 问题 {N}: {主题}
**上下文**：{引用探路发现}
**推荐**: 选项 [X] — {推荐理由}
| 选项 | 描述 | 影响 |
|------|------|------|
| A | {答案} | {影响} |
**验证**: {用户选择后，对选项中的可验证信息做 1 轮快速验证}
```
- 每轮 ≤5 个问题，按影响排序
- 提供推荐并说明理由
- 用户回复 "推荐"/"A"/"B"/"done" 均可
- 无模糊点时跳过

**用户选择验证**：用户选择方案后，对关键选择做技术可行性验证：
- 用户选择"修改表结构" → `mysql-archive` 检查表是否存在、字段类型是否匹配
- 用户选择"复用某页面模式" → Glob 确认参考页面目录是否存在
- 验证结果记录到规格文档的"假设"章节

### Step 3: 生成规格文档

输出到 `docs/规格文档/YYYY-MM-DD-{功能简述}.md`：

```
# 规格文档：{功能简述}
## 基本信息（日期、关联探路报告、类型）
## 问题描述（What）— 现象 / 根因 / 触发条件
## 修改目标（Goal）
## 影响范围（Scope）— 直接修改 / 关联 / 不在范围内
## 数据来源（涉及 DB 时必填，其他可选）
### API 接口数据（引用探路报告 VO/DTO 结构）
| VO/DTO 类 | 字段 | 类型 | 赋值来源 | 对应表/字段 |
|-----------|------|------|---------|------------|
| {VO 类名} | {字段名} | {字段类型} | {直接映射/枚举转换/字典翻译/计算派生/聚合统计} | {表名.字段} |
### 表结构（底层存储）
| 表名 | 字段 | 类型 | 备注 |
|------|------|------|------|
| {表名} | {字段名} | {数据库类型} | {字段说明} |
### 表↔VO 字段映射关系
| 表字段 | VO 字段 | 映射方式 | 转换规则 |
|--------|---------|---------|---------|
| {表名.字段} | {VO.字段} | {直接映射/类型转换/值映射} | {无/类型转换规则/枚举值对照表} |
### 数据转换逻辑
- {字段 A} → {转换规则} → {目标字段}
### 测试数据（引用探路报告中的采样结果）
```sql
-- 正常流程
SELECT * FROM {表名} WHERE {条件} LIMIT 3;
-- 边界值
SELECT DISTINCT {状态字段} FROM {表名};
```
## 验收标准（AC）
- [ ] AC01: {可验证描述}
## 澄清记录（如有交互）
## 假设（信息不足时）
```

### Step 4: 更新规格文档索引

在 `docs/规格文档/INDEX.md` 表**顶部**插入新行。

### Step 5: 更新工作流状态（Phase 出口）

1. 如有设计决策被用户确认，创建 `decisions/YYYY-MM-DD-{简述}.md`，更新 `decisions[]` 数组
2. 更新 `artifacts.spec.path`, `artifacts.spec.updated_at`
3. Phase 出口：
   - `phase.status = "completed"`, `phase.completed_at = 当前时间`
   - `progress.phases_completed.append("phase-3-spec")`
   - `phase.current = "phase-4-plan"`, `phase.status = "pending"`
   - `metrics_snapshot.phase_durations[phase-3-spec] = 耗时分钟数`
4. 更新 `session.last_activity = 当前时间`

## 自检

- [ ] 根因引用探路报告行号
- [ ] Scope 边界明确（含不在范围内）
- [ ] AC 可验证、有编号（AC01...）
- [ ] 不说 How（只聊 What/Why）
- [ ] 假设已记录
- [ ] 涉及 DB 时数据来源章节完整，包含 API 接口数据（VO/DTO）、表结构、表↔VO 字段映射关系三部分
- [ ] 测试数据引用探路报告中的采样结果
- [ ] 用户关键选择已验证技术可行性（如有交互）

## 约束

- 绝不写代码或做实现决策。所有根因引用探路报告行号。不知则标注"需补充探路"。
