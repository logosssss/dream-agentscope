# Compaction 落地（已锁定）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Memory 专题已收口；优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；MCP / 多 Channel / mq 仍后置 |
| 原生面 | **Compaction**（对话上下文压缩；压前可 flush 进日流水） |
| 与危机 | **CRITICAL 整段跳过 compact**（不 flush、不摘要、不改消息列表）；ELEVATED/NORMAL 走框架 compact |
| 压前 flush | **保持** `flushBeforeCompact=true`（框架默认）。压前 flush 走 `MemoryFlushManager`，**不经** `CrisisGatedMemoryFlushMiddleware`，故必须靠本专题危机闸整段跳过 |
| 摘要用模 | **与对话同模型**（不配 `CompactionConfig.model`） |
| 触发 / 保留 | **框架默认**（约 `triggerMessages=50`、`keepMessages=20` 等）；不先抄指南示例 30/10 |
| 挂载 | **`disableCompaction()`** + 产品 `CrisisGatedCompactionMiddleware`；非 CRITICAL 再委托 `CompactionMiddleware.onReasoning`。对齐 Memory 的 `disableMemoryHooks` 模式 |
| 范围 | **仅 companion**；echo 保持禁用且不挂闸。`disableCompaction` 放 `configureCommon`，闸挂在 `customizeCompanion`（须在 `SafetyGate` 之后） |
| Tool eviction | **本专题不做**；已另开并收口，见 `docs/tool-result-eviction-落地.md` |
| 落地形态 | 文档锁定后按块：① spike → ② 接线 → ③ README/专题收口 |

## 明确不做（本专题）

1. 不接 Tool Result Eviction  
2. 不上 Subagent / MCP / 多 Channel / `mode=mq`  
3. 不切 Permission 终态 Gate  
4. 不把 compaction 旋钮放进 Nacos 热更白名单（本地/代码配置即可）  
5. 不在本专题补齐「紧急溢出 `compactionHook`」产品闸（历史已知限制；收口见另文 [`compaction-hook-落地.md`](compaction-hook-落地.md)）  
6. 不改 Memory 已锁约定（USER、throttled 10m、CRITICAL 禁 flush、同对话模型）

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **对照 spike** — 已完成  
   `CompactionSurfaceSpikeTest`：默认 `flushBeforeCompact=true`、无独立 model、trigger/keep≈50/20；  
   `ConversationCompactor` 依赖 `MemoryFlushManager`（压前 flush 不经产品 Memory 闸）；  
   `disableCompaction` 清空 `compactionHook`，`compaction(config)` 才安装；  
   条数触发时 `compactIfNeeded` 会调模型。不改工厂主路径。  
2. **disable + 危机闸接线** — 已完成  
   `configureCommon`：`disableCompaction()`。  
   companion：`CrisisGatedCompactionMiddleware`（CRITICAL 直接 `next`；否则委托 `CompactionMiddleware.onReasoning`，默认 config）。  
   经 `HarnessAgentRegistry` 回查 Harness（与 Memory 闸相同）；挂在 SafetyGate 之后。  
   测试：`CrisisGatedCompactionMiddlewareTest`。  
3. **README / 专题收尾** — 已完成  
   `agent-runtime/README.md` 与 `HarnessAgentFactory` 注释写清：companion 开危机闸 compact；echo 不开；  
   紧急溢出 `compactionHook` 原为已知限制，收口跟踪见 [`compaction-hook-落地.md`](compaction-hook-落地.md)；Tool Result Eviction 见 `docs/tool-result-eviction-落地.md`。

## 验收要点

- ①：默认行为与压前 flush 路径有对照结论（可丢弃 spike 测）  
- ②：CRITICAL 不进入 `CompactionMiddleware`；NORMAL/ELEVATED 可委托；echo 无闸、仍 disable  
- ③：文档与工厂注释一致；不宣称已闸紧急 `compactionHook`

## 状态

grill 已结束；落地 ①～③ 已完成。本专题（常规危机闸）收口。  
紧急 `compactionHook` 另见 [`compaction-hook-落地.md`](compaction-hook-落地.md)。若再改规范，先改本文再动代码。
