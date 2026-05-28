# archive-dev 优化记录

## [16.2.0] - 2026-05-28

### 精简 — SKILL.md 主技能文件（350行 → 170行）

- **SKILL.md**：v16.1.0 → v16.2.0
  - 删除重复内容：流程图+职责表+门控表合并为一个表格
  - 删除"常见违规"章节（与强制执行规则 90% 重复）
  - 删除每个 Phase 的详细描述（已有子技能文件，主文件仅做概览引用）
  - 删除"为什么需要 Phase 3 和 Phase 4?"对比大表（缩为 2 句对比）
  - Description 从 18 字枚举缩为一句："质量门控驱动的规范开发"
  - 整体约 50% 精简

### 精简 — Phase 子技能同步压缩（2258行 → 808行）

| 文件 | 变化 |
|------|------|
| `phase-2-probe.md` | 458→84行：删除 4×Agent 返回格式模板（与输出模板重复）+ 完整 135 行输出模板 |
| `phase-3-spec.md` | 216→79行：完整输出模板缩为 inline 大纲 |
| `phase-4-plan.md` | 178→73行：同上 |
| `phase-4.5-coverage-check.md` | 218→65行：6 维检查合并精简 |
| `phase-5-code.md` | 234→67行：删除 FE/BE 子代理重复描述和验证命令表 |
| `phase-5.5-review.md` | 145→69行：3 组核对表合并 |
| `phase-6-record.md` | 200→60行：模板章节重复消除（已有 TEMPLATE.md） |
| `phase-6.6-audit.md` | 156→63行：6 类扫描模式描述压缩 |
| `phase-6.5-rule-sync.md` | 164→71行：判断标准表合并 |
| `phase-2.5-quality-gate.md` | 182→110行：27项检查表格式压缩 |
| `phase-1-clarify.md` | 107→67行：示例和问题库压缩 |

### 清理 — References 目录

- 删除已弃用的 `graphify-reference.md`（由 codegraph 替代）
- 删除已弃用的 `lexis-commands.md`（由 codegraph 替代）
- 清理 `change-record-detailed.md` 末尾残留的 watchdog 注释
- 清理 `mcp-tools-summary.md` 中的 ~~lexis~~/~~graphify~~ 引用行
- SKILL.md frontmatter 同步移除已弃用文件引用

### 背景

- SKILL.md 和各子技能文件经过多轮迭代累积了大量冗余内容
- 复用 Speckit 精简模式：主文件做骨架概览，子文件做操作指南，不留重复
- 删除已弃用工具文档，减少 CodeBuddy 每次加载的 token 消耗

---

## [16.1.0] - 2026-05-28

### 新增 — API接口文档审核纳入Phase 5.5

- **Phase 5.5 文档代码审核增强**：`phases/phase-5.5-review.md`
  - Step 1.2 新增定位API接口文档子步骤：检查 `docs/接口文档/` 中是否有对应模块的接口文档
  - Step 2.3 新增API接口文档核对表：接口路径/请求方法/参数/响应格式/权限注解/接口完整性六项核对
  - 输出模板增加API接口文档审核项（状态：一致/已更新/已新增）
  - 自检清单增加API接口文档相关检查项
  - 约束新增API接口文档缺失必须补充、API索引同步更新

- **Phase 2 探路增强**：`phases/phase-2-probe.md`
  - Agent D 查阅项目文档范围增加 `接口文档/` 目录

- **SKILL.md 同步更新**：v16.0.0 → v16.1.0
  - 主流程 Phase 5.5 描述增加"API接口文档"
  - Phase 5.5 核对项表格增加"API接口文档"行
  - 强制执行规则新增 9.1：API接口文档审核子规则
  - 常见违规新增"API接口文档与代码不一致未更新"

### 背景
- 需求：审核接口文档与代码是否一致，及时更新补充接口文档
- 已有 `docs/接口文档/` 目录（6个模块文档）+ `docs/知识库/topics/00-全API索引.md` 全量索引
- 需要在每次代码修改后审核API接口文档一致性，确保文档不落后于代码

---

## [10.1.0] - 2026-05-25

### 新增 — 业务规则同步机制

- **Phase 6.5 业务规则同步**：`phases/phase-6.5-rule-sync.md`
  - 每次修改后评估是否需要沉淀业务规则到 `docs/业务规则/`
  - 按模块分类（collect、common 等），每个模块独立规则文件
  - 公共组件/通用接口单独存放于 `common/` 目录
  - 不涉及规则时在修改记录中注明"不涉及业务规则变更"

- **docs/业务规则/ 目录**（第一期）
  - `collect/` — 组卷业务规则、移交业务规则、档案状态流转规则
  - `common/` — 弹窗/表单/表格组件规则、API调用规范、权限注解、事务边界、状态字典

### 变更 — 现有文件更新

- `SKILL.md`：v10.0.0 → v10.1.0，主流程+规则+交付物路径增加 Phase 6.5
- `phase-6-record.md`：向量库同步从"修改记录为主"改为"业务规则 mandatory + 修改记录 optional"

---

### 新增 — Speckit 理念融合优化

- **Phase 2.5 质量门控**：`phases/phase-2.5-quality-gate.md`
  - 21 项探路报告质量检查（L0-L5完整性、文件精确性、工具使用规范性）
  - 门控规则：≥90% 通过 / 70-89% 警告 / <70% 不通过
  - 自适应检查项（根据 Bug/前端/后端/DB/配置 跳过不适用项）
  - 参考 Speckit Checklist 理念："Checklist = 需求编写的单元测试"

- **Phase 4.5 覆盖验证**：`phases/phase-4.5-coverage-check.md`
  - AC 覆盖率检查（每个验收标准必须被至少一个任务覆盖）
  - 文件覆盖率检查（规格中的每个直接修改文件必须有对应任务）
  - 孤儿任务检测、术语一致性检查、不在范围违规检查
  - 门控规则：AC覆盖率 100% → 进入 Phase 5
  - 参考 Speckit Analyze 理念：跨制品一致性分析

### 增强 — 已有阶段升级

- **Phase 3 规格澄清** (v2.0.0)：`phases/phase-3-spec.md`
  - 新增 Step 3.5 交互式澄清模式（参考 `speckit.clarify`）
  - 结构化 Q&A 格式：推荐锚定 + 选项表格
  - 最多 3 个澄清问题，按影响排序
  - 新增澄清记录章节到规格文档
  - AC 编号标准化（AC01, AC02...）便于 Phase 4.5 追踪
  - 新增假设记录章节

- **Phase 4 实施计划** (v2.0.0)：`phases/phase-4-plan.md`
  - 标准化任务格式：`T### [P] 任务描述 — \`path/to/File\`  [AC01]`
  - 新增 [P] 并行标记（参考 `speckit.tasks`）
  - 新增 AC 覆盖映射到任务清单
  - 更新模板增加 AC 覆盖验证表

### 变更 — SKILL.md 重构

- 版本号 9.2.0 → 10.0.0（MAJOR：新增质量门控架构）
- 主流程增加 Phase 2.5 和 Phase 4.5
- 强制执行规则从 8 条增加到 10 条
- 新增质量门控设计表
- Phase 2/3/4 内容精简为概要型（详细内容委托给子技能）
- 标题从 "参考 Spec-Kit SDD 范式" → "参考 Speckit SDD 范式 + 质量门控"

### Speckit 学习要点采纳

| Speckit 特性 | 如何在 archive-dev 中实现 |
|--------------|-------------------------|
| Checklist 作为 "需求编写的单元测试" | Phase 2.5 检查探路报告质量 |
| 交互式 Clarify 推荐锚定 | Phase 3 Step 3.5 结构化 Q&A |
| 标准化任务 ID + [P] 并行标记 | Phase 4 T### [P] 格式 |
| 跨制品一致性分析 | Phase 4.5 覆盖验证 |
| 门控机制 | Phase 2.5 / 4.5 通过率阈值 |
| 摘要型主入口委托详细子技能 | SKILL.md 精简为概要 |

### 变更文件

- `SKILL.md`：frontmatter、主流程、规则、Phase 概述全部更新
- `phases/phase-3-spec.md`：v2.0.0 — 交互式澄清 + AC 编号
- `phases/phase-4-plan.md`：v2.0.0 — 标准化任务格式 + AC 映射
- `phases/phase-2.5-quality-gate.md`：新建
- `phases/phase-4.5-coverage-check.md`：新建
- `CHANGELOG.md`：本条目

---

### 重构
- **Phase 2 自探路**：CodeBuddy 不再依赖 Hermes 预先生成探路报告
  - Phase 2 从"审核探路报告"变为"代码探路 + 生成探路报告"
  - 探路工具链：lexis（精确定位）+ kb-search（语义搜索）+ graphify（调用链）+ Read/Grep（补充验证）
  - 输出格式遵循 `探路索引模板.md` 的 L0-L5 层级结构
- **职责边界调整**：Hermes 负责 Web 测试 → BUG 单，CodeBuddy 负责 Phase 2-6 全部环节
- **新技能文件**：`phases/phase-2-probe.md` — 只读探路技能（deepseek-v4-flash）
- **移除依赖**：不再需要 `archive-code-probe` 技能和 Hermes 探路环节

### 变更的文件
- `SKILL.md`：frontmatter、职责边界、主流程、强制执行规则、Phase 2 章节全部重写
- `phases/phase-2-probe.md`：新建 Phase 2 探路技能文件

### 背景
- Hermes 的探路报告生成环节不在 CodeBuddy 控制范围
- CodeBuddy 需要自给自足，自行完成代码定位和报告生成

---

## [9.1.0-cb] - 2026-05-22

### 新增
- **Phase 3 技能实现**：`phases/phase-3-spec.md` — 规格澄清技能
  - 只读模式（Read + Grep），deepseek-v4-flash 模型
  - 读取探路报告 → 输出规格文档（What/Goal/Scope/Acceptance Criteria）
  - 4 步流程：定位探路报告 → 分析 → 澄清4问 → 生成文档
  - 输出路径：`docs/规格文档/YYYY-MM-DD-{简述}.md`
- **Phase 4 技能实现**：`phases/phase-4-plan.md` — 实施计划技能
  - 只读模式（Read + Grep + Agent），deepseek-v4-flash 模型
  - 读取规格文档 → 任务分解 → 排序 → 风险评估 → 生成计划
  - 6 步流程：定位规格 → 理解 → 分解 → 排序 → 评估风险 → 生成计划
  - 输出路径：`docs/实施计划/YYYY-MM-DD-{简述}.md`
- **交付物目录**：创建 `docs/规格文档/`, `docs/实施计划/`, `docs/探路报告/` 目录

### 修复
- **frontmatter 引用清理**：移除 6 个不存在的 reference 文件引用
  - 原：probe-report-protocol.md, skills-audit.md, hermes-codebuddy-sync.md 等
  - 现：spec-driven-development.md, kb-search-reference.md, lexis-commands.md, mcp-tools-summary.md, change-record-detailed.md, graphify-reference.md

### 背景
- Phase 3 和 Phase 4 在 v9.0.0-cb 中被声明为 mandatory 但缺少技能实现文件
- 此次补全使 6 阶段规范化开发流程（Phase 2→3→4→5→6）可完整执行

---

## [4.0.0] - 2026-05-10

### 新增
- **TDD 工作流（强制执行）**：RED-GREEN-REFACTOR 循环
  - 铁律：无失败测试，不写生产代码
  - 6 阶段流程：RED写失败测试 → 验证RED → GREEN最小实现 → 验证GREEN → REFACTOR → 覆盖率验证
  - 明确每个阶段的验证命令

### 新增
- **Rationalization 检测（红线）**
  - 列出 6 类自我欺骗想法及真相
  - 发现即停止并重启 TDD 循环

### 新增
- **TDD 合规性检查清单**
  - 6 项必须确认事项
  - 任一未满足 = 跳过 TDD，需重启

### 新增
- **TDD 循环流程图**
  - ASCII 流程图可视化

### 新增
- **TDD 验证命令**
  - 前端 RED/GREEN 验证命令
  - 前端覆盖率命令
  - 后端 RED/GREEN 验证命令
  - 后端覆盖率命令

### 更新
- 典型遗漏场景：增加"TDD 跳过"行

### 背景
- 对比 Superpowers TDD 工作流，识别档案技能缺失 RED-GREEN-REFACTOR 闭环
- 参照 test-driven-development 技能的完整 TDD 流程

---

## [3.2.0] - 2026-05-09

### 新增
- **主技能**：`business module index` 业务模块索引表
  - 9个业务域的前后端对应关系
  - 快速定位规则（需求关键词 → 代码位置）

### 新增
- **后端技能**：核心实体按业务域分组
  - 收集模块：ArchiveCollection、ArchiveBuildingDetail、ArchiveBuildingBatchTotal
  - 四性检测：ArchiveDetectionReport、ArchiveFourPropertiesDetection
  - 档案利用：ArchiveBorrowBatchInfo
  - 系统管理：ArchiveFonds、ArchiveCtg、ArchiveEntityType
- **后端技能**：状态字段处理模式（5_1未组卷等状态）
- **后端技能**：事务边界模式（@Transactional 用法）

### 新增
- **前端技能**：模块索引表（前后端对应）
- **前端技能**：组件结构模式（树+表格布局、弹窗表单）
- **前端技能**：状态显示映射（archiveStatus 1-16完整状态码）

### 修正
- **状态码错误**：将错误的 `5_1/5_2/5_3/5_4` 修正为正确的 16个状态值
  - 正确来源：`sys_dict: archive_status`
  - 1=未归档, 2=已归档, 3=未移交, 4=已移交...16=已上架

### 背景
- 基于项目业务逻辑探索结果
- 补充业务实体和代码模式的空白

---

## [3.1.0] - 2026-05-09

### 优化
- 增加「必查清单（高频遗漏项）」
  - 前后端同步检查
  - Service 接口检查
  - 权限注解检查
  - 事务边界检查
- 增加「快速自检问题」（4个问题）
- 增加「典型遗漏场景」表格（来自历史经验）

### 背景
- 根据 #1066 评估分析，发现「遗漏后端修改」是最高频问题
- 制定了「关联范围检查」清单

## [3.0.0] - 2026-05-09

### 重构
- 拆分为子技能架构
  - `archive-dev-frontend/` - 前端开发子技能
  - `archive-dev-backend/` - 后端开发子技能
  - `archive-dev-kb/` - 知识库操作子技能

### 优化
- 主技能精简为入口点（170行 → 200行）
- 移除了详细的命令和流程（分散到子技能）
- 保留需求分析方法和影响范围评估作为核心

### 背景
- 原 v2.0.0 (549行) 过于庞大
- 前端/后端开发者关注点不同
- 知识库操作频率低于代码修改

---

## [2.0.0] - 2026-05-06

### 新增
- 添加项目结构规范（前后端）
- 添加影响范围评估清单
- 添加 Git 提交规范
- 添加知识库同步流程
- 添加修改记录文档模板

### 来源
- 基于 50 个历史提交的 git-history-analysis
- 总结常见修改模式和流程

---

## [1.0.0] - 2026-04-29

### 创建
- 初始版本
- 基础需求分析流程

---

## [6.1.0-cb] - 2026-05-18

### 重构
- **CodeBuddy 专用简化版**，删除 Hermes 特有的 Web 测试环节
- 5 阶段 → 4 阶段：去掉了 Phase 1 Web 测试验证 和 Phase 4 Web 复测验证
- 原 Phase 2/3/5 重编号为 Phase 1/2/3
- 版本号标记 `6.1.0-cb` 以区别于 Hermes 版

### 新增
- **Phase 1.4 定位问题清单** — 控制台报错/接口调用/状态流转/字段同步/SQL 错误五类排查项
- 禁止事项增加"不要只加不减"

### 删除
- 移除全部 `web-tester` 技能引用和凭证信息
- 移除 Phase 1 Web 测试验证整个章节
- 移除 Phase 4 Web 复测验证整个章节
- 移除强制执行规则中对 Phase 1/4 的 MANDATORY 要求

### 变更
- "常见违规"聚焦 CodeBuddy 实际易犯的错误（不移除旧代码、前后端不一致）
- 同步步骤集成到 Phase 3 Step 4 中（`python kb_hook.py`）

### 背景
- CodeBuddy 不具备浏览器测试能力，Web 测试环节无法执行
- 简化后流程：环境确认 → 代码探路定位 → 最小修改 → 修改记录

### 同步
- 从 Hermes Agent 技能库同步至 v6.1.0
- 与 Hermes 版 `archive-dev` 保持一致（`software-development/archive-dev/SKILL.md`）

### 新增
- **强制执行规则** — 6 条禁止违规的铁律
- **常见违规** — 基于历史审计发现的 4 种典型违规模式
- **Phase 0 禁止操作** — 5 种被禁止的终端操作（服务由 IDE 管理）
- **Phase 1/4 MANDATORY 标记** — Web 测试/复测不得跳过
- **Phase 5 详细流程** — Step 1-4 生成流程 + 各章节编写规范 + 回滚格式 + 质量检查清单
- **实例参考** — 指向已有修改记录文件

### 变更
- Phase 5 从 8 行扩展为 6 小节（5.1-5.6）
- 删除不存在的 `kb_hook.py` 引用，改为 kb-search MCP 查询方式

### 背景
- 审计 CodeBuddy 历史对话发现 Phase 5 未执行（文件不同步导致）
- 两个技能源保持单向同步：Hermes → CodeBuddy
