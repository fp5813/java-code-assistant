# Spec-Driven Development 参考（基于 Spec-Kit）

## 核心理念

> **先说清楚"做什么 + 为什么"，再动手"怎么做"**

Spec-Kit 是 GitHub 推出的规范驱动开发工具包，核心思想是：
- 代码只是实现，规范才是源头
- 用 Markdown 规格文档驱动 AI 生成代码
- 多阶段细化：规格 → 计划 → 任务 → 实现

## 与传统开发模式的对比

| 维度 | 传统模式（Vibe Coding） | 规范驱动（SDD） |
|------|----------------------|----------------|
| 需求理解 | 靠 prompt 猜测 | 规格文档明确定义 |
| 修改范围 | 容易扩大 | 先定边界 |
| 验收标准 | 改完即结束 | 规格中定义 |
| 返工风险 | 高（方向易错） | 低（规格澄清阶段纠正） |
| 协作性 | AI 各自为战 | 规格是团队共识 |

## archive-dev 中的 SDD 流程映射

```
Spec-Kit 原流程              archive-dev 对应
─────────────────────────────────────────────
spec (规格)        ←→    Phase 3 规格澄清
plan (计划)        ←→    Phase 4 实施计划
tasks (任务)       ←→    Phase 5 实施（任务清单执行）
implement (实现)   ←→    Phase 5 实施（代码修改）
check (校验)       ←→    Phase 6 修改记录 + Hermes 复测
```

## 关键原则

### 1. 意图驱动（What + Why 先于 How）

**错误示范**：
```
"在 FileAdjustmentModal.vue 的第 200 行加一个判断"
```

**正确示范**：
```
"档案调整弹框在案卷级时不应显示，因为案卷本身不需要调整，
只有文件需要调整。这是业务规则，不是 UI 选项。"
```

### 2. 规格澄清阶段不写代码

Phase 3 的目标是回答：
- 现象是什么？（What）
- 根本原因是什么？（Why）
- 修复后应该是什么样子？（Goal）
- 哪些不做？（Scope 边界）

### 3. 计划阶段不写代码

Phase 4 的目标是回答：
- 有哪些任务？
- 任务之间的依赖是什么？
- 实施顺序是什么？

### 4. 实施阶段按计划执行

Phase 5 按任务清单执行，每完成一个任务打勾确认。

## Spec-Kit CLI 命令（参考）

> 以下命令因网络问题无法安装，仅作参考：

```bash
# 初始化项目
specify init <PROJECT_NAME>

# 创建规格文档
specify spec

# 生成实施计划
specify plan

# 执行实施
specify implement

# 校验工具链
specify check
```

## 进一步阅读

- GitHub Spec-Kit：https://github.com/github/spec-kit
- Spec-Kit 中文版：https://github.com/linfee/spec-kit-cn
