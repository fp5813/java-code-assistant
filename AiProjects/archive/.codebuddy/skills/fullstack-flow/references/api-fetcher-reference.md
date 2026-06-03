# api-fetcher MCP 工具参考

> api-fetcher 是轻量 MCP 服务，用于调用本地后端 API 获取真实响应数据，验证 Controller 层返回的 VO/DTO 结构。

## ⚡ 每日速查

```bash
# 1. 登录（首次 / token过期后）
api_login
# → token 自动缓存到 token-cache.json，6 天有效

# 2. 看列表接口返回什么字段
api_list path="/system/xxx/list" params={pageSize:3}
# → 自动注入 X-Access-Token，返回 responseFields 概览 + 真实数据

# 3. 看单条详情
api_get_by_id path="/system/xxx/queryById" id="xxx"
# → 返回完整 VO/DTO 数据
```

**无需手动传 token**，`api_login` 一次后全程自动注入。

## 背景

Phase 2 B2-1 之前只能通过 `codegraph_node(VO类名)` 读取代码定义来了解 VO/DTO 结构，但代码定义与实际 JSON 响应可能存在差异：

- `@JsonIgnore` 注解导致字段不输出
- `@JsonFormat` 导致日期类型变化
- null 值字段是否被序列化
- 动态字段（如 Map 或 `ext` 字段）
- 字典翻译后的实际值

api-fetcher 通过实际调用 API 解决上述问题。

## 工具列表

| 工具名 | 用途 |
|--------|------|
| `api_login` | 登录获取 JWT token（可选，根据接口是否需要鉴权） |
| `api_list` | 调用 GET 列表接口，返回分页数据 |
| `api_get_by_id` | 调用 GET 详情接口，返回单条数据 |
| `api_call` | 通用 API 调用，支持任意 HTTP 方法 |

## ⚡ Token 缓存机制

api-fetcher 会自动将登录获取的 JWT token 缓存到本地文件 `token-cache.json`，避免每次重启重复登录：

- **缓存路径**：`mcp-servers/api-fetcher/token-cache.json`
- **有效期**：6 天（JWT 实际有效期 7/30 天，留有余量提前续期）
- **自动注入**：调用 `api_list`/`api_get_by_id`/`api_call` 时自动读取缓存并注入 `X-Access-Token` header，**无需手动传参**
- **缓存失效**：过期后自动触发重新登录，仅需再次调用 `api_login`

## 典型探路用法

### 1. 登录获取 token（仅首次需要，后续自动复用）

```
api_login
```
默认使用 `admin / archive@System#2026`，也可通过参数自定义。

返回值示例：
```json
{
  "status": 200,
  "loginSuccess": true,
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "via": "/sys/mLogin",
  "cacheFile": "...token-cache.json",
  "expiresIn": "~6 days",
  "_nextStep": "token 已缓存，后续 api_list 等调用自动注入 X-Access-Token header"
}
```

### 2. 调用列表接口（token 自动注入，无需传 Authorization）

```
api_list path="/system/archiveFonds/list" params={pageNo:1, pageSize:3}
```

### 3. 获取单条详情（token 自动注入）

```
api_get_by_id path="/system/archiveFonds/queryById" id="12345"
```

### 4. 手动传 header（覆盖自动注入）

```
api_list path="/system/xxx/list" headers={"X-Access-Token":"手动指定token"}
```

## 返回值结构

工具返回的 JSON 包含三层：

```json
{
  "status": 200,              // HTTP 状态码
  "endpoint": "http://...",   // 实际调用的完整 URL
  "responseFields": {         // 字段结构概览（自动推断）
    "id": { "type": "string", "sample": "xxx" },
    "name": { "type": "string", "sample": "xxx" }
  },
  "data": ...                 // 原始响应数据
}
```

- `responseFields` 自动从响应数据的第一条记录提取字段名、类型和样例值
- `data` 保留原始 API 响应（含 JeecgBoot 的 `Result` 包装）

## 配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `--base-url` | `http://127.0.0.1:8081/archive` | 后端基础 URL |
| `--use-mlogin` | 未设置 | 使用 `/sys/mLogin` 免验证码登录（推荐） |
| `--token-cache-path` | `mcp-servers/api-fetcher/token-cache.json` | token 缓存文件路径 |

配置在 `.mcp.json` 中：

```json
{
  "mcpServers": {
    "api-fetcher": {
      "args": [
        "--base-url=http://127.0.0.1:8081/archive"
      ],
      "command": "node",
      "type": "stdio",
      "cwd": "mcp-servers/api-fetcher"
    }
  }
}
```

> 如果后端未关闭验证码（非 dev profile），添加 `--use-mlogin` 使用 APP 端免验证码登录。

## 注意事项

- **后端必须先启动**：服务不可用时 API 调用会返回错误，此时应跳过 Step B 仅做 codegraph 分析
- **只读原则**：探路阶段仅使用 GET 方法（`api_list`/`api_get_by_id`），POST 仅在 `api_login` 等必要场景使用
- **Token 头格式**：JeecgBoot 使用 `X-Access-Token` header（非标准 `Authorization: Bearer`），api-fetcher 自动使用正确格式
- **Token 缓存**：登录一次后自动缓存到本地文件，下次重启依然有效。缓存 6 天后过期，需重新 `api_login`
- **后端验证码**：`application-dev.yml` 中已配置 `jeecg.firewall.enable-login-captcha: false` 关闭验证码；如使用其他 profile，可改用 `--use-mlogin` 走 APP 端接口
