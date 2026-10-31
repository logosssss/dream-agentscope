# agent-access

接入层。认证之后的 HTTP 入口、线程上的租户用户，以及把请求交给 `AgentExecutor`。本模块不构造 `HarnessAgent`。

坐标：`com.zhu:agent-access`。包名：`com.zhu.access`。

## 依赖

依赖 `agent-common`、`agent-semantic`、`agent-runtime`、`agent-audit`、`agent-state`、`spring-boot-starter-web`、`spring-boot-starter-data-redis`、`mybatis-plus-spring-boot3-starter`。Redis 自动配置在启动模块里排除，避免没配地址时去连本机。MySQL 关闭时不扫描会话 Mapper。

架构测试禁止 `com.zhu.access` 引用 `..harness..`。控制器也不能依赖 `..infrastructure.persistence..`。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api.controller` | `LivenessController` | `GET /health/live`。只表示进程还在，不查数据库和模型 |
| `api.controller` | `ReadinessController` | `GET /health/ready`。生产探测 MySQL、Redis、Milvus，任一项失败 HTTP 503 |
| `api.controller` | `ChatController` | `POST /api/chat` 同步 JSON；`POST /api/chat/stream` 输出 SSE。不覆盖过滤器写入的线程身份 |
| `api.controller` | `ApprovalController` | `GET /api/approvals/pending`。只返回当前请求头用户和租户的待确认，不带工具参数 |
| `api.controller` | `SessionController` | `GET /api/sessions`。只返回当前请求头用户和租户的活动会话，不含正文 |
| `api.dto` | `SessionSummary` | 列表一行：会话、短标题、通道、最近活动时间 |
| `api.dto` | `ChatRequest` | HTTP 入参。`channel` 空则 `WEB`，不传给运行层 |
| `domain.channel` | `AccessChannel` | `WEB` / `IM` / `API`。只描述请求从哪进来 |
| `infrastructure.security` | `AccessIdentityFilter` | `X-Tenant-Id`、`X-User-Id`、`X-Request-Id` 写入线程，请求结束清理。不拦截缺头请求 |
| `infrastructure.cache` | `AccessCache` | 额度、幂等、限流计数，以及会话列表缓存。只走 Redis |
| `application` | `ChatAppService` | 请求体身份优先，空字段用请求头。空提示词走 `PromptService`，额度通过并占锁后记下会话，再调用 `AgentExecutor` |
| `application` | `SessionListService` | 列表键 `session-list:{tenant}:{user}`，五分钟。写入后删键。Redis 失败改查 MySQL |
| `application` | `QuotaGuard`、`SessionTurnGuard` | 额度键 `quota:{tenant}:{user}:{yyyyMMdd}`。会话短锁 `turn:{tenant}:{user}:{session}`，结束即释放 |

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

`agentscope.access.daily-limit` 和 `per-minute` 默认是 0，表示不限制。带了 `X-Request-Id` 时，十分钟内的重复提交返回冲突，不再执行第二次。同一租户、用户、会话进行中时，再来一轮也返回冲突，调用结束就释放。Redis 不保存 Agent 状态或沙箱快照。Redis 在额度、幂等、限流或占锁时失败，请求返回 HTTP 503，错误码 `UNAVAILABLE`，不执行对话，也不改查 MySQL 或内存。释放锁失败不改变已经生成的回复，锁靠过期时间放开。

会话列表是另一条路。`agent_session_meta` 由用户进程的 Flyway 建立，标题最多 40 个字符，不存正文。`GET /api/sessions` 先读 Redis，没有或 Redis 不可用时查这张表。对话在占锁成功后写入；删缓存失败不改变已经开始的对话。MySQL 关闭时不写列表，这只出现在测试里。

## 还没有

登录和令牌、IM/API 通道适配。待确认按当前用户读 `approval_record`。会话列表已经按当前用户读 `agent_session_meta`。SSE 只转发运行层已经收好的事件，接入层不调用 `streamEvents`。通道枚举已经在接入层，不要挪到运行层。Milvus 这次只探测地址，不建集合。
