# agent-runtime

执行内核。每次请求创建一次 `HarnessAgent`，执行完就关闭。本模块不认识具体产品，也不提供 HTTP 接口。

坐标：`com.zhu:agent-runtime`。包名：`com.zhu.runtime`。

## 依赖

依赖 `agent-common`、`agent-semantic`、`agent-state`、`agent-vector`、`agent-audit`、`agentscope-harness`、`agentscope-extensions-model-dashscope`。

对 `agent-semantic` 的 Java 引用只能落在 `com.zhu.semantic.contract`。不要 import `semantic.application` 或 `semantic.infrastructure`。

不能依赖 `agent-access`。审批要通知前端时，发布 `agent-audit` 里的事件，由接入层订阅。

## 调用链

```text
AgentExecutor.execute(AgentRequest)
        │
        ▼
AgentExecutionService
        │  校验 userId、sessionId、userText、输入长度和系统提示词占位符
        │  构造 RuntimeContext
        ▼
HarnessAgentFactory.build(request)   每次 new Builder，然后 build()
        │
        ▼
agent.call(userText, context).block(timeout)
        │
        finally agent.close()

AgentExecutor.stream(AgentRequest)
        │  同样先校验并构造 RuntimeContext
        ▼
agent.streamEvents(userText, context)
        │  危机档和打开人设检查时先扣文本增量，结束事件再过规则
        │  取消、完成或失败时 close
        ▼
AgentStreamEvent   接入层再写成 SSE
```

容器里只有 `AgentExecutionService` 和 `HarnessAgentFactory`。没有 `HarnessAgent` 这个 Bean。Builder 是可变的，所以不在多线程之间共享同一个 Builder 再改提示词。

正式启动会挂上 `DistributedStore`，并把工作区设成 `RemoteFilesystemSpec`，隔离粒度固定 `SESSION`。文件进 MySQL 的 `agentscope_store`，不落在本机盘。开关关闭时没有这个 Bean，只留给测试。

停机时 `AgentShutdown` 让进程不再接新的 Agent 调用，并按 `timeout-seconds` 等待已经开始的调用。Web 容器使用 `server.shutdown=graceful`。容器的终止宽限不要短于这个等待时间。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api` | `AgentExecutor` | 接入层唯一入口 |
| `api` | `AgentRequest` | 执行入参。产品语义由调用方填好 |
| `api` | `AgentResponse` | 运行标识和文本，不泄漏框架的 `Msg` |
| `api` | `AgentStreamEvent` | 流式事件的类型和短文本 |
| `api` | `SessionTurnCount` | 会话已经成功结束的轮次。人设检查只看这个数 |
| `application` | `AgentExecutionService` | 同步 `block` 和流式 `streamEvents`，结束时关闭 Agent。危机档和打开人设检查时过整段回复，不另写干预 |
| `application` | `ResolvedChatModel` | MySQL 打开时用启用的 `model_config`，拼成 `provider:model`。没有行再用进程配置 |
| `application` | `AgentStreamEvents` | 框架事件收成 `AgentStreamEvent` |
| `application` | `CrisisReplyOutlet` | 需要攒齐再查的流式出口。增量先扣下，整段结束再过规则 |
| `application` | `AbsentSessionTurnCount` | MySQL 关闭时成功轮次按 0，人设检查保持关闭 |
| `infrastructure.agent` | `HarnessAgentFactory`、`CatalogToolRegistrar`、`WorkspaceFilesystem`、`AgentShutdown` | 按请求组装 Agent，并把目录里的业务工具挂到这次 Toolkit。有状态库时工作区按对话隔离。停机时等待在途调用 |
| `infrastructure.embed` | `DashScopeTextEmbedder` | 密钥读 `DASHSCOPE_API_KEY`。空密钥或失败返回空向量，不让进程退出 |
| `config` | `AgentRuntimeProperties` | 前缀 `agentscope.runtime` |
| `config` | `RuntimeConfig` | 只打开配置绑定，不创建 Agent |
| `domain` | `CallIdentity`、`TextEmbedder`、`RecalledMemories`、`CrisisReplyGuard`、`PersonaConsistencyGuard` | 从这次 `RuntimeContext` 读租户、用户、会话，不用 ThreadLocal。嵌入锁 `text-embedding-v4` / 1024。召回结果去重最多 5 条，再由中间件追加到系统提示词。危机回复按玩笑、暧昧、扮演、危险方法四类词表检查，命中换成 `CrisisPrompt`。人设回复按恋人、助手、替人决定三类词表检查，命中换成短基线 |
| `domain.middleware` | `AuditMiddleware`、`ApprovalMiddleware`、`FileWriteLimitMiddleware`、`SafetyGateMiddleware`、`PersonaConsistencyMiddleware`、`CompanionMemoryMiddleware` | 执行策略。ASK 时发布审批事件。写文件超过 `max-write-bytes` 时丢掉，不调用接入层。关键词或语义判定到危机时换危机话术，升高时只加克制标记。已经成功 20 轮、本会话第 1 轮且已有跨会话记忆，或用户纠正身份时打开人设检查标记，危机档不打开。普通和升高在对话开始时召回最近 3 加近邻 3，去重最多 5 条；成功结束后写短摘要和相识画像。系统提示词追加短画像和最多 5 条召回。危机不召回、不写、不注入。读失败或没密钥不改变回复。不用已标删除的 Hook |
| `task` | `AgentTask`、`AgentTaskPublisher` | 任务消息体和发布口。业务层调用，工厂和 Middleware 不感知 |
| `task` | `SyncTaskPublisher`、`DispatchingAgentTaskHandler`、`DocumentIndexExecutor` | `mode=sync` 时当前线程执行。`DOCUMENT_INDEX` 切块嵌入双写；其它类型只记审计。`SyncAgentTaskHandler` 留给测试直接构造 |
| `task` | `RocketMqTaskPublisher` | `mode=mq` 时的占位。启动即失败，topic 预留为 `agentscope-tasks`，不引入 RocketMQ 依赖 |
| `domain.knowledge` | `TextChunker`、`HybridChunkMerger` | 固定 500/50 字符切块。不做语义切块。Hybrid 合并：向量优先、全文补缺、按 id 去重 |
| `infrastructure.tool` | `KnowledgeSearchTool` | Agentic `knowledge_search`。身份只从 `RuntimeContext` 取；Hybrid（向量 + FULLTEXT），向量失败降级全文；只回 SUCCESS 切块。放在运行层是因为要嵌入和向量 |

## 配置

| 项 | 默认 | 说明 |
| --- | --- | --- |
| `agentscope.runtime.model` | `dashscope:qwen-plus`，可用 `AGENTSCOPE_MODEL` 覆盖 | 模型坐标。密钥用环境变量，不写进仓库 |
| `agentscope.runtime.agent-id` | `companion-v1` | 人格版本。不要把 userId 拼进来。请求里的 `agentId` 非空时以请求为准 |
| `agentscope.runtime.name` | `companion` | 展示名 |
| `agentscope.runtime.workspace` | 空 | 空则用框架默认工作区 |
| `agentscope.runtime.max-iters` | `20` | 单次最大推理轮数。由产品语义提供，小于 1 时仍按 20 |
| `agentscope.runtime.max-write-bytes` | `5242880` | 单次写文件内容上限，5MB。小于 1 时仍按 5MB |
| `agentscope.runtime.timeout-seconds` | `120` | 同步 `block` 和流式 `timeout` 的上限 |
| `agentscope.task.mode` | `sync` | 当前线程执行任务。`mq` 会拒绝启动 |

系统提示词来自接入层填好的 `AgentRequest.systemPrompt`。接入层丢掉请求体人设，按 `prompt.companion.system` 取值。工厂在空值时用产品兜底，避免空提示词进入模型。

业务工具实现 `ProductTool`。`toolId` 必须和 `@Tool` 的 name、目录里的标识一致。MySQL 打开时目录来自 `tool_registry` 的启用行。工厂在 `build` 之后把目录点名且有实现的工具注册到这次 Agent 自己的 Toolkit。目录为空，或实现不在目录里时，不注册。产品域有 `echo_text`（全局 `ASK`）；运行层有 `knowledge_search`（全局 `ALLOW`，种子见 `V35__knowledge_search_tool.sql`）。

`userId`、`sessionId`、`userText` 缺任何一个，抛 `BusinessException(BAD_REQUEST)`。

一次调用结束且回复带 token 用量时，`AuditUsageMeter` 写一条动作名为 `model.usage` 的审计，并写入 `token_usage`；若有 `DailyTokenAccumulator` 再累加当日 Redis token 计数。明细只有模型名、输入 token、输出 token 和耗时，不放提示词。没有用量就不记。审计或用量表写失败不改变已经生成的回复。命中 `model_price` 时按每千 token 计价，没有命中时金额记 0。

每个 Agent 挂 `OtelTracingMiddleware`。`agentscope.otel.endpoint` 为空时不导出，默认对应环境变量 `OTEL_EXPORTER_OTLP_ENDPOINT`。配了才按 OTLP 上报 trace，服务名用 `OTEL_SERVICE_NAME`，缺省是 `agentscope-user`。度量和日志不导出。导出装不上时对话继续。

`mode=sync` 时，`SyncTaskPublisher` 先写 `agent_task` 再执行。MySQL 关闭时不落库。任务参数不入库。

切到 `agentscope.task.mode=mq` 之前要先做完这三步，否则 `RocketMqTaskPublisher` 会让进程起不来：

1. 在 `agent-runtime` 引入 `rocketmq-spring-boot-starter`。现在不要加。
2. 配置 `rocketmq.name-server`，地址只来自环境变量。不要把地址写进仓库。
3. 增加消费者，监听 topic `agentscope-tasks`，收到消息后调用现有的 `AgentTaskHandler.handle`。消息 key 用 `taskId`。不要再注册第二个 `AgentTaskHandler`。

## 还没有

按产品预构建多份 Builder。进出模型仍检查长度和未替换的 `{{变量}}`。关键词命中 `CRITICAL` 时换成危机话术，不再问语义判定。语义判定失败按 `ELEVATED`。干预写失败不改变话术。危机档同步和流式都检查输出红线，流式先攒齐再查，命中换成 `CrisisPrompt`，不另写干预。升高档不查输出红线。会话已经成功 20 轮、本会话第 1 轮且已有画像或情景记忆，或用户对准身份纠正时，按规则看人设漂移。短基线来自 `PersonaContract`，不调模型。纠正只认固定短句。标记只活这一轮。情景记忆只留用户输入的短句，不调用对话模型。普通和升高把短画像和最多 5 条召回追加到系统提示词，危机不追加。嵌入失败、向量写入失败或画像读失败不改变回复。Persona 仍不读画像表，注入时从表读阶段，没有行再用锁定阶段。管理列表在 `agent-admin`。RocketMQ 客户端和消费者还没接。`stream` 返回 `Flux<AgentStreamEvent>`，SSE 控制器放在 `agent-access`，不要在本模块写控制器。不要再注册第二个 `AgentTaskHandler`。知识库删库/删文档与纯文本文件上传在接入层完成。Hybrid 检索已接；还没有 Rerank。索引仍走 sync 发布器，只是入口 `@Async` 离开 HTTP 线程。

`ApprovalMiddleware` 在 `ASK` 时把本次工具标成 `ASKING` 并发出事件，在 `DENY` 时把这次工具从后续执行里拿掉。同一用户、会话和工具已有未执行的批准单时，这次调用放行，结束后回写执行状态，不存工具输出。它不改框架的 `PermissionEngine`：默认权限上下文是 trivial，临时加规则要么这次判定读不到，要么会让同一次调用里没匹配到的工具也变成询问。框架自 2.0.0 起把 `Hook` 标成删除，执行策略挂在 `Middleware` 上。
