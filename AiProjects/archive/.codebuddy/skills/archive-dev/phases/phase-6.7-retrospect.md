---
name: archive-dev/phases/phase-6.7-retrospect
description: "复盘回顾：混合触发式复盘（轻量检查+五维分析+根因分析），输出复盘文档并执行技能改进沉淀。"
version: 1.0.0
tags: [archive, codebuddy-only, review, retrospective, improvement]
role: codebuddy-recorder
model: deepseek-v4-flash
tools: [Read, Write, Edit, Grep, Agent]
references:
  - ../references/codegraph-reference.md
  - ../references/spec-driven-development.md
---

# Phase 6.7: 复盘回顾

> 代码改完了，复盘是最后一环。发现问题不追溯，下次还会犯同样错误。
>
> 混合触发：每次开发后轻量检查"复盘四问"，发现问题才输出完整复盘报告。

## 输入

- Phase 6 修改记录（`docs/修改记录/`）
- Phase 2.5 质量门控输出（如有）
- Phase 5.5 审核输出（如有）
- Phase 6.6 审计报告（如有）
- 最近复盘记录（`docs/复盘记录/`）
- Memory/feedback 历史记录

## 输出

- `docs/复盘记录/YYYY-MM-DD-{简述}.md` — 复盘文档
- `docs/复盘记录/INDEX.md` — 索引更新
- 技能文件更新（SKILL.md 规则 / Phase 检查项 / 参考文献）
- Memory/feedback 更新
- CHANGELOG 更新

## 模式

只读 + 写入（分析过程只读，输出阶段写复盘文档和技能文件）

---

## 流程

### Step 0: 读取工作流状态

1. Read `.codebuddy/workflow/state.yaml`，验证 `phase.current == "phase-6.7-retrospect"`
2. 设置 `phase.status = "in_progress"`, `phase.started_at = 当前时间`

### Step 1: 收集素材

> **重要**：所有素材优先从文件系统读取（`Read docs/...`），而非依赖上下文记忆。自动压缩可能已将对话中的制品摘要化，文件系统保留完整版本。

从以下来源收集复盘素材：

| 来源 | 什么信息 | 问什么 |
|------|---------|--------|
| Phase 6 修改记录 | 修改原因、技术难点、遇到的问题 | 为什么这次修改是必要的？ |
| Phase 2.5 门控报告 | 门控失败项 | 门控发现了什么？为什么没拦住？ |
| Phase 5.5 审核输出 | ⚠️ 建议项、不一致项 | 审核发现了什么质量问题？ |
| Phase 6.6 审计报告 | 未归档规则清单 | 审计发现了什么遗漏？ |
| 最近复盘记录 | 同类问题历史 | 是不是重复踩坑？ |
| Memory/feedback | 已沉淀的惯例和教训 | 现有沉淀是否覆盖当前场景？ |

### Step 2: 五维分析

按以下五个维度逐一分析，输出分析结果：

#### 维度一：流程缺陷

分析哪个 Phase 的流程或检查项未能预防此问题：

| 问题表现 | 应拦截 Phase | 实际拦截情况 | 改进方向 |
|---------|-------------|-------------|---------|
| {描述} | Phase N（如 Phase 2） | 未拦截 / 检查项不足 | 新增/修改检查项或流程步骤 |

#### 维度二：代码质量

分析代码中的反模式或可改进模式：

| 问题代码模式 | 正确做法 | 沉淀到 |
|------------|---------|--------|
| {反模式描述} | {推荐模式} | references/ 或 Memory |

#### 维度三：项目惯例

分析是否有新的设计决策、组件使用约束需要记录：

| 决策内容 | 约束说明 | 适用场景 |
|---------|---------|---------|
| {决策} | {约束} | {场景} |

#### 维度四：技能可用性

分析 Phase 文档自身是否有缺陷（描述不清、步骤缺失、示例不足）：

| 文档缺陷 | 影响 | 改进建议 |
|---------|------|---------|
| {描述} | {影响范围} | {改进} |

#### 维度五：根因分析（5 Why）

> 仅 BUG 修复必填此维度。其他触发类型可选。

| 轮次 | 问题 | 答案 |
|------|------|------|
| Why 1 | {问题表现} | {直接原因} |
| Why 2 | 为什么会有这个直接原因？ | {第二层原因} |
| Why 3 | ... | ... |
| Why 4 | ... | ... |
| Why 5 | 流程/技能/检查项层面的根因 | {根本原因} |

### Step 3: 生成沉淀建议

根据五维分析结果，生成具体的沉淀项：

| 类型 | 代码 | 说明 | 目标 |
|------|------|------|------|
| 流程缺陷 | **A** | 流程/规则/检查项遗漏 | SKILL.md rules / Phase 检查项 |
| 检查遗漏 | **B** | Phase 2.5/4.5/5.5 检查项不足 | 对应 Phase 的检查清单 |
| 代码模式 | **C** | 代码规范、反模式、最佳实践 | references/ 或 Memory |
| 项目惯例 | **D** | 设计决策、组件约束、约定 | Memory/feedback |
| 技能可用性 | **E** | Phase 文档可读性/完整性 | Phase 文档本身 |

### Step 4: 执行沉淀

按沉淀建议逐一执行：

#### 4.1 更新技能文件

根据沉淀项类型更新目标文件：

- **类型 A**: 在 SKILL.md 新增/修改强制执行规则，在对应 Phase 文档新增流程步骤
- **类型 B**: 在 Phase 2.5/4.5/5.5 的自检清单或检查表中新增项
- **类型 C**: 更新 references/ 下对应文件，或更新 Memory/feedback
- **类型 D**: 更新 Memory/feedback
- **类型 E**: 更新对应 Phase 文档的描述/步骤/示例

#### 4.2 输出复盘文档

按模板生成 `docs/复盘记录/YYYY-MM-DD-{简述}.md`，记录完整复盘分析。

#### 4.3 更新复盘记录索引

```markdown
| {YYYY-MM-DD} | [{简述}](./{文件名}) | {BUG修复/质量门控/代码审核/阶段复盘} | {N} | {序号（可选）} |
```

#### 4.4 更新 CHANGELOG

在 CHANGELOG 新增复盘驱动变更记录，包含：
- 版本号（递增）
- 变更类型（复盘驱动优化）
- 变更文件清单
- 复盘背景简要说明

#### 4.5 更新工作流状态（Phase 出口）

1. 如有决策日志产生，更新 `decisions[]` 数组引用
2. 更新 `artifacts.retrospect.path`, `artifacts.retrospect.triggered = true`
3. Phase 出口：
   - `phase.status = "completed"`, `phase.completed_at = 当前时间`
   - `progress.phases_completed.append("phase-6.7-retrospect")`
   - 复盘后有阶段需执行时：`phase.current = "phase-6.5-rule-sync"`, `phase.status = "pending"`
   - 复盘无后续阶段时：`phase.current = null`, `phase.status = "completed"`（工作流结束）
   - `metrics_snapshot.phase_durations[phase-6.7-retrospect] = 耗时分钟数`
4. `session.last_activity = 当前时间`

#### 4.6 Memory 同步

1. 读取复盘文档的"五维分析"结果
2. 判断哪些维度产出需要同步到 CodeBuddy MEMORY.md：
   - 项目惯例 → 创建 `C:\Users\User\.codebuddy\projects\d-AiProjects-archive\memory/feedback_{简述}.md`
   - 流程改进 → 更新对应的 Phase 文档和 SKILL.md
   - 技术沉淀 → 创建 `C:\Users\User\.codebuddy\projects\d-AiProjects-archive\memory/project_{简述}.md`
3. 更新 MEMORY.md 索引行（添加到顶部）
4. 在复盘文档中注明 "Memory 已同步: {文件列表}"

---

## 复盘类型判定表

| 触发条件 | 复盘类型 | 必填维度 | 可省略维度 |
|---------|---------|---------|-----------|
| 本次为 BUG 修复 | BUG 复盘 | 五维全部（根因必填） | 无 |
| Phase 5.5 有 ⚠️ 建议项 | 代码审核复盘 | 流程缺陷、代码质量、项目惯例 | 技能可用性（可选） |
| Phase 2.5/4.5/5.5 门控失败重试 | 质量门控复盘 | 流程缺陷、技能可用性 | 代码质量、根因 |
| 新增设计模式/约定 | 惯例沉淀复盘 | 项目惯例 | 流程缺陷、根因 |
| Phase 6.6 审计未归档≥5 | 审计驱动复盘 | 流程缺陷、代码质量、技能可用性 | 根因 |

---

## 输出模板

```markdown
## 复盘记录（Phase 6.7）
| 维度 | 分析结果 | 沉淀项 |
|------|---------|:------:|
| 流程缺陷 | {分析} | {S01} |
| 代码质量 | {分析} | {S02} |
| 项目惯例 | {分析} | {S03} |
| 技能可用性 | {分析} | {S04} |
| 根因 | {5 Why 结论（如有）} | {S05} |
```

---

## 完整性自检

- [ ] 素材收集完成（修改记录/门控/审核/审计/历史复盘）
- [ ] 五维分析完成（流程缺陷/代码质量/项目惯例/技能可用性/根因）
- [ ] 沉淀建议已生成（至少 1 项）
- [ ] 技能文件已更新（规则/检查项/步骤）
- [ ] 复盘文档已输出到 `docs/复盘记录/`
- [ ] INDEX.md 已更新
- [ ] Memory/feedback 已同步（如涉及项目惯例）
- [ ] CHANGELOG 已记录本次复盘驱动变更

## 约束

- 不修改代码（代码问题应在 Phase 5.5 退回 Phase 5）
- 复盘文档不修改，只追加（历史复盘不可变）
- 技能文件更新必须标注版本号和变更日期
- 复盘不可省略"沉淀"环节（即使无改进项，也需输出"本次无改进项"的确认）
- Memory/feedback 更新使用 `Write` 工具创建独立文件，不修改已有记忆中的内容
