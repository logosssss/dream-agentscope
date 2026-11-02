# agent-access

接入层。认证之后的 HTTP 入口、线程上的租户用户，以及把请求交给 `AgentExecutor`。本模块不构造 `HarnessAgent`。

坐标：`com.zhu:agent-access`。包名：`com.zhu.access`。

## 依赖

依赖 `agent-common`、`agent-semantic`、`agent-runtime`、`agent-audit`、`agent-state`、`agent-vector`、`spring-boot-starter-web`、`spring-boot-starter-data-redis`、`mybatis-plus-spring-boot3-starter`。Redis 自动配置在启动模块里排除，避免没配地址时去连本机。MySQL 关闭时不扫描会话 Mapper。

架构测试禁止 `com.zhu.access` 引用 `..harness..`。控制器也不能依赖 `..infrastructure.persistence..`。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api.controller` | `LivenessController` | `GET /health/live`。只表示进程还在，不查数据库和模型 |
| `api.controller` | `ReadinessController` | `GET /health/ready`。生产探测 MySQL、Redis、Milvus，任一项失败 HTTP 503 |
| `api.controller` | `ChatController` | `POST /api/chat` 同步 JSON；`POST /api/chat/stream` 输出 SSE。不覆盖过滤器写入的线程身份 |
| `api.controller` | `ApprovalController` | `GET /api/approvals/pending`。只返回当前请求头用户和租户的待确认，不带工具参数 |
| `api.controller` | `NotificationController` | `GET /api/notifications` 列出本人审批通知。不发送，也不改状态 |
| `api.controller` | `SessionController` | `GET /api/sessions` 列出活动会话；`GET /api/sessions/{sessionId}` 看一次会话的最近运行和轮数。不含正文 |
| `api.dto` | `SessionDetail` | 详情一行：状态、最近运行、轮数和最近活动时间 |
| `api.controller` | `RunController` | `GET /api/runs` 列出本人本租户已经结束的运行；`GET /api/runs/{runId}` 看一次运行和步骤。不返回全文 |
| `api.dto` | `RunSummary` / `RunDetail` / `RunStepSummary` | 列表一行短摘要；详情另带步骤类型、工具名和状态 |
| `application` | `RunQuery` / `MysqlRunQuery` | 按用户和租户读 `agent_run`。详情再读步骤。MySQL 关闭时没有实现 |
| `api.controller` | `LoginController` | `POST /api/login` 签发会话令牌；`POST /api/logout` 删掉令牌。仍要调用方令牌 |
| `api.controller` | `MeController` | `GET /api/me` 看这次请求的租户和用户。不签发令牌 |
| `api.dto` | `CurrentUser` | 当前身份。显示名和状态在 MySQL 关闭时为空 |
| `api.dto` | `SessionSummary` | 列表一行：会话、短标题、通道、最近活动时间 |
| `api.dto` | `ChatRequest` | HTTP 入参。`channel` 空则 `WEB`，不传给运行层 |
| `api.dto` | `LoginRequest` / `LoginResponse` | 登录入参和令牌。没有密码字段 |
| `domain.channel` | `AccessChannel` | `WEB` / `IM` / `API`。只描述请求从哪进来 |
| `infrastructure.security` | `CallerTokenFilter` | `X-Caller-Token` 对上 `AGENTSCOPE_CALLER_TOKEN` 才放行。存活和就绪除外。这不是用户登录 |
| `infrastructure.security` | `AccessIdentityFilter` | `Authorization: Bearer` 登录会话优先；否则 `X-Tenant-Id` / `X-User-Id`。请求结束清理 |
| `infrastructure.cache` | `AccessCache` | 额度、幂等、限流、会话列表和登录会话。只走 Redis |
| `application` | `UserLoginService` | 核对租户用户后写入 `login-session:{token}`。Redis 失败 503 |
| `application` | `CurrentUserQuery` | 先过账号门，再可选读 `tenant_user`。不读剩余 TTL |
| `application` | `ChatAppService` | 请求体身份优先，空字段用请求头。空提示词走 `PromptService`，额度通过并占锁后记下会话，再调用 `AgentExecutor` |
| `application` | `SessionListService` | 列表键 `session-list:{tenant}:{user}`，五分钟。写入后删键。Redis 失败改查 MySQL |
| `api.controller` | `QuotaController` | `GET /api/quota` 看当日已用量和上限。不扣额度 |
| `api.dto` | `QuotaSnapshot` / `QuotaMeter` | 请求、token、费用微单位和同时运行的已用量与上限 |
| `application` | `QuotaGuard`、`RedisDailyTokenAccumulator`、`RedisDailyCostAccumulator`、`SessionTurnGuard`、`ConcurrentRunGuard` | 请求额度键 `quota:{tenant}:{user}:{yyyyMMdd}`；token 键 `quota-tokens:...`；费用键 `quota-cost:...`。会话短锁 `turn:{tenant}:{user}:{session}`，结束即释放。同时运行键 `concurrent:{tenant}`，结束减一 |
| `application` | `AccessAccountGate` | 对话以外的接口先过租户和用户门。MySQL 关闭时放行 |
| `api.controller` | `KnowledgeController` | `POST/GET/DELETE /api/kb`；`GET /api/kb/{kbId}` 看一个库；`GET /api/kb/{kbId}/documents`；`POST /api/kb/documents` JSON 正文；`POST /api/kb/documents/file` 纯文本 multipart（≤2MB）；status / reindex / `DELETE` 文档。立即返回 PENDING |
| `api.controller` | `AttachmentController` | `GET /api/attachments` 列出本人本租户仍启用的附件；`GET /api/attachments/{id}` 看 MIME 和大小；`POST /api/attachments` JSON 只登记元数据；`POST /api/attachments/file` multipart 写入 `ObjectStore`（≤5MB）；`GET /api/attachments/{id}/content` 下载正文；`POST /api/attachments/{id}/refs` 挂引用 |
| `api.controller` | `DeletionRequestController` | `GET /api/deletion-requests` 列出本人本租户的删除请求；`POST /api/deletion-requests` 登记待处理。不批准，也不真正删除 |
| `api.controller` | `FeedbackController` | `GET /api/feedback` 列出本人反馈；`POST /api/feedback` 登记 1 到 5 的评分。不核运行，也不收文件 |
| `api.controller` | `UsageController` | `GET /api/usage` 列出本人本租户的 token 用量。不带提示词，也不改金额 |
| `api.controller` | `TaskController` | `GET /api/tasks` 列出本人本租户的同步任务；`GET /api/tasks/{taskId}` 看一次任务的开始结束时间。不读参数，也不是待办 |
| `api.dto` | `TaskSummary` / `TaskDetail` | 列表一行：任务标识、类型、状态和异常类型；详情另带开始结束时间 |
| `application` | `TaskQuery` / `MysqlTaskQuery` | 按用户和租户读 `agent_task`。详情再带开始结束时间。MySQL 关闭时没有实现 |
| `application` | `AttachmentAppService` | 先写对象再登记。`storage_type` 跟实现走。路径 `att/{tenant}/{user}/{id}/{fileName}`。目录不可用或超限不写对象。登记失败尝试回滚对象。下载只读本人 `att/` 或 `kb/` 对象键，JSON 声称的盘路径不读 |
| `application` | `KnowledgeAppService`、`KnowledgeIndexLauncher`、`KnowledgeTextFileReader` | 落库后 `@Async` 发布 `DOCUMENT_INDEX`。文件只解 UTF-8 纯文本；原始字节写入 `ObjectStore`（local / OSS / S3 / MinIO），附件 `storage_type` 跟实现走，路径为业务对象键并挂 `DOCUMENT`。删除先清向量和对象再删 MySQL。对象删失败不挡删行。MySQL 关闭时建库不可用 |
| `application` | `OpenTenantUserGate` | MySQL 关闭时不查 `tenant_user`。打开时改走目录 |
| `infrastructure.persistence` | `MysqlSessionTurnCount` | MySQL 打开时数 `agent_run` 的成功行。人设检查用，不占锁 |

`/api/chat` 返回 `ApiResult<AgentResponse>`。`/api/chat/stream` 的 `Content-Type` 是 `text/event-stream`，每条 `data` 是 `AgentStreamEvent`（`type`、`text`）。两条路径共用同一份请求体：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `text` | 是 | 用户输入，对应 `userText` |
| `userId` | 是 | 请求体和 `X-User-Id` 都空时拒绝。搭子按用户切开 |
| `sessionId` | 是 | 缺了由运行层拒绝 |
| `tenantId` | 否 | 有值以请求体为准；空则用 `X-Tenant-Id` |
| `channel` | 否 | 空则 `WEB`。不传给运行层 |
| `agentId` | 否 | 空则用 `agentscope.runtime.agent-id` |
| `systemPrompt` | 否 | 已忽略。人设按 `prompt.companion.system` 读取，读不到用仓库兜底 |

线程上下文只是接入层副本。执行身份以 `AgentRequest` 里的字段为准。

`agentscope.access.daily-limit` 和 `per-minute` 默认是 0，表示不限制。MySQL 打开且该租户有启用的 `REQUESTS` / `DAILY` 行时，当日请求用这一行的上限，0 仍表示不限额；没有行时用进程配置。有启用的 `TOKENS` / `DAILY` 时，入口只读 Redis 当日已用量（`quota-tokens:{tenant}:{user}:{day}`），达到上限拒绝对话；用量在模型记账后由 `RedisDailyTokenAccumulator` 累加 input+output。有启用的 `COST` / `DAILY` 时同理读 `quota-cost:...`（金额微单位 ×1e6）；计价写 `token_usage` 后由 `RedisDailyCostAccumulator` 累加。分钟限流仍是进程配置。计数仍在 Redis，不把已用量写回这张表。查这行失败返回 `UNAVAILABLE`，不改用进程配置。当日请求计数通过后写 `quota_usage_log`，数量是 1，不填运行标识。不限额时不写。流水写失败不改变这次请求，超限的那一次不记。带了 `X-Request-Id` 时，十分钟内的重复提交返回冲突，不再执行第二次。同一租户、用户、会话进行中时，再来一轮也返回冲突，调用结束就释放。有启用的 `CONCURRENT_RUNS` / `CURRENT` 时，同一租户同时进行的对话不能超过这一行；占位键 `concurrent:{tenant}`，结束减一，进程崩溃后靠过期放开。Redis 不保存 Agent 状态或沙箱快照。`GET /api/quota` 只读这些 Redis 计数和租户上限，不增加计数，也不写流水。没有租户行时请求上限用进程配置，其它项上限为空。Redis 在额度、幂等、限流、占锁或并发占位时失败，请求返回 HTTP 503，错误码 `UNAVAILABLE`，不执行对话，也不改查 MySQL 或内存。释放锁或归还占位失败不改变已经生成的回复，键靠过期时间放开。

会话列表是另一条路。`agent_session_meta` 由用户进程的 Flyway 建立，标题最多 40 个字符，不存正文。`GET /api/sessions` 先过账号门，再读 Redis，没有或 Redis 不可用时查这张表。`GET /api/sessions/{sessionId}` 不走列表缓存，直接查库，带最近运行和轮数。别人的标识按找不到处理。对话在占锁成功后写入；删缓存失败不改变已经开始的对话。MySQL 打开时，同一租户下的 `sessionId` 已被其他用户占用则返回冲突。MySQL 关闭时不查归属，这只出现在测试里。

同一次对话结束时写 `agent_run`。同步路径用回复上的运行标识；流式路径由接入层生成，因为事件里没有标识。输入和输出只留 40 个字符的摘要，异常只留类型名。写失败不改变回复。成功、失败或取消后，把这个标识填进会话的 `last_run_id`。`API` 通道的触发类型是 `API`，页面和即时通讯是 `USER`。`GET /api/runs` 列出当前用户在这个租户下已经结束的运行，默认 50 条，最多 100 条。只带短摘要，不读步骤，也不返回异常原文。`GET /api/runs/{runId}` 先确认这次运行属于当前用户和租户，再读 `agent_run_step`。别人的标识按找不到处理。步骤只带序号、类型、工具名和状态，不带回参数、耗时或 token。MySQL 关闭时返回不可用。

同一次运行再写 `agent_run_step`。同步路径只有一步推理。流式路径按看到的顺序记下工具开始和待确认，工具名不含参数，最后补一步推理。取消的推理记成跳过。单步耗时和 token 先空着。

同步任务在执行前写入 `agent_task`，状态是 `RUNNING`，结束后改成 `SUCCEEDED` 或 `FAILED`。不存任务参数。开始记录失败时任务不执行。结束记录失败不改变已经跑完的结果。`GET /api/tasks` 列出当前用户在这个租户下的任务，默认 50 条，最多 100 条。不读 payload，也不是 `/api/todos`。`GET /api/tasks/{taskId}` 先确认这次任务属于当前用户和租户，再带开始结束时间。别人的标识按找不到处理。进行中的结束时间为空。MySQL 关闭时返回不可用。调度目录可以登记，还不计算下次触发，也不消费。

情景记忆和用户画像由运行层写。本模块不写这两张表。

`POST /api/login` 核对租户和 `tenant_user` 仍为 `ACTIVE` 后，签发不透明令牌写入 Redis，键是 `login-session:{token}`。没有密码，也不发 JWT。有效秒数读 `agentscope.access.login-session-seconds`，默认一天。后续请求可带 `Authorization: Bearer <token>`；带了就只信会话，无效 401，Redis 失败 503。没带 Bearer 时仍可用请求头。`POST /api/logout` 删掉会话键。`GET /api/me` 回这次请求已经核对过的租户和用户。MySQL 打开时带显示名和状态；关闭时这两项为空。不读剩余 TTL。调用方令牌仍然要过。MySQL 关闭时门放行，只留给测试。

## 还没有

密码登录、JWT、IM/API 通道适配。调用方令牌已经校验。对话仍可用请求头或请求体带身份。MySQL 打开时，对话、会话列表、运行记录、用量、额度、同步任务、待确认、待办、反馈、附件、删除请求和知识库接口都要求这个租户已登记且状态是 `ACTIVE`，并且这个用户已挂到该租户且状态是 `ACTIVE`。停用走管理接口。对话同时选用启用的 `model_config`，没有行再用进程配置。MySQL 关闭时不查。待确认按当前用户读 `approval_record`。同一事件再记一条 `approval_notification`，通道是 `WEB`，状态是 `PENDING`。`GET /api/notifications` 列出当前用户的通知，默认 50 条，最多 100 条。表没有租户列，不按租户再滤。不发 IM、邮件或短信。通知写失败不改变这次询问。MySQL 关闭时不记、列表返回不可用。同一事件再给发起人开一条 `user_todo`，类型是 `APPROVAL`，状态是 `OPEN`，标题是工具名。`POST /api/todos` 再给当前用户开确认或任务待办，只接受 `CONFIRM` / `TASK`。不核运行或任务是否存在。`GET /api/todos` 返回当前用户还开着的待办，含审批、确认和任务。`GET /api/todos/{todoId}` 看一条待办，含已关闭行和创建时间，不带回截止时间。别人的主键按找不到。审批通过后改 `DONE`，拒绝后改 `CANCELLED`。`POST /api/todos/{todoId}/done` 把这个用户还开着的确认或任务改成 `DONE`。`POST /api/todos/{todoId}/cancel` 改成 `CANCELLED`。审批待办仍走决定接口。别人的行按找不到处理。MySQL 关闭时这些接口返回不可用。`GET /api/feedback` 列出当前用户的反馈，默认 50 条，最多 100 条。表没有租户列，不按租户再滤。`POST /api/feedback` 记下当前用户对一次运行的评分，只接受 1 到 5。不核对运行是否存在，也不接收文件。MySQL 关闭时返回不可用。`GET /api/attachments` 列出当前用户在这个租户下仍启用的附件，默认 50 条，最多 100 条。不读对象存储。`GET /api/attachments/{attachmentId}` 先确认归属再带 MIME 和大小，不带回校验和。别人的或停用的按找不到。`POST /api/attachments` 仍可只登记元数据，不收文件。空白存储类型按 `LOCAL`。`POST /api/attachments/file` 收 multipart，字节写入 `ObjectStore`，`storage_type` 跟实现走，路径为 `att/{tenant}/{user}/{id}/{fileName}`，不超过 5MB。`GET /api/attachments/{attachmentId}/content` 只下载本人已落到对象存储的文件，路径必须落在 `att/{tenant}/{user}/` 或 `kb/{tenant}/{user}/`。对象不存在或 JSON 声称的盘路径按找不到。失败仍走统一 JSON。`POST /api/attachments/{attachmentId}/refs` 把该附件挂到一条消息或一次运行上，附件必须属于当前用户。不核对话或运行是否存在。MySQL 关闭时返回不可用。`GET /api/deletion-requests` 列出当前用户在这个租户下的删除请求，默认 50 条，最多 100 条，含已决定的行。`POST /api/deletion-requests` 记下当前用户的数据删除请求，状态是 `PENDING`。只接受 `SESSION` / `ATTACHMENT` / `ALL`。不批准，也不真正删除。MySQL 关闭时返回不可用。知识库可接 JSON 正文或纯文本文件 multipart；不解析 PDF/Office。文件字节写入 `ObjectStore`。列表库、单库详情、文档列表、删库、删文档已有；删时按 DOCUMENT 引用清对象。`GET /api/kb/{kbId}` 先确认归属再返回库名，不读文档。别人的标识按找不到。Hybrid 与 Rerank 已在运行层。`GET /api/runs` 列出当前用户在这个租户下已经结束的运行，默认 50 条，最多 100 条。只带短摘要，不读步骤，也不返回异常原文。`GET /api/runs/{runId}` 看一次运行和步骤，别人的标识按找不到。`GET /api/usage` 列出当前用户在这个租户下的 token 用量，默认 50 条，最多 100 条。不带提示词，也不改金额。`GET /api/quota` 看当日请求、token、费用微单位和同时运行的已用量与上限。不增加计数。Redis 失败返回不可用。`GET /api/me` 看这次请求的租户和用户。MySQL 打开时带显示名和状态。不读剩余 TTL。`GET /api/tasks` 列出当前用户在这个租户下的同步任务，默认 50 条，最多 100 条。不读 payload，也不是待办。`GET /api/tasks/{taskId}` 看一次任务的开始结束时间，别人的标识按找不到。MySQL 关闭时返回不可用。会话列表已经按当前用户读 `agent_session_meta`。会话详情已经带最近运行和轮数。运行列表已经按当前用户读 `agent_run`。SSE 只转发运行层已经收好的事件，接入层不调用 `streamEvents`。危机档和打开人设检查时可能先没有文本增量，结束事件带检查后的全文。通道枚举已经在接入层，不要挪到运行层。就绪检查只探测 Milvus 地址。集合由用户进程创建，管理进程不建。
