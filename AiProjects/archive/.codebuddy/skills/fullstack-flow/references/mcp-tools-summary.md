# MCP 工具汇总

| 工具 | 用途 | 状态 |
|------|------|------|
| **`codegraph`** | **代码结构搜索 + 调用链 + 影响分析** — 主力探路工具 | **⭐ 主用** |
| `mysql-archive` | 数据库表结构查询 | 主用 |
| `api-fetcher` | 调用本地后端 API 接口获取实时响应数据（验证 VO/DTO 结构和字段值） | **⭐ B2-1 新增** |
| `Context7` | GitHub 官方文档 | 辅助 |

## 使用优先级

1. **codegraph_context** — 首选，一次调用获取全部上下文
2. **codegraph_node** — 深入关键符号 details + trail
3. **api-fetcher** — 调用后端 API 获取真实响应数据，验证 VO/DTO 字段结构（后端运行时可选）
4. **mysql-archive** — 直接查数据库表结构

## 详细参考

- [codegraph 使用指南](./codegraph-reference.md)
- [api-fetcher 使用指南](./api-fetcher-reference.md)
