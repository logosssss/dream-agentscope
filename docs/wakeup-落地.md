# Wakeup 落地（已锁定 · 窄范围）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Memory / Compaction / Eviction / Skills / Permission Gate / Subagent / MCP / Sandbox 已收口；  
紧急 `compactionHook` 危机闸 **本轮跳过**（仍为 Compaction 已知限制）。优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；多 Channel / `mode=mq` 仍后置 |
| 原生面 | **Harness Wakeup**：`MessageBus.enqueueWakeup` + `WakeupDispatcher` + `WakeupTarget` |
| 非目标 | **不做**管理端 `agent_task_schedule` 真消费；**不挂** `InboxMiddleware`；**不上** `TeamWakeups` 产品面 |
| MessageBus | **进程内** `InMemoryMessageBus`（框架 `MessageBus` 契约）；不接 Redis/MySQL Bus；不借 RocketMQ |
| 装配范围 | **仅 companion**；echo 不接唤醒 |
| 默认姿态 | **`agentscope.runtime.wakeup.enabled=false`**；`true` 才建 Bus 并 `WakeupDispatcher.start()` |
| 硬失败 | `enabled=true` 但装配/启动 Dispatcher 失败 → **启动/重建失败**；不静默假装在听 |
| 配置 / 热更 | `agentscope.runtime.wakeup.*` 进 **业务 yml + Nacos 白名单可热更**；非法热更保留上一份 |
| 触发源 | 用户进程学习入口 + 单测可直接 enqueue；**不上** cron / 调度表扫描 |
| HTTP 入口 | 用户进程 `POST /api/internal/wakeups`（**不在** agent-admin；管理进程无 Harness/Bus） |
| 令牌 | **`AGENTSCOPE_WAKEUP_TOKEN`**（与 Caller / Admin 都不同）；未配或错误则入口拒收 |
| 忙会话 | `isSessionRunning`（或占轮中）→ **跳过并打日志**；不阻塞 Dispatcher、不强制并行、不自建重试队列 |
| 可观测 | **只打结构化日志**；不新增 echo/SSE 事件；不强制写审计表 |
| 落地形态 | **①** 锁文档 + 配置骨架 → **②** Bus + Dispatcher + companion Target + 内部 HTTP → **③** 单测（无真 MQ）与 README |
| 测试 | **不连**真实 Redis 队列 / RocketMQ / Nacos；Bus 用进程内实现 |

## 配置前缀

- `agentscope.runtime.wakeup.enabled`（默认 `false`；业务白名单可热更）
- 令牌：`agentscope.wakeup.token` ← `AGENTSCOPE_WAKEUP_TOKEN`（infra / 环境变量；**不进**业务热更明文）

## 明确不做（本专题）

1. 不做管理端调度表真消费；不把唤醒入口挂到 `agent-admin`  
2. 不挂 InboxMiddleware；不上 Team / 多 Channel / `mode=mq`  
3. 不接跨副本持久化 MessageBus  
4. 不复用 Caller Token 打内部唤醒；不 loopback 免鉴权  
5. session 忙时不阻塞重试、不打穿 SessionTurnGate  
6. 不借本专题补紧急 `compactionHook`；不改 Memory / Compaction / Sandbox 等已锁约定  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **锁定文 + 配置骨架** — 已完成  
   本文；`AgentRuntimeProperties` 嵌套 `wakeup`（`enabled` 默认 false）；  
   `nacos-allowlist.yml` 前缀 `agentscope.runtime.wakeup`；本地 / deploy 样例默认关；  
   单测只验缺省与白名单，不启 Dispatcher。  
2. **Bus + Dispatcher + Target + 内部 HTTP** — 已完成  
   进程内 `InMemoryMessageBus`；`WakeupCoordinator` 在 `enabled=true` 时启 `WakeupDispatcher`；  
   Target 懒解析 `AccessChannelGateway.harnessGatewayOrNull()`（companion / main）；  
   `POST /api/internal/wakeups` + `X-Wakeup-Token`（`AGENTSCOPE_WAKEUP_TOKEN`）；  
   Caller 过滤器跳过该路径；忙则框架 Dispatcher 跳过；令牌/启动失败硬退出。  
3. **单测 + README / 专题收口** — 已完成  
   `WakeupTokens` / Filter / Bus / Coordinator 单测（无真 MQ）；  
   根 / bootstrap / runtime / access / common README、`docs/release-gate.md` 与本文一致：  
   默认关；仅 companion；内部 HTTP + 独立令牌；忙跳过；硬失败无静默；  
   不宣称已接调度表消费 / Inbox / TeamWakeups / 多 Channel / `mode=mq`。

## 验收要点

- ①：锁文档与白名单/属性骨架一致；默认关；令牌不进 Nacos 明文  
- ②：仅 companion；内部 HTTP 在用户进程；忙跳过；硬失败无静默  
- ③：文档与代码一致；测试不连真 MQ / 外部总线  

## 状态

grill 已结束；落地 ①②③ 已完成。本专题收口。若再改规范，先改本文再动代码。
