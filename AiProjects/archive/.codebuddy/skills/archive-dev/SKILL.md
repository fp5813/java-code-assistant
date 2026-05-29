---
name: archive-dev
description: "档案系统全栈开发流程 — 质量门控驱动的规范开发（探路→规格→计划→代码→记录）"
version: 16.7.0
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
  - references/kb-search-reference.md
  - references/mcp-tools-summary.md
  - references/change-record-detailed.md
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
| **Phase 2** 代码探路 | `docs/探路报告/*.md` | 4 并行 Agent（代码/DB/影响/文档），调用链标注 file:line | — |
| **Phase 2.5** 质量门控 | 追加到探路报告 | 30 项完整性检查 | Gate 1: 通过率 ≥90% |
| **Phase 3** 规格澄清 | `docs/规格文档/*.md` | 交互式 Q&A，输出 What/Goal/Scope/AC | — |
| **Phase 4** 实施计划 | `docs/实施计划/*.md` | 分解 T### [P] 任务，标注 AC | — |
| **Phase 4.5** 覆盖验证 | 追加到实施计划 | AC 覆盖率 / 文件覆盖率 / 术语一致性 | Gate 2: AC 100% |
| **Phase 5** 最小修改 | 修改后的代码 | FE/BE 并行子代理，影响范围自检 | — |
| **Phase 5.5** 文档代码审核 | 即时审核结果（Phase 6 纳入记录） | 核对探路报告/规格/API文档与代码一致性 | Gate 3: 一致性自检 |
| **Phase 6** 修改记录 | `docs/修改记录/*.md` | 前后代码对比 + 回滚方案（不使用 git） | Gate 4: 完整性自检 |
| **Phase 6.5** 业务规则同步 | `docs/业务规则/*.md` | 评估并更新本次修改涉及的业务规则 | — |
| **Phase 6.6** 业务规则审计 | `docs/业务规则/审计/*.md` | 扫描 ≥3 类业务规则模式（状态/枚举/权限），完整审计阶段执行 | Gate 5: ≥3 类模式 |
| **Phase 6.7** 复盘回顾 | `docs/复盘记录/*.md` | 复盘四问 → 五维分析（流程/质量/惯例/技能/根因）→ 沉淀改进项 | — |

> 各 Phase 详细指南见 `phases/phase-*.md`。

## 强制执行规则

1. **Phase 1** 必须执行描述澄清，提取 ≥3 个关键词方可进入 Phase 2
2. **Phase 2** 必须使用 MCP 工具链自行探路，生成 `docs/探路报告/`，不得跳过
3. **Phase 2.5** 质量门控必须执行，通过率 ≥90% 方可进入 Phase 3
4. **Phase 3** 必须读取探路报告，输出 What/Goal/Scope/AC
5. **Phase 4** 必须输出 T### [P] 标准化任务清单，每任务标注 AC
6. **Phase 4.5** 覆盖验证必须执行，AC 覆盖率 100% 方可进入 Phase 5
7. **Phase 5** 必须过影响范围自检清单，FE/BE 并行执行
8. **Phase 5.5** 必须审核探路报告/规格/API 接口文档与代码一致性，不一致必须更新
9. **Phase 6** 必须按模板输出修改记录，包含回滚方案
10. **Phase 6.5** 必须评估本次修改是否涉及业务规则，涉及则更新
11. **Phase 6.6** 必须扫描 ≥3 类业务规则模式，完整审计建议阶段性执行
12. 服务由 IDE 管理 — 禁止在终端中用 `mvn spring-boot:run` / `java -jar` 启动
13. 修改记录输出到 `docs/修改记录/`，按模板格式
14. **每个 Phase 完成后必须更新对应 INDEX.md**（探路报告/规格文档/实施计划/修改记录/业务规则/接口文档）
15. **Phase 5.5 审核 API 文档后**：新增接口模块时同步更新 `docs/接口文档/INDEX.md`
16. **Phase 6.5 处理过期规则**：引用源头已删除的规则标注"已废弃"并直接删除
17. **docs/只维护有效文档**：archive-dev 流程不读取的目录/文件直接删除，不保留一次性或人工文档
18. **Phase 2 探路必须覆盖全路径**：同一功能的所有 UI 入口（列表页/详情弹框/编辑弹框）和数据流封装层（onChange wrapper、componentProps 包裹、formActionType 作用域）
19. **Phase 5 修改 async 函数必须做竞态分析**：API 返回后检查值是否已变，避免旧响应覆盖新状态
20. **Phase 6 完成后必须执行"复盘触发检查"四问**：质量门控失败/审核发现缺陷/反复修改/暴露技能盲区，任一"是"则进入 Phase 6.7
21. **Phase 6.7** 复盘回顾必须在以下情况执行：质量门控失败、审核发现缺陷、反复修改同一问题、暴露技能流程盲区
22. **复盘发现必须沉淀为可验证的改进**：每条发现至少对应一项 SKILL.md 规则更新 / Phase 检查项新增 / Memory 记录 / CHANGELOG 条目变更

## 为什么需要 Phase 3（规格澄清）和 Phase 4（实施计划）？

没有规范驱动 → 靠感觉判断修改范围、无明确验收标准、遗漏场景、方向错了才返工。
有规范驱动 → 先说清楚边界、规格中定义 AC、计划阶段覆盖全部分支、规格澄清阶段纠正方向。

**质量门控（Phase 2.5 + Phase 4.5）不是可选项。**

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
docs/知识库/topics/00-全API索引.md
```
