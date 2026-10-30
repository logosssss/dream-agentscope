# agent-access

接入层。认证之后的 HTTP 入口、线程上的租户用户，以及把请求交给 `AgentExecutor`。本模块不构造 `HarnessAgent`。

坐标：`com.zhu:agent-access`。包名：`com.zhu.access`。

## 依赖

依赖 `agent-common`、`agent-semantic`、`agent-runtime`、`agent-audit`、`agent-state`、`spring-boot-starter-web`。

架构测试禁止 `com.zhu.access` 引用 `..harness..`。控制器也不能依赖 `..infrastructure.persistence..`。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api.controller` | `LivenessController` | `GET /health/live`。只表示进程还在，不查数据库和模型 |
| `api.controller` | `ChatController` | `POST /api/chat`。写入并在 `finally` 清理 `TenantContext`、`UserContext` |
| `api.dto` | `ChatRequest` | HTTP 入参，与运行层的 `AgentRequest` 分开 |
| `application` | `ChatAppService` | 把 `ChatRequest` 译成 `AgentRequest`，再调用 `AgentExecutor` |
| `application` | `ApprovalNotifyAppService` | 订阅 `ApprovalRequestedEvent`，先收进待推送列表 |

`/api/chat` 目前是同步 JSON，返回 `ApiResult<AgentResponse>`。请求体字段：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `text` | 是 | 用户输入，对应 `userText` |
| `userId` | 是 | 缺了由运行层拒绝 |
| `sessionId` | 是 | 缺了由运行层拒绝 |
| `tenantId` | 否 | 放进线程上下文，并传给运行层 |
| `agentId` | 否 | 空则用 `agentscope.runtime.agent-id` |
| `systemPrompt` | 否 | 空则运行层使用占位符 |

线程上下文只是接入层副本。执行身份以 `AgentRequest` 里的字段为准。

## 还没有

登录和令牌、SSE、额度扣减、Channel，以及把待推送审批真正送到 IM 或前端。订阅已经在接入层，不要挪到运行层。

存活检查故意不看 MySQL。依赖不可用应该影响就绪检查，而不是把进程判死。
