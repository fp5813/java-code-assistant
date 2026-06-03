---
name: archive-dev
description: "档案系统全栈开发流程 — 质量门控驱动的规范开发（探路→规格→计划→代码→记录）"
version: 16.12.0
tags: [archive, vue3, java, spring-boot, fullstack, codebuddy-only, spec-driven]
related_skills: [archive-code-explore, archive-test, systematic-debugging, codebase-knowledge-graph]
role: codebuddy-coder
skills:
  - archive-dev/phases/phase-1-clarify
  - archive-dev/phases/phase-2-probe
  - archive-dev/phases/phase-2.5-quality-gate
  - archive-dev/phases/phase-3-spec
  - archive-dev/phases/phase-4-plan
  - archive-dev/phases/phase-4.5-coverage-check
  - archive-dev/phases/phase-5-code
  - archive-dev/phases/phase-5.5-review
  - archive-dev/phases/phase-6-record
  - archive-dev/phases/phase-6.5-rule-sync
  - archive-dev/phases/phase-6.6-audit
  - archive-dev/phases/phase-6.7-retrospect
references:
  - references/spec-driven-development.md
  - references/codegraph-reference.md
  - references/mcp-tools-summary.md
---

# 档案系统开发（CodeBuddy 专用）

> **核心理念**：写代码前先过门控。每一步有制品、有检查、有记录，避免 AI 直接生成导致的返工和遗漏。

## 职责分工

| 角色 | 职责 |
|------|------|
| **用户/测试** | 提交 BUG 描述 / 新增功能描述（不限格式，CodeBuddy 负责澄清） |
| **CodeBuddy** | Phase 1 → 2 → 2.5 → 3 → 4 → 4.5 → 5 → 5.5 → 6 → 6.7 → 6.5（→ 6.6 阶段性执行） |

CodeBuddy 收到任务后先执行 Phase 1 描述澄清，不依赖任何外部预生成文档。

## 流程速览

| Phase | 制品 | 核心动作 | 门控 |
|-------|------|----------|------|
| **Phase 1** 描述澄清 | 澄清后描述（对话确认） | 多轮交互（≤5 问题/轮），提取 ≥3 个探路关键词 | Gate 0: 自检关键词充足 |
| **Phase 2** 代码探路 | `docs/探路报告/*.md` | 4 并行 Agent（代码/DB+API采样+VO视图/影响/文档），三层数据采样（API→VO→DB），调用链标注 file:line | — |
| **Phase 2.5** 质量门控 | 追加到探路报告 | 35 项完整性检查（含 L4.5 VO/DTO 视图 + 表↔VO 映射） | Gate 1: 通过率 100% |
| **Phase 3** 规格澄清 | `docs/规格文档/*.md` | 交互式 Q&A，输出 What/Goal/Scope/AC | — |
| **Phase 4** 实施计划 | `docs/实施计划/*.md` | 分解 T### [P] 任务，标注 AC | — |
| **Phase 4.5** 覆盖验证 | 追加到实施计划 | AC 覆盖率 / 文件覆盖率 / 术语一致性 / 数据追溯完整性 | Gate 2: 通过率 100% |
| **Phase 5** 最小修改 | 修改后的代码 | FE/BE 并行子代理，影响范围自检 | — |
| **Phase 5.5** 文档代码审核 | 即时审核结果（Phase 6 纳入记录） | 核对探路报告/规格/API文档与代码一致性，文档完整性全覆盖扫描 | Gate 3: 一致性自检 + 文档全覆盖 |
| **Phase 6** 修改记录 | `docs/修改记录/*.md` | 前后代码对比 + 回滚方案（不使用 git） | Gate 4: 完整性自检 |
| **Phase 6.5** 业务规则同步 | `docs/业务规则/*.md` | 评估并更新本次修改涉及的业务规则 | — |
| **Phase 6.6** 业务规则审计 | `docs/业务规则/审计/*.md` | 扫描 6 类业务规则模式全覆盖（状态/枚举/权限/数据过滤/前端条件/@Dict），完整审计阶段执行 | Gate 5: 6 类全覆盖 |
| **Phase 6.7** 复盘回顾 | `docs/复盘记录/*.md` | 复盘四问 → 五维分析（流程/质量/惯例/技能/根因）→ 沉淀改进项 | — |

> 各 Phase 详细指南见 `phases/phase-*.md`。

## 强制执行规则

1. **Phase 1** 必须执行描述澄清，提取 ≥3 个关键词方可进入 Phase 2
2. **Phase 2** 必须使用 MCP 工具链自行探路，生成 `docs/探路报告/`，不得跳过
3. **Phase 2 探路涉及 DB 时必做数据采样**：使用 `codegraph` 定位 Controller 层 API 接口，获取 VO/DTO 字段结构反映业务数据视图；使用 `mysql-archive.execute_query` 查询真实数据行（正常流程+边界DISTINCT）；对照表字段与 VO 字段记录映射关系。追踪数据写入链路，输出到探路报告 L4、L4.5 和测试数据样例章节
4. **Phase 2.5** 质量门控必须执行，通过率 100% 方可进入 Phase 3
5. **Phase 3** 必须读取探路报告，输出 What/Goal/Scope/AC
6. **Phase 4** 必须输出 T### [P] 标准化任务清单，每任务标注 AC
7. **Phase 4.5** 覆盖验证必须执行，AC 覆盖率 100% 方可进入 Phase 5
8. **Phase 5** 必须过影响范围自检清单，FE/BE 并行执行
9. **Phase 5.5** 必须审核探路报告/规格/API 接口文档与代码一致性，不一致必须更新
10. **Phase 6** 必须按模板输出修改记录，包含回滚方案
11. **Phase 6.5** 必须评估本次修改是否涉及业务规则，涉及则更新
12. **Phase 6.6** 必须扫描 6 类业务规则模式全覆盖，完整审计建议阶段性执行
13. 服务由 IDE 管理 — 禁止在终端中用 `mvn spring-boot:run` / `java -jar` 启动
14. 修改记录输出到 `docs/修改记录/`，按模板格式
15. **每个 Phase 完成后必须更新对应 INDEX.md**（探路报告/规格文档/实施计划/修改记录/业务规则/接口文档）
16. **Phase 5.5 审核 API 文档后**：新增接口模块时同步更新 `docs/接口文档/INDEX.md`
17. **Phase 6.5 处理过期规则**：引用源头已删除的规则标注"已废弃"并直接删除
18. **docs/只维护有效文档**：archive-dev 流程不读取的目录/文件直接删除，不保留一次性或人工文档
19. **Phase 2 探路必须覆盖全路径**：同一功能的所有 UI 入口（列表页/详情弹框/编辑弹框）和数据流封装层（onChange wrapper、componentProps 包裹、formActionType 作用域）
20. **Phase 5 修改 async 函数必须做竞态分析**：API 返回后检查值是否已变，避免旧响应覆盖新状态
21. **Phase 6 完成后必须执行"复盘触发检查"四问**：质量门控失败/审核发现缺陷/反复修改/暴露技能盲区，任一"是"则进入 Phase 6.7
22. **Phase 6.7** 复盘回顾必须在以下情况执行：质量门控失败、审核发现缺陷、反复修改同一问题、暴露技能流程盲区
23. **复盘发现必须沉淀为可验证的改进**：每条发现至少对应一项 SKILL.md 规则更新 / Phase 检查项新增 / Memory 记录 / CHANGELOG 条目变更
24. **每个 Phase 入口必须读取 `.codebuddy/workflow/state.yaml`**，确认当前阶段状态正确，如检测到未完成的工作流则进入恢复模式
25. **每个 Phase 出口必须更新 `.codebuddy/workflow/state.yaml`**，写入制品路径和门控结果，设置下一阶段为 pending
26. **Phase 3/4 有设计决策时必须记录到 `decisions/`**：用户明确选择 / Agent 推荐替代方案 / 新增设计模式时强制记录
27. **Phase 6.7 复盘时同步 Memory**：复盘五维分析中涉及项目惯例/流程改进/技术沉淀的，自动写入 `C:\Users\User\.codebuddy\projects\d-AiProjects-archive\memory\` 并更新 MEMORY.md
28. **state.yaml 禁止手动编辑**，只能通过 Phase 入口/出口协议自动更新
29. **Phase 1/3 交互式 Q&A 必须验证用户回答**：用户提供的模块名、页面路径、方法名、表名等具象信息，CodeBuddy 必须使用 Read/Grep/codegraph/mysql-archive 等工具在 1 轮内验证其真实性。验证失败时标注"(待验证)"并继续追问核实，不得直接采信无法验证的用户陈述。
30. **验证发现问题必须回退对应 Phase**：用户/测试人员在任意阶段（含 Phase 6 完成后）发现问题，按类型回退到对应阶段重新执行并更新制品，完成后重新走后续所有 Phase，不得在流程外做 ad-hoc 修复。判定规则：
    - 代码逻辑错误 → 回退 Phase 5（代码修改），重走 Phase 5.5 → Phase 6
    - 方案设计缺陷 → 回退 Phase 4（实施计划），重走 Phase 4.5 → Phase 5 → Phase 5.5 → Phase 6
    - 需求/规格遗漏 → 回退 Phase 3（规格澄清），重走 Phase 3 → Phase 4 → Phase 4.5 → Phase 5 → Phase 5.5 → Phase 6
    - 探路/根因误判 → 回退 Phase 2（代码探路），重走 Phase 2.5 → Phase 3 → Phase 4 → Phase 4.5 → Phase 5 → Phase 5.5 → Phase 6
31. **Phase 5.5 必须执行端到端场景验证**：Phase 5.5 审核通过后，必须按 Phase 3 规格的 AC 逐条模拟用户操作路径执行端到端验证。无法依赖交互的步骤标注"需人工测试"。验证通过后方可进入 Phase 6。验证发现与 AC 不符时标记"回退 Phase 5"。
32. **验证回退时必须更新 state.yaml**：回退时设置 `phase.current = "{回退 Phase ID}"`, `phase.status = "in_progress"`, `progress.phases_blocked` 追加回退原因。回退重新完成后更新所有相关制品，按正常出口协议推进。
33. **修改记录末尾追加验证变更日志**：Phase 6 生成修改记录时，如存在 Phase 6 之后的验证→回退→修复循环，在修改记录末尾追加"验证变更日志"章节，按时间顺序记录每次验证发现和修复摘要，格式：
    ```
    ## 验证变更日志
    | 轮次 | 发现 | 修复 | 回退阶段 |
    |------|------|------|---------|
    | 1 | {描述} | {修复摘要} | Phase 5 |
    ```
34. **Phase 5.5 必须执行文档完整性扫描**：Phase 5.5 审核时，必须扫描 `docs/接口文档/` 和 `docs/业务规则/` 是否覆盖了本次修改涉及的所有 Controller/API/业务规则。使用 `Grep` 搜索前端 API 调用路径和后端 Controller `@RequestMapping`，与文档逐条比对。缺失文档在当前 Phase 5.5 内补充，不得留到后续 Phase。

## 为什么需要 Phase 3（规格澄清）和 Phase 4（实施计划）？

没有规范驱动 → 靠感觉判断修改范围、无明确验收标准、遗漏场景、方向错了才返工。
有规范驱动 → 先说清楚边界、规格中定义 AC、计划阶段覆盖全部分支、规格澄清阶段纠正方向。

**质量门控（Phase 2.5 + Phase 4.5）不是可选项。**

## 原则解决层级

当设计原则之间发生冲突时，按以下优先级判定：

| 优先级 | 原则类别 | 说明 |
|--------|---------|------|
| **P0** | 安全与数据完整性 | 始终优先于其他所有原则 |
| **P1** | 用户意图与规格 AC | 用户确认的验收标准高于内部优化 |
| **P2** | 流程纪律 | 规范驱动 > 直接编码，门控不可跳过 |
| **P3** | 最小修改 | 改动范围不超出规格 Scope |
| **P4** | 代码质量 | 顺手修复/重构/代码规范 |

**常见冲突裁决**：
- **最小修改 vs 顺手修复**：除非涉及安全或导致 Phase 5.5 退回，否则不改无关代码
- **数据采样精度 vs 效率**：简单文案/配置修改可跳过多层采样，仅做文件定位；涉及业务逻辑变更时严格执行完整采样
- **门控阈值统一**：Phase 2.5（100%）和 Phase 4.5（100%）均要求完美覆盖，探路报告必须完整

## 缺失维度（已纳入流程）

以下非功能维度在开发流程中被系统性地覆盖：

| 维度 | 覆盖阶段 | 检查内容 |
|------|---------|---------|
| **安全** | Phase 5.5 BE 审核 | `@RequiresPermissions` 权限注解完整性、SQL 注入风险、认证绕过 |
| **性能** | Phase 2 探路建议 | N+1 查询检测、分页实现方式、批量操作效率 |
| **可测试性** | Phase 5 验证 | 编译通过 + 测试通过 + 配置引用一致性 |
| **向后兼容** | Phase 3 Scope | 明确声明 API/数据格式的兼容性要求 |
| **可观察性** | Phase 5.5 BE 审核 | 新增接口的日志记录、异常信息完整性 |
| **数据库迁移** | Phase 2 L3 链路 | 数据写入链路标注，确认不影响现有数据路径 |
| **依赖管理** | Phase 5 约束 | 不引入计划外新依赖 |
| **国际化** | Phase 2 探路标记 | 涉及前端文案修改时标注 i18n 键名 |

> 安全/性能/可测试性为 P0 维度，在 Phase 2.5 质量门控和 Phase 5.5 审核中作为必查项。其他维度在对应阶段按需检查。

## Agent 记忆与状态管理

archive-dev 工作流状态由 `.codebuddy/workflow/` 集中管理，取代隐式的制品存在性推断。

### 目录结构

```
.codebuddy/workflow/
├── state.yaml                   # 当前工作流状态（单一事实源）
├── state.schema.yaml            # 验证规则（Phase 入口/出口自检）
├── decisions/
│   ├── INDEX.md                 # 决策日志索引
│   └── YYYY-MM-DD-{简述}.md     # 单个决策记录
├── metrics/
│   ├── gates.yaml               # 门控通过/失败历史
│   └── phases.yaml              # Phase 耗时/重试统计
└── sessions/                    # 会话记录（自动生成）
```

### Phase 转换协议

每个 Phase 按以下协议操作 state.yaml。入口/出口协议已标准化，各 Phase 文档中仅引用本表，不再重复书写。

### 入口协议（Phase 文档 Step 0）

所有 Phase 入口均执行以下步骤（仅 Phase 1 增加初始化/恢复检测）：

```
1. Read `.codebuddy/workflow/state.yaml`
2. 验证 phase.current == "{当前 Phase ID}"
3. 验证前置制品存在（如有）
4. 设置 phase.status = "in_progress", phase.started_at = 当前时间
5. 更新 session.last_activity = 当前时间
```

### 出口协议（Phase 文档末尾）

所有 Phase 出口均执行以下步骤：

```
1. 更新 artifacts.{对应制品}：更新制品路径和时间戳
2. 更新门控结果（Gate 阶段）：status/score/failed_items
3. 设置 phase.status = "completed", phase.completed_at = 当前时间
4. progress.phases_completed.append("{当前 Phase ID}")
5. 设置 phase.current = "{下一 Phase ID}", phase.status = "pending"
   - Gate 失败时：phase.current = "{回退 Phase ID}", phases_blocked 记录原因
   - 工作流结束（Phase 6.6）：phase.current = null, phase.status = "completed"
6. metrics_snapshot.phase_durations[{当前 Phase ID}] = 耗时分钟数
7. gates_summary 自动更新（Gate 阶段）
8. 更新 session.last_activity = 当前时间
```

### Phase 转换速查

| Phase | 入口验证 | 出口制品 | 下一阶段 | Gate 回退 |
|-------|---------|---------|---------|----------|
| Phase 1 | 初始化/恢复 | task.keywords | Phase 2 | — |
| Phase 2 | current==phase-2 | probe_report.path | Phase 2.5 | — |
| Phase 2.5 | current==phase-2.5 | gate_2_5 | Phase 3 | <70% → Phase 2 |
| Phase 3 | +验证探路报告存在 | spec.path + decisions | Phase 4 | — |
| Phase 4 | +验证 spec 存在 | plan.path + decisions | Phase 4.5 | — |
| Phase 4.5 | +验证 plan 存在 | gate_4_5 | Phase 5 | 失败 → Phase 4 |
| Phase 5 | +验证 gate_4_5 通过 | code_changes[] | Phase 5.5 | — |
| Phase 5.5 | +验证 code_changes 非空 | review + gate_3 | Phase 6 | 有问题 → Phase 5 |
| Phase 6 | +验证 gate_3 通过 | change_record.path | Phase 6.7 或 Phase 6.5 | 复盘四问分支 |
| Phase 6.7 | current==phase-6.7 | retrospect.path | Phase 6.5 或结束 | — |
| Phase 6.5 | +验证 change_record 存在 | rule_sync | Phase 6.6 | — |
| Phase 6.6 | current==phase-6.6 | audit_report + gate_5 | 结束 | — |

### 决策日志触发条件

| 场景 | 必须记录 | 推荐记录 |
|------|---------|---------|
| 用户从多个选项中明确选择 | ✅ | - |
| Agent 推荐 + 用户确认的替代方案 | ✅ | - |
| 新增设计模式或组件约束 | ✅ | - |
| 标准方案（最常见） | - | ✅ |
| 纯技术实现细节 | - | ❌ |

### 复盘触发条件汇总

复盘入口由 3 个独立条件和 1 个强制规则驱动，统一判定表：

| 来源 | 触发条件 | 标记位置 | 目标 |
|------|---------|---------|------|
| Phase 2.5 质量门控 | 失败项 ≥ 1 个 | 门控输出追加 `⚠️ 需复盘` | Phase 6 Step 6 四问读取 |
| Phase 5.5 代码审核 | 代码质量 ≥ 1 个 ⚠️ 或 不一致 ≥ 2 个 | 审核输出标注 `⚠️ 需复盘` | Phase 6 Step 6 四问读取 |
| Phase 6 复盘四问 | Q1-Q4 任一"是" | 修改记录追加复盘触发章节 | Phase 6.7 入口 |
| Phase 6.6 审计 | 未归档规则 ≥ 5 条 | 审计报告标记 | 强制触发 Phase 6.7 |
| **强制执行** | BUG 修复 / 大规模重构 / 重复 BUG / 用户要求 | SKILL.md 规则 22 | 直接进入 Phase 6.7 |

## 上下文管理

archive-dev 流程多阶段连续执行，上下文窗口会随探路报告、规格文档、代码修改等内容增长。已配置自动压缩策略：

- **触发阈值**：上下文达到 130K tokens（约 13%，模型总容量 1000K）时自动压缩
- **配置方式**：`.codebuddy/.env` 中 `CODEBUDDY_AUTOCOMPACT_PCT_OVERRIDE=13`
- **预期效果**：压缩保留关键制品（探路报告摘要、AC 清单、修改记录），丢弃冗余对话历史
- **手动压缩**：当自动压缩不够及时时，可使用 `/compact 仅保留当前 Phase 制品` 手动触发
- **建议压缩时机**：
  - **Phase 5 完成后**：执行 `/compact 保留探路摘要+AC清单+修改范围`，为 Phase 6 修改记录（含代码 diff，20-40K）预留空间
  - **Phase 3 开始前**：如上下文已接近 100K，执行 `/compact 保留 Phase 1 关键词+探路报告摘要`，确保规格文档基于完整探路结果
- **重要原则**：关键制品优先从文件读取（`Read docs/...`），而非依赖上下文记忆，避免压缩导致信息丢失

## 交付物路径

```
docs/INDEX.md                          ← 文档中心入口（仅 active 制品）
docs/探路报告/INDEX.md
docs/规格文档/INDEX.md
docs/实施计划/INDEX.md
docs/修改记录/INDEX.md
docs/业务规则/INDEX.md
docs/接口文档/INDEX.md
docs/复盘记录/INDEX.md
docs/接口文档/00-全API索引.md
```
