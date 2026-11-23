# agent-runtime

执行内核。每次请求创建一次 `HarnessAgent`，执行完就关闭。本模块不认识具体产品，也不提供 HTTP 接口。

坐标：`com.zhu:agent-runtime`。包名：`com.zhu.runtime`。

## 依赖

依赖 `agent-common`、`agent-semantic`、`agent-state`、`agent-vector`、`agent-cache-api`、MyBatis-Plus、`agentscope-harness`、`agentscope-extensions-model-dashscope`。事件审计写入、token 用量、安全干预、模型查找在本模块本地实现。测试依赖 `spring-boot-starter-test`（模块内纯单元测试点；见 `docs/bootstrap-test-sink-落地.md`）。

对 `agent-semantic` 的 Java 引用只能落在 `com.zhu.semantic.contract` 及其子包（`product` / `tool` / `permission` / `safety`）。不要 import `semantic.application` 或 `semantic.infrastructure`。

纯领域测在本模块 `src/test`（如 `domain` / `domain.knowledge` 下 Guard、切块、Hybrid 合并）。**新测优先写本模块**；不宣称 bootstrap 旧测已清完。

不能依赖 `agent-access`。Permission Gate **运行入口 = 当前 A**：无条件判定交框架 Gate；产品条件闸在 `ApprovalMiddleware`；ASK 活单与续跑在本模块 `PendingAskStore` / `PendingAskFacade`。旧审批表链路已硬删（见 `docs/approval-hard-delete-落地.md`）。见 `docs/permission-gate-落地.md`。

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

正式启动会挂上 `DistributedStore`。默认工作区是 `RemoteFilesystemSpec`，隔离粒度固定 `USER`（companion / echo 各自挂载；Remote 与 Docker 规格互斥）。`sandbox.enabled=true` 时仅 companion 换 `DockerFilesystemSpec`（缺 Store 或 `docker version` 失败则硬失败，不回退 Remote）；echo 仍 Remote。文件/快照进 MySQL（含 JDBC 沙箱快照与执行守卫），不落在本机盘。开关关闭时没有 Store Bean，只留给测试。见 `docs/sandbox-落地.md`。

停机时 `AgentShutdown` 让进程不再接新的 Agent 调用，并按 `timeout-seconds` 等待已经开始的调用。Web 容器使用 `server.shutdown=graceful`。容器的终止宽限不要短于这个等待时间。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api` | `package-info` | 对外端口根；按域分包，接入层只依赖本树 |
| `api.agent` | `AgentExecutor`、`AgentRequest`、`AgentResponse`、`AgentStreamEvent`、`ResidentAgentReloader`、`RebuildResult` | 对话执行缝与 DTO；WEB/API 通道；热更后重建长驻 Agent |
| `api.approval` | `PendingAskFacade`、`PendingAskSummary` | 待确认摘要与原子决定续跑 |
| `api.wakeup` | `WakeupPort` | 唤醒 enqueue 端口 |
| `api.session` | `SessionTurnCount` | 会话已经成功结束的轮次；人设检查只看这个数 |
| `api.usage` | `UsageMeter` | 模型用量出口 |
| `infrastructure.approval` | `PendingAskStore`、`PendingAsk`、`PendingAskFacadeService` | 进程内 ASK 仓（含 ToolUseBlock）；Facade 正式实现 |
| `application.turn` | `AgentTurnSupport`、`ResolvedChatModel`、`AbsentSessionTurnCount` | 校验 / RuntimeContext / 流式护栏收口与用量记账入口；模型坐标解析；MySQL 关闭时轮次按 0 |
| `application.stream` | `AgentStreamEvents`、`CrisisReplyOutlet` | 框架事件收成 `AgentStreamEvent`；危机档流式先扣增量、整段再过规则 |
| `application.usage` | `AuditUsageMeter`、`ModelCallUsage` | 模型用量写入审计与 `token_usage`；单次调用记账辅助 |
| `infrastructure.agent` | `HarnessAgentFactory`、`ResidentAgentBinding` / `ResidentAgentBindingRegistry`、`CompanionMiddlewareChain`、`McpToolsConfigFactory`、`CompanionSandboxFilesystem`、`DockerCliProbe`、`PermissionContextFactory`、`CatalogToolRegistrar`、`WorkspaceFilesystem`、`HarnessAgentRegistry`、`AgentShutdown` | 给 GatewayBootstrap 提供 `configureCommon` / `customizeEcho`；**产品 Agent 经 Binding 注册表挂载**（companion 实现在 `agent-product-companion`）；公共项关 web/shell、**打开 Harness Memory**、若干 disable*、BYPASS 权限；MCP/Sandbox 零件供 Binding 使用；echo 仍平台挂。见各 `docs/*-落地.md` |
| `infrastructure.gateway` | `ChannelGateway`、`ApiChannel`、`RoomNormalizedSessionTurnGate`、`AgentChannelRuntimeContextResolver`、`ResidentAgentDrain` | 长驻 companion+echo；白名单 `{chatui,api}`；room 归一化闸；实现 `AgentExecutor`/`ResidentAgentReloader`。见 `docs/channel-gateway-落地.md`、`docs/api-channel-落地.md` |
| `infrastructure.wakeup` | `InMemoryMessageBus`、`WakeupCoordinator` | 进程内 Bus + Dispatcher；实现 `WakeupPort`；Target 懒解析 `ChannelGateway`。见 `docs/wakeup-落地.md` |
| `infrastructure.embed` | `DashScopeTextEmbedder` | 密钥读 `DASHSCOPE_API_KEY`。空密钥或失败返回空向量，不让进程退出 |
| `config` | `AgentRuntimeProperties` | 前缀 `agentscope.runtime` |
| `config` | `RuntimeConfig` | 只打开配置绑定，不创建 Agent |
| `domain` | `CallIdentity`、`TextEmbedder`、`CrisisReplyGuard`、`PersonaConsistencyGuard` | 从这次 `RuntimeContext` 读租户、用户、会话，不用 ThreadLocal。嵌入锁 `text-embedding-v4` / 1024。危机回复按玩笑、暧昧、扮演、危险方法四类词表检查，命中换成 `CrisisPrompt`。人设回复按恋人、助手、替人决定三类词表检查，命中换成短基线 |
| `domain.middleware` | `AuditMiddleware`、`ApprovalMiddleware`、`FileWriteLimitMiddleware`、`SafetyGateMiddleware`、`CrisisGatedToolResultEvictionMiddleware`、`CrisisGatedCompactionMiddleware`、`PersonaConsistencyMiddleware`、`CrisisGatedMemoryFlushMiddleware` | 执行策略。`ApprovalMiddleware` 只做产品条件闸（ALLOW 下放，ASK/DENY 丢掉；无条件判定交 Gate，不落单）。写文件超过 `max-write-bytes` 时丢掉，不调用接入层。关键词或语义判定到危机时换危机话术，升高时只加克制标记。已经成功 20 轮或用户纠正身份时打开人设检查标记，危机档不打开。**跨会话记忆权威为 Harness Memory**（`MEMORY.md` + flush/consolidation，与对话同模型，throttled 10m；`memory_*` 已开）。工厂 `disableMemoryHooks`，由 `CrisisGatedMemoryFlushMiddleware` 在非 CRITICAL 时触发框架 flush/maintenance；**CRITICAL 不入队**。**companion** 在 SafetyGate 后顺序挂：`CrisisGatedToolResultEvictionMiddleware`（`defaults()`）→ `CrisisGatedCompactionMiddleware`；二者均为工厂 disable 后产品闸，**CRITICAL 整段跳过**；echo 不挂。紧急溢出 `compactionHook`：**不装**（公开 API 无法只装 hook，装则双压打穿危机闸；溢出硬失败；spike 见 `docs/compaction-hook-落地.md` 口径 C）。情景/画像遗留已硬删（见 `docs/memory-hard-delete-落地.md`）。读失败或没密钥不改变回复。见 `docs/memory-longterm-落地.md`、`docs/compaction-落地.md`、`docs/compaction-hook-落地.md`、`docs/tool-result-eviction-落地.md`、`docs/permission-gate-落地.md` |
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
| `agentscope.runtime.sandbox.enabled` | `false` | companion 是否换 DockerFilesystem。true 时缺 Store / Docker CLI 硬失败。热更走 `rebuildAll()`。见 `docs/sandbox-落地.md` |
| `agentscope.runtime.sandbox.image` | 空（接线回落 `ubuntu:24.04`） | Docker 镜像 tag；可 Nacos 覆盖。拉取凭证不进本字段 |
| `agentscope.runtime.wakeup.enabled` | `false` | 是否启进程内 Wakeup Bus + Dispatcher（`WakeupCoordinator` 在本模块）。true 时令牌必填且非占位。见 `docs/wakeup-落地.md` |
| `agentscope.task.mode` | `sync` | 当前线程执行任务。`mq` 会拒绝启动 |

系统提示词来自接入层填好的 `AgentRequest.systemPrompt`。接入层丢掉请求体人设，按 `prompt.companion.system` 取值。工厂在空值时用产品兜底，避免空提示词进入模型。

业务工具实现 `ProductTool`。`toolId` 必须和 `@Tool` 的 name、目录里的标识一致。MySQL 打开时目录来自 `tool_registry` 的启用行。工厂在 `build` 之后把目录点名且有实现的工具注册到这次 Agent 自己的 Toolkit。目录为空，或实现不在目录里时，不注册。产品域有 `echo_text`（全局 `ASK`）；运行层有 `knowledge_search`（全局 `ALLOW`，种子见 `V35__knowledge_search_tool.sql`）、`attachment_read`（全局 `ALLOW`，种子见 `V43__attachment_read_tool.sql`；经 `UserAttachmentTextPort` 读 PDF/docx/纯文本）。

`AgentRequest` 可带本轮 `images`（Base64）；`ChannelGateway` 组 `ImageBlock`，失败降级纯文本并提示「未能看图」，不自动换模。

`userId`、`sessionId`、`userText` 缺任何一个，抛 `BusinessException(BAD_REQUEST)`。

一次调用结束且回复带 token 用量时，`AuditUsageMeter` 写一条动作名为 `model.usage` 的审计，并写入 `token_usage`；若有 `DailyTokenAccumulator` 再累加当日 Redis token 计数。明细只有模型名、输入 token、输出 token 和耗时，不放提示词。没有用量就不记。审计或用量表写失败不改变已经生成的回复。命中 `model_price` 时按每百万 token 计价，没有命中时金额记 0。

每个 Agent 挂 `OtelTracingMiddleware`。`agentscope.otel.endpoint` 为空时不导出，默认对应环境变量 `OTEL_EXPORTER_OTLP_ENDPOINT`。配了才按 OTLP 上报 trace，服务名用 `OTEL_SERVICE_NAME`，缺省是 `agentscope-user`。度量和日志不导出。导出装不上时对话继续。

`mode=sync` 时，`SyncTaskPublisher` 先写 `agent_task` 再执行。MySQL 关闭时不落库。任务参数不入库。

切到 `agentscope.task.mode=mq` 之前要先做完这三步，否则 `RocketMqTaskPublisher` 会让进程起不来：

1. 在 `agent-runtime` 引入 `rocketmq-spring-boot-starter`。现在不要加。
2. 配置 `rocketmq.name-server`，地址只来自环境变量。不要把地址写进仓库。
3. 增加消费者，监听 topic `agentscope-tasks`，收到消息后调用现有的 `AgentTaskHandler.handle`。消息 key 用 `taskId`。不要再注册第二个 `AgentTaskHandler`。
## 已交付（摘要）

- 安全与人设：关键词/语义定档；危机输出红线；人设纠正短句或满 20 轮；流式增量可先扣后盖。图见 [`docs/diagrams/chains.md`](../docs/diagrams/chains.md)
- Memory：用户工作区 `MEMORY.md` + 危机闸 flush；`memory_*` 已开；情景/画像 **已硬删**（[`memory-longterm-落地.md`](../docs/memory-longterm-落地.md)、[`memory-hard-delete-落地.md`](../docs/memory-hard-delete-落地.md)）
- 上下文：危机闸 Eviction → Compaction；echo 不挂（[`compaction-落地.md`](../docs/compaction-落地.md)、[`tool-result-eviction-落地.md`](../docs/tool-result-eviction-落地.md)）
- Skills 窄面：`disableDynamicSkills`；工作区默认 skill 可空；`load_skill` 保留（[`skills-落地.md`](../docs/skills-落地.md)）
- Subagent：动态关；companion 静态 `subagent-probe`（[`subagent-落地.md`](../docs/subagent-落地.md)）
- Permission Gate 运行入口 A + `PendingAsk*`；旧审批链路硬删（[`permission-gate-落地.md`](../docs/permission-gate-落地.md)、[`approval-hard-delete-落地.md`](../docs/approval-hard-delete-落地.md)）
- MCP / Sandbox / Wakeup 接线已收口（默认关或空清单不连；见对应落地文）
- 知识：`knowledge_search` Hybrid + Rerank；索引 sync 发布 + 入口 `@Async`；删库/附件 multipart 在接入层
- 聊天附件：本轮多模态入模 + `attachment_read` 按需读文档
- 图册：[`docs/diagrams/`](../docs/diagrams/README.md)

## 已知限制（刻意不做）

- 紧急 `compactionHook`：**不装**（口径 C；[`compaction-hook-落地.md`](../docs/compaction-hook-落地.md)）
- Skills manage / Curator / Marketplace / Promotion：**不开**（[`skills-curator-落地.md`](../docs/skills-curator-落地.md)）
- 动态 Subagent / 多角色工人：**不上**；Remote **仅学习声明面**，工厂不挂（[`remote-subagent-落地.md`](../docs/remote-subagent-落地.md)）
- MCP **服务发现**、Sandbox 私有仓库拉取 / 沙箱体积旋钮 / echo 扩事件、Wakeup 调度表·Inbox·TeamWakeups·跨副本 Bus：**不做**
- `mode=mq`：启动失败；不引入 RocketMQ 依赖（切 mq 前须另专题完成客户端与消费者）
- 本模块不写 HTTP/SSE 控制器；不注册第二个 `AgentTaskHandler`
- 不按产品预构建多份常驻 Builder（每次请求 build）

## 还没有

- RocketMQ 客户端与 `agentscope-tasks` 消费者（仅当未来真开 `mode=mq`）
- 产品级 Remote Subagent（鉴权、超时、失败语义、工厂旋钮）
- Skills manage/Curator **产品开启路径**（若另 grill 锁定）
- 框架若仍向子代理注入 `agent_*`：继续标已知限制，本专题不宣称已根治

