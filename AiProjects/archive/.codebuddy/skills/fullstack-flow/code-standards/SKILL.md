---
name: fullstack-flow/code-standards
description: 编码规范和项目约定（fullstack-flow 子技能）— 前后端目录结构、命名规范、组件设计原则、API 风格。
user-invocable: false
---

# 档案系统编码规范

## 后端规范（Java + Spring Boot / JeecgBoot）

### 目录结构

```
archive-server/
├── src/main/java/org/jeecg/modules/
│   ├── archive/       ← 档案业务模块
│   │   ├── entity/    ← JPA Entity
│   │   ├── controller/~ Controller（REST API）
│   │   ├── service/   ← Service 接口 + Impl
│   │   ├── mapper/    ← MyBatis Plus Mapper
│   │   └── vo/        ← 视图对象
│   └── ...
```

### 命名规范

| 层面 | 规范 | 示例 |
|------|------|------|
| Entity | 大写字母开头驼峰 | `ArchiveBusinessData` |
| Controller | 实体名 + Controller | `ArchiveBusinessDataController` |
| Service | I + 实体名 + Service | `IArchiveBusinessDataService` |
| Mapper | 实体名 + Mapper | `ArchiveBusinessDataMapper` |
| VO | 实体名 + VO/PageVO | `ArchiveBusinessDataVO` |

### API 风格

| 操作 | HTTP | URL 模式 | 注解 |
|------|------|----------|------|
| 分页查询 | GET | `/archive/xxx/page` | `@GetMapping` |
| 单条查询 | GET | `/archive/xxx/{id}` | `@GetMapping` |
| 新增 | POST | `/archive/xxx/add` | `@PostMapping` |
| 编辑 | PUT | `/archive/xxx/edit` | `@PutMapping` |
| 删除 | DELETE | `/archive/xxx/delete` | `@DeleteMapping` |

- 新接口需要 `@RequiresPermissions` 权限注解
- 多表操作需要 `@Transactional` 事务注解
- Controller 层保持轻薄，业务逻辑下沉到 Service

## 前端规范（Vue 3 + TypeScript + Vite）

### 目录结构

```
archive-web/src/
├── views/archive/    ← 档案业务视图
├── components/       ← 公共组件
├── api/              ← API 请求封装
├── utils/            ← 工具函数
└── types/            ← TypeScript 类型
```

### 组件设计原则

1. **不修改通用组件**：通用组件（`components/` 下）不因单一业务需求修改，通过 props 或 slots 扩展
2. **表单回显**：表单回显使用 `getById` 接口，不依赖列表页传入的完整数据
3. **弹框数据传递**：弹框组件的数据通过 props 传递，不通过全局状态（Pinia）共享
4. **C 类门类节点**：分类树中 C 类节点（门类）没有具体的档案数据，查询时需跳过

### API 选择指南

| 场景 | 接口 | 说明 |
|------|------|------|
| 档案列表 | `getArchivePageList` | 带分页、筛选 |
| 档案详情 | `getArchiveInfo` | 含完整元数据 |
| 档案树 | `getArchiveTree` | 门类分类树 |
| 编码字段处理 | `normalizeCodeField` | 将数组→纯编码字符串 |

## Git 规范

| 操作 | 规范 |
|------|------|
| 分支名 | `fix/功能简述` 或 `feat/功能名称` |
| 提交信息 | `类型(模块): 具体描述` 如 `fix(档案管理): 修复案卷排序BUG` |
| 提交粒度 | 一个 BUG 一个 commit，不混 commit |
