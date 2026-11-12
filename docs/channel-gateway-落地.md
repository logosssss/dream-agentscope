# Channel Gateway 落地（已锁定）

日期：2026-11-11。分支：`feat/agentscope-channel-gateway`。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 主路径 | 仍走 `ChatUiChannel` → Gateway |
| 同 session 串行 | 进程内 `SessionTurnGate` / `LocalSessionTurnGate`；启动后**显式** `setSessionTurnGate` |
| 业务上下文 | 用 `runtimeContextResolver` 写 `tenantId` 等；不要靠往 `SendOptions` / `MsgContext.extra` 塞全量字段 |
| Channel 管理 | `ChannelManager` **只读显式**（看见默认 ChatUi 即可），本期不注册多 Channel |
| Agent 创建 | 改由 `GatewayBootstrap` Builder 创建长驻 Agent；工厂只留 `customizeCompanion` / `customizeEcho`（或等价钩子） |
| `configureAllAgents` | 只挂真正公共的项；产品差异不塞进去 |

## 落地顺序

再说「继续 / 落地」时按序做，一次一块：

1. **显式 SessionTurnGate + 同 session 并发测试** — 已完成  
   `AccessChannelGateway.startGatewayLocked` 在 `start()` 后显式 `setSessionTurnGate(new LocalSessionTurnGate())`。  
   `SessionTurnGateTest`：闸本身阻塞 + ChatUi 同 session 并发占轮 = 1。
2. **resolver（tenantId 等）** — 已完成（本块）  
   `AccessChannelRuntimeContextResolver` 挂在 Gateway；`SendOptions` 只带 `staging(tenantId, agentId)`。  
   护栏收口仍用本地 `turns.context(request)`。`AccessChannelRuntimeContextResolverTest` 覆盖暂存与 resolve。
3. **ChannelManager 只读显式** — 已完成（本块）  
   Builder `.channel(ChatUiChannel.create())` 只挂一条；启动后 `requireChatUi(channelManager)` 只读取回。  
   名单必须恰好 `chatui`。`AccessChannelManagerTest` 覆盖取回与空 manager 拒绝。
4. **Bootstrap Builder 建 Agent** — 已完成（本块）  
   Gateway 用 `.configureAllAgents(configureCommon).agent(..., customizeCompanion/Echo)`；工厂不再 `build*Resident`。  
   公共项：关 web/shell、追踪、停机闸、状态库/会话文件系统。搭子工具在 build 后 `attachCompanionTools`。  
   `AccessChannelBootstrapAgentTest` 覆盖公共项先跑、两 Agent 键与路由。

## 明确不做

- 直调 `Gateway.run` / `runStream` 替代 ChatUi
- Redis turn / 分布式 gate
- 多 Channel、Subagent、Wakeup
- 本期引入 RocketMQ 客户端或切 `mode=mq`

## 分支现状

- ① 已收口：显式 `LocalSessionTurnGate` + `SessionTurnGateTest`。
- ② 已收口：`setRuntimeContextResolver` + staging；本地护栏上下文与 Gateway 业务暂存分开。
- ③ 已收口：Builder 显式挂唯一 ChatUi；运行期只从 `ChannelManager` 读取，不再 `chatUiChannel()` 另造。
- ④ 已收口：Bootstrap Consumer 建 Agent；工厂只留 `configureCommon` / `customizeCompanion` / `customizeEcho` / `attachCompanionTools`。

## 验收要点

- 同 session 两路并发：闸键相同，并发占轮 = 1
- 对话主路径仍经 ChatUi；护栏 / 用量收口不依赖把全量 `RuntimeContext` 塞进 `SendOptions`
- 进程仍可不配模型密钥启动；首次对话再懒启 Gateway
- Agent 由 Bootstrap Consumer 创建；`configureAllAgents` 不含产品中间件；搭子工具 build 后挂

## 状态

①～④ 已按序收口。后续若开新块，另起 grill / 文档条目，不要把「明确不做」项混进来。
