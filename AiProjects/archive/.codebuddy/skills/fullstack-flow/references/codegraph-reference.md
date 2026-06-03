# codegraph MCP 工具命令参考

> codegraph 是本项目 **主力代码探路工具**。为 lexis + graphify 的功能超集，支持代码结构搜索、符号定位、调用链追踪、影响范围分析、批量源码获取。
>
> **首选工具：`codegraph_context`** — 一次调用完成"搜索 + 定位 + 关联符号 + 代码片段"全部任务。

## 工具对照表

| 场景 | 旧工具 (lexis/graphify) | **新工具 (codegraph)** |
|------|------------------------|----------------------|
| 综合代码上下文（首选） | search_code + get_symbol + find_references 分3步 | `codegraph_context` **1步** |
| 获取单个符号详情+调用链 | get_symbol + find_references | `codegraph_node` |
| 搜索符号位置 | search_code | `codegraph_search` |
| 两点间调用路径 | call_chain + graphify path | `codegraph_trace` |
| 变更影响范围 | impact_analysis + graph_impact | `codegraph_impact` |
| 批量获取源码 | get_symbol 多个调用 | `codegraph_explore` |
| 文件/目录浏览 | find_file + 手动 `ls` | `codegraph_files` |
| 语义搜索代码结构 | graph_query | `codegraph_context` 或 `codegraph_search` |

## 工具详解

### 1. `codegraph_context` — 综合代码上下文 ⭐ 首选

**一次调用完成**：搜索入口点 + 关联符号 + 关键代码片段。

```
参数：
  task: "描述任务/BUG/功能需求"   ← 自然语言描述，codegraph 会语义理解
  maxNodes: 20                    ← 最大符号数（默认 20，一般足够）
  includeCode: true               ← 是否包含代码片段（默认 true）
```

**典型用法**：

```
# Bug 调查：直接描述错误场景
codegraph_context(task="ArchiveBuildingBatchTotal list 接口报错，搜索条件传递异常")

# 功能探索：描述要理解的功能
codegraph_context(task="弹框组件的显示/隐藏逻辑，关联 status 字段")

# 新增功能：描述所需功能
codegraph_context(task="全宗管理模块的树形结构加载和部门关联逻辑")
```

**优势**：一次调用即可获得：
- 入口点列表（Controller、Service、Mapper）
- 关联符号（相关类、方法、接口）
- 关键代码片段（行号标注）
- 足够的上下文判断是否需要进一步深入

---

### 2. `codegraph_node` — 符号详情 + 调用链

获取单个符号的代码位置、签名、docstring，以及 **trail**（谁调用它 / 它调用谁）。

```
参数：
  symbol: "类名或方法名"   ← 来自 codegraph_context/codegraph_search 的结果
  includeCode: false       ← true 时返回完整源代码（函数体/类成员列表）
```

**典型用法**：

```
# 查看方法详情 + 调用链（不带源码，轻量）
codegraph_node(symbol="queryPageList")

# 查看方法完整源码（需要调试细节）
codegraph_node(symbol="ArchiveFondsServiceImpl.page", includeCode=true)

# 查看类结构
codegraph_node(symbol="ArchiveFondsController")
```

**trail 字段说明**：
- `callers` — 哪些符号调用了当前符号
- `callees` — 当前符号调用了哪些符号
- 每个调用关系标注 `file:line`

---

### 3. `codegraph_search` — 快速符号搜索

按名称搜索符号，返回位置列表（无代码）。

```
参数：
  query: "符号名或部分名称"   ← 支持驼峰/下划线/部分匹配
  kind: "function"           ← 可选: function/method/class/interface/type/variable/route/component
  limit: 10                  ← 最大结果数（默认 10）
```

**典型用法**：

```
# 搜索类
codegraph_search(query="ArchiveFonds", kind="class")

# 搜索方法
codegraph_search(query="queryPageList", kind="method")

# 搜索路由
codegraph_search(query="archiveFonds", kind="route")

# 搜索组件
codegraph_search(query="ArchiveFondsModal", kind="component")
```

---

### 4. `codegraph_trace` — 调用路径追踪

追踪从符号 A 到符号 B 的调用路径。

```
参数：
  from: "起始符号名"     ← 如 Controller 方法
  to: "目标符号名"       ← 如 Service/Mapper 方法
```

**典型用法**：

```
# Controller → Service → Mapper 的完整路径
codegraph_trace(from="ArchiveFondsController.queryPageList", to="ArchiveFondsMapper")

# 前端 API → 后端路径
codegraph_trace(from="getFondsList", to="ArchiveFondsController.queryPageList")
```

---

### 5. `codegraph_impact` — 影响范围分析

分析修改某个符号可能影响的代码范围。

```
参数：
  symbol: "符号名"
```

**典型用法**：

```
# 修改 Service 方法前，评估影响范围
codegraph_impact(symbol="ArchiveFondsServiceImpl.updateStatus")

# 修改 Entity 字段前，评估连锁影响
codegraph_impact(symbol="ArchiveFondsEntity")
```

---

### 6. `codegraph_explore` — 批量获取源码

一次调用获取多个相关符号的完整源码，按文件分组。

```
参数：
  query: "符号名/文件名/代码术语"   ← 用好几个词描述要获取的代码
  maxFiles: 12                       ← 最大文件数（默认 12）
```

**典型用法**：

```
# 获取 Controller + Service + Mapper 的完整实现
codegraph_explore(query="ArchiveFondsController ArchiveFondsServiceImpl ArchiveFondsMapper ArchiveFonds")

# 获取前端组件和相关 API
codegraph_explore(query="ArchiveFondsModal ArchiveFonds.api fondsList.vue")
```

**注意**：返回的代码是 **逐字节一致的源代码**（等同于 Read），行号标注，已读后无需再 Read。

---

### 7. `codegraph_files` — 文件结构浏览

浏览项目的文件/目录结构。

**典型用法**：

```
# 查看模块目录结构
codegraph_files(path="archive-web/src/views/archive/system/")

# 查看后端模块结构
codegraph_files(path="jeecg-boot-module/jeecg-module-archivesys/src/main/java/org/jeecg/module/system/")
```

---

## 标准探路流程

### Bug 调查流程（3 步）

```
Step 1: codegraph_context(task="BUG 描述")         ← 一次性获取入口点+关键符号+代码片段
Step 2: codegraph_node(symbol="关键方法", includeCode=true)  ← 深入关键方法看细节
Step 3: codegraph_trace(from="入口", to="数据源")           ← 追踪完整调用链
```

### 新增功能探路流程（3 步）

```
Step 1: codegraph_context(task="功能描述")          ← 获取相关代码上下文
Step 2: codegraph_files(path="模块目录")             ← 了解模块文件结构
Step 3: codegraph_explore(query="相关符号")          ← 批量获取源码
```

### 影响评估流程（2 步）

```
Step 1: codegraph_impact(symbol="要修改的符号")     ← 评估影响范围
Step 2: codegraph_node(symbol="关联符号")            ← 深入受影响节点
```

## 与其他工具配合

| 配合工具 | 用途 | 调用时机 |
|---------|------|---------|
| `mysql-archive` | 直接查表结构/数据 | codegraph 定位到表后，用 mysql-archive 确认字段值域 |
| `Read` | 精确验证行号和上下文 | codegraph 返回的位置信息需要精确确认时 |
| `Agent` | 并行子代理探路 | 拆分探路任务并行执行，提升效率 |

## 已弃用的旧工具

- `lexis` 工具家族（search_code/get_symbol/find_references/call_chain）→ **使用 codegraph 替代**
- `graphify` 工具家族（graph_query/graph_explain/graph_impact）→ **使用 codegraph 替代**
