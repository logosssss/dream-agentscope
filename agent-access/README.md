# agent-access

接入层。认证之后的 HTTP 入口、线程上的租户用户，以及把请求交给 `AgentExecutor`。本模块不构造 `HarnessAgent`。

坐标：`com.zhu:agent-access`。包名：`com.zhu.access`。

## 依赖

依赖 `agent-common`、`agent-semantic`、`agent-runtime`、`agent-audit`、`agent-state`、`spring-boot-starter-web`、`spring-boot-starter-data-redis`。Redis 自动配置在启动模块里排除，避免没配地址时去连本机。

架构测试禁止 `com.zhu.access` 引用 `..harness..`。控制器也不能依赖 `..infrastructure.persistence..`。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api.controller` | `LivenessController` | `GET /health/live`。只表示进程还在，不查数据库和模型 |
| `api.controller` | `ReadinessController` | `GET /health/ready`。MySQL、Redis 关闭时跳过；打开但不可用时 HTTP 503 |
| `api.controller` | `ChatController` | `POST /api/chat` 同步 JSON；`POST /api/chat/stream` 输出 SSE。不覆盖过滤器写入的线程身份 |
| `api.controller` | `ApprovalController` | `GET /api/approvals/pending`。只返回当前请求头用户和租户的待确认，不带工具参数 |
| `api.dto` | `ChatRequest` | HTTP 入参。`channel` 空则 `WEB`，不传给运行层 |
| `domain.channel` | `AccessChannel` | `WEB` / `IM` / `API`。只描述请求从哪进来 |
| `infrastructure.security` | `AccessIdentityFilter` | `X-Tenant-Id`、`X-User-Id`、`X-Request-Id` 写入线程，请求结束清理。不拦截缺头请求 |
| `infrastructure.cache` | `AccessCache` | 额度、幂等、限流计数。默认进程内；`agentscope.cache.redis.enabled=true` 且配置了 host 后改走 Redis |
| `application` | `ChatAppService` | 请求体身份优先，空字段用请求头。空提示词走 `PromptService`，额度通过后再调用 `AgentExecutor` |
| `application` | `QuotaGuard` | 键为 `quota:{tenant}:{user}:{yyyyMMdd}`（48 小时）、`idem:{requestId}`（10 分钟）、`rate:{endpoint}:{user}:{分钟}` |
| `application` | `ApprovalNotifyAppService` | 订阅 `ApprovalRequestedEvent`。按用户交到 `/api/approvals/pending`，列表只在本进程内存 |

`/api/chat` 返回 `ApiResult<AgentResponse>`。`/api/chat/stream` 的 `Content-Type` 是 `text/event-stream`，每条 `data` 是 `AgentStreamEvent`（`type`、`text`）。两条路径共用同一份请求体：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `text` | 是 | 用户输入，对应 `userText` |
| `userId` | 是 | 请求体和 `X-User-Id` 都空时，运行层拒绝 |
| `sessionId` | 是 | 缺了由运行层拒绝 |
| `tenantId` | 否 | 有值以请求体为准；空则用 `X-Tenant-Id` |
| `channel` | 否 | 空则 `WEB`。不传给运行层 |
| `agentId` | 否 | 空则用 `agentscope.runtime.agent-id` |
| `systemPrompt` | 否 | 有值以请求体为准。空则按 `prompt.platform.system` 读取，读不到用 `.` |

线程上下文只是接入层副本。执行身份以 `AgentRequest` 里的字段为准。

`agentscope.access.daily-limit` 和 `per-minute` 默认是 0，表示不限制。带了 `X-Request-Id` 时，十分钟内的重复提交返回冲突，不再执行第二次。Redis 不保存 Agent 状态、沙箱快照或执行锁。

## 还没有

登录和令牌、会话列表缓存、IM/API 通道适配。待确认通知只在用户进程内存里，重启会丢，也不等于管理进程读到的 MySQL 单据。SSE 只转发运行层已经收好的事件，接入层不调用 `streamEvents`。通道枚举已经在接入层，不要挪到运行层。向量库还没有接入，就绪检查不探测它。
