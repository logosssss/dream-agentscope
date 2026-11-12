# agent-runtime

执行内核。每次请求创建一次 `HarnessAgent`，执行完就关闭。本模块不认识具体产品，也不提供 HTTP 接口。

坐标：`com.zhu:agent-runtime`。包名：`com.zhu.runtime`。

## 依赖

依赖 `agent-common`、`agent-semantic`、`agent-state`、`agent-vector`、`agent-audit`、`agentscope-harness`、`agentscope-extensions-model-dashscope`。

对 `agent-semantic` 的 Java 引用只能落在 `com.zhu.semantic.contract` 及其子包（`product` / `tool` / `permission` / `safety`）。不要 import `semantic.application` 或 `semantic.infrastructure`。

不能依赖 `agent-access`。Permission Gate **运行入口 = 当前 A**：无条件判定交框架 Gate；产品条件闸在 `ApprovalMiddleware`；ASK 续跑在接入层 `PendingAskStore`。`ApprovalRequestedEvent` / `ApprovalRecorder` 已软退役（主路径停发停写）。见 `docs/permission-gate-落地.md`。

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
| `infrastructure.agent` | `HarnessAgentFactory`、`McpToolsConfigFactory`、`PermissionContextFactory`、`CatalogToolRegistrar`、`WorkspaceFilesystem`、`HarnessAgentRegistry`、`AgentShutdown` | 给 GatewayBootstrap 提供 `configureCommon` / `customizeCompanion` / `customizeEcho`；公共项关 web/shell、**打开 Harness Memory**（`MemoryConfig` throttled 10m、不另配 model）、**`disableCompaction` / `disableToolResultEviction`**（companion 再挂危机闸；框架默认会挂 eviction，必须显式关）、**`disableDynamicSkills`**（框架默认动态 skill 开，必须显式关；工作区默认 skill 仍开、目录可空，不写种子、不开 SkillManage/Curator）、**`disableDynamicSubagents`**（框架默认动态子代理开，必须显式关；companion 另挂静态 `subagent-probe`，echo 不挂）、挂 BYPASS 权限上下文；**MCP**：`McpToolsConfigFactory` 把 `agentscope.runtime.mcp.servers` 转成 `ToolsConfig`；空清单不设 `toolsConfig`；仅 companion 装配 + 注册监听，echo 不连；`${ENV}` 占位展开；`required` 硬/软失败交给框架 Registrar。工具在 build 后挂。有状态库时工作区按**用户**隔离（`IsolationScope.USER`）。`HarnessAgentRegistry` 供 Middleware 回查长驻 Harness。停机时等待在途调用。Skills / Subagent / MCP 见对应 `docs/*-落地.md` |
| `infrastructure.embed` | `DashScopeTextEmbedder` | 密钥读 `DASHSCOPE_API_KEY`。空密钥或失败返回空向量，不让进程退出 |
| `config` | `AgentRuntimeProperties` | 前缀 `agentscope.runtime` |
| `config` | `RuntimeConfig` | 只打开配置绑定，不创建 Agent |
| `domain` | `CallIdentity`、`TextEmbedder`、`RecalledMemories`、`CrisisReplyGuard`、`PersonaConsistencyGuard` | 从这次 `RuntimeContext` 读租户、用户、会话，不用 ThreadLocal。嵌入锁 `text-embedding-v4` / 1024。`RecalledMemories` 仅旧 Companion 路径对照用。危机回复按玩笑、暧昧、扮演、危险方法四类词表检查，命中换成 `CrisisPrompt`。人设回复按恋人、助手、替人决定三类词表检查，命中换成短基线 |
| `domain.middleware` | `AuditMiddleware`、`ApprovalMiddleware`、`FileWriteLimitMiddleware`、`SafetyGateMiddleware`、`CrisisGatedToolResultEvictionMiddleware`、`CrisisGatedCompactionMiddleware`、`PersonaConsistencyMiddleware`、`CrisisGatedMemoryFlushMiddleware` | 执行策略。`ApprovalMiddleware` 只做产品条件闸（ALLOW 下放，ASK/DENY 丢掉；无条件判定交 Gate，不落单）。写文件超过 `max-write-bytes` 时丢掉，不调用接入层。关键词或语义判定到危机时换危机话术，升高时只加克制标记。已经成功 20 轮、本会话第 1 轮且已有画像/情景行，或用户纠正身份时打开人设检查标记，危机档不打开。**跨会话记忆权威为 Harness Memory**（`MEMORY.md` + flush/consolidation，与对话同模型，throttled 10m；`memory_*` 已开）。工厂 `disableMemoryHooks`，由 `CrisisGatedMemoryFlushMiddleware` 在非 CRITICAL 时触发框架 flush/maintenance；**CRITICAL 不入队**。**companion** 在 SafetyGate 后顺序挂：`CrisisGatedToolResultEvictionMiddleware`（`defaults()`）→ `CrisisGatedCompactionMiddleware`；二者均为工厂 disable 后产品闸，**CRITICAL 整段跳过**；echo 不挂。紧急溢出 `compactionHook` 未产品闸（已知限制）。`CompanionMemoryMiddleware` **已从对话路径拆除**（类保留对照）。读失败或没密钥不改变回复。见 `docs/memory-longterm-落地.md`、`docs/compaction-落地.md`、`docs/tool-result-eviction-落地.md`、`docs/permission-gate-落地.md` |
| `task` | `AgentTask` | 任务消息体。业务层与发布/消费共用，工厂和 Middleware 不感知 |
| `task.publish` | `AgentTaskPublisher`、`SyncTaskPublisher`、`RocketMqTaskPublisher` | 发布口；`mode=sync` 当前线程执行；`mode=mq` 启动即失败，topic 预留 `agentscope-tasks`，不引入 RocketMQ 依赖 |
| `task.handle` | `AgentTaskHandler`、`DispatchingAgentTaskHandler`、`DocumentIndexExecutor`、`SyncAgentTaskHandler` | 消费入口按 type 分发；`DOCUMENT_INDEX` 切块嵌入双写；其它类型只记审计；`SyncAgentTaskHandler` 留给测试直接构造 |
| `task.log` | `AgentTaskLog`、`AbsentAgentTaskLog` | 任务状态落库缝；MySQL 关闭时空实现（测试用，不是写失败降级） |
| `domain.knowledge` | `TextChunker`、`HybridChunkMerger`、`ChunkReranker` | 固定 500/50 字符切块。不做语义切块。Hybrid 合并：向量优先、全文补缺、按 id 去重。重排缝：失败退回原序截断 |
| `infrastructure.rerank` | `DashScopeChunkReranker` | 百炼 `qwen3.7-text-rerank`。固定 `dashscope.aliyuncs.com`；密钥读 `DASHSCOPE_API_KEY`，空密钥/超时/失败不抛；不记账、不占额度 |
| `infrastructure.tool` | `KnowledgeSearchTool` | Agentic `knowledge_search`。身份只从 `RuntimeContext` 取；Hybrid（向量 + FULLTEXT）合并最多 20 条，再 Rerank 到 5；向量失败降级全文，Rerank 失败退回 Hybrid 原序；只回 SUCCESS 切块。放在运行层是因为要嵌入和向量 |

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
| `agentscope.runtime.mcp.servers` | 空 map | companion MCP 清单（name → transport / command\|url / args / env 占位 / required 等）。空则不连；热更走 `rebuildAll()`；密钥用 `${ENV}`，不进 Nacos 明文。见 `docs/mcp-落地.md` |
| `agentscope.task.mode` | `sync` | 当前线程执行任务。`mq` 会拒绝启动 |

系统提示词来自接入层填好的 `AgentRequest.systemPrompt`。接入层丢掉请求体人设，按 `prompt.companion.system` 取值。工厂在空值时用产品兜底，避免空提示词进入模型。

业务工具实现 `ProductTool`。`toolId` 必须和 `@Tool` 的 name、目录里的标识一致。MySQL 打开时目录来自 `tool_registry` 的启用行。工厂在 `build` 之后把目录点名且有实现的工具注册到这次 Agent 自己的 Toolkit。目录为空，或实现不在目录里时，不注册。产品域有 `echo_text`（全局 `ASK`）；运行层有 `knowledge_search`（全局 `ALLOW`，种子见 `V35__knowledge_search_tool.sql`）。

`userId`、`sessionId`、`userText` 缺任何一个，抛 `BusinessException(BAD_REQUEST)`。

一次调用结束且回复带 token 用量时，`AuditUsageMeter` 写一条动作名为 `model.usage` 的审计，并写入 `token_usage`；若有 `DailyTokenAccumulator` 再累加当日 Redis token 计数。明细只有模型名、输入 token、输出 token 和耗时，不放提示词。没有用量就不记。审计或用量表写失败不改变已经生成的回复。命中 `model_price` 时按每百万 token 计价，没有命中时金额记 0。

每个 Agent 挂 `OtelTracingMiddleware`。`agentscope.otel.endpoint` 为空时不导出，默认对应环境变量 `OTEL_EXPORTER_OTLP_ENDPOINT`。配了才按 OTLP 上报 trace，服务名用 `OTEL_SERVICE_NAME`，缺省是 `agentscope-user`。度量和日志不导出。导出装不上时对话继续。

`mode=sync` 时，`SyncTaskPublisher` 先写 `agent_task` 再执行。MySQL 关闭时不落库。任务参数不入库。

切到 `agentscope.task.mode=mq` 之前要先做完这三步，否则 `RocketMqTaskPublisher` 会让进程起不来：

1. 在 `agent-runtime` 引入 `rocketmq-spring-boot-starter`。现在不要加。
2. 配置 `rocketmq.name-server`，地址只来自环境变量。不要把地址写进仓库。
3. 增加消费者，监听 topic `agentscope-tasks`，收到消息后调用现有的 `AgentTaskHandler.handle`。消息 key 用 `taskId`。不要再注册第二个 `AgentTaskHandler`。

## 还没有

按产品预构建多份 Builder。进出模型仍检查长度和未替换的 `{{变量}}`。关键词命中 `CRITICAL` 时换成危机话术，不再问语义判定。语义判定失败按 `ELEVATED`。干预写失败不改变话术。危机档同步和流式都检查输出红线，流式先攒齐再查，命中换成 `CrisisPrompt`，不另写干预。升高档不查输出红线。普通人设检查时流式仍推送文本增量，结束再按基线覆盖。会话已经成功 20 轮、本会话第 1 轮且表上仍有旧画像/情景行，或用户对准身份纠正时，按规则看人设漂移。短基线来自 `PersonaContract`，不调模型。纠正只认固定短句。标记只活这一轮。跨会话记忆权威是用户工作区 `MEMORY.md`（LLM flush，CRITICAL 不入队；`memory_*` 已开）。对话路径不再写情景表/向量/画像；`CompanionMemoryMiddleware` 与表/Milvus 集合为遗留。管理端无情景/画像 API。人设中间件仍可读旧行作跨会话触发。见 `docs/memory-longterm-落地.md`。companion 上下文治理：危机闸 Eviction（`defaults()`）→ Compaction（默认阈值，同对话模型）；CRITICAL 整段跳过；echo 不开。紧急 `compactionHook` 产品闸未做。见 `docs/compaction-落地.md`、`docs/tool-result-eviction-落地.md`。Skills（窄）：`configureCommon` 已 `disableDynamicSkills`；工作区默认 skill 仍开（不调 `disableDefaultWorkspaceSkills`）；仓库/启动不写 `AGENTS.md` 或 skill 种子；`load_skill` 面保留。SkillManage / ProposeSkill / Curator / Marketplace / Promotion 另块，本专题不开。见 `docs/skills-落地.md`。Subagent：`configureCommon` 已 `disableDynamicSubagents`；companion 仅静态 `subagent-probe`（ISOLATED / 继承权限 / 零工具 / `Mode.SUBAGENT`）；echo 不挂；不上 Remote / 多角色 / 动态恢复。嵌套靠探针零工具 + Mode 收敛，框架若仍向子代理注入 `agent_*` 则标已知限制。见 `docs/subagent-落地.md`。RocketMQ 客户端和消费者还没接。`stream` 返回 `Flux<AgentStreamEvent>`，SSE 控制器放在 `agent-access`，不要在本模块写控制器。不要再注册第二个 `AgentTaskHandler`。知识库删库/删文档与纯文本文件上传在接入层完成。Hybrid + 百炼 Rerank 已接。索引仍走 sync 发布器，只是入口 `@Async` 离开 HTTP 线程。对象存储在 `agent-object-store`（local / OSS / S3 / MinIO 可切换）。通用附件 multipart 写入和正文下载已在接入层。知识库删文档/删库时按 DOCUMENT 引用清对象。

Permission Gate **运行入口 = 当前 A**（专题 ①～④ 已收口）：`ApprovalMiddleware` 只问 `PermissionRuleService`；产品 ASK→DENY；无条件判定交 Gate；续跑与旧表软退役见 `docs/permission-gate-落地.md`。历史双闸 B 见 `docs/permission-engine-落地.md`。框架自 2.0.0 起把 `Hook` 标成删除，执行策略挂在 `Middleware` 上。MCP（①～③ 已收口）：无清单不连；有清单仅 companion 经 `ToolsConfig.mcpServers` 装配；不做 MCP 服务发现；不另建 MCP 默认 DENY。见 `docs/mcp-落地.md`。不宣称已硬删 `approval_record`，不宣称已接多 Channel / mq / Wakeup / Remote Subagent。
