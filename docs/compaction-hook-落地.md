# CompactionHook 紧急溢出落地（已锁定 · 窄范围 · 文档收口）

日期：2026-11-13。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Compaction 常规危机闸已收口（见 `docs/compaction-落地.md`）；  
紧急溢出 `compactionHook` 原标已知限制。本专题 spike 后二次 grill，改为 **文档收口（口径 C）**。  
优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定（二次 grill 后）

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；多 Channel / mq 等仍后置 |
| 问题 | `disableCompaction()` 清空 `compactionHook`；溢出时框架硬失败（`no compaction configured`） |
| 成功口径 **C** | **不装**紧急 hook；保持 `disableCompaction` + `CrisisGatedCompactionMiddleware`；以 spike 证明「公开 API 无法只装 hook」并文档化 |
| 常规路径 | **保留**危机闸（CRITICAL 整段跳过）；不因本专题打穿 |
| 装配 | **不改**工厂主路径（companion / echo 仍现状） |
| 历史口径 B′ | 曾拟 `compaction(config)` + 保留危机闸；**spike 证伪可干净落地**，已废弃 |
| 双压策略 | 曾约「拆不开则阻断回 grill」→ 二次 grill 选 **C** 解阻 |
| 落地形态 | **①** spike（已完成）→ **②** 接线（**取消**）→ **③′** 文档收口（README/工厂注释） |
| 测试 | 保留 `CompactionHookSurfaceSpikeTest` 作机制对照；不连真中间件 |

## 框架硬约束

1. 溢出时仅判断 `getCompactionHook() != null`，再 `ConversationCompactor.compactIfNeeded`  
2. **不经过** `CrisisGatedCompactionMiddleware.onReasoning`，**不读** `RiskLevel`  
3. `compactionHook` 在 Agent 构建时 **final**，不能按轮次摘装  
4. **① 追加**：`compaction(config)` 会把 **同一** `CompactionMiddleware` 挂进 middleware 链；无法只装 hook  

## ① spike 结论（2026-11-13）

对照测试：`CompactionHookSurfaceSpikeTest`（不改工厂主路径）。

| 结论 | 证据 |
| --- | --- |
| `compaction(默认 config)` → hook **非空**，类型为 `CompactionMiddleware` | `compactionConfig_installsEmergencyHook` |
| **同一对象**也被挂进 `getDelegate().getMiddlewares()` | `compactionConfig_alsoAddsCompactionMiddlewareToChain_sameInstanceAsHook` |
| 公开 API **无法**「只装 hook、不挂链」 | 上条；Builder 仅有 `compaction` / `disableCompaction` |
| 与 `CrisisGatedCompactionMiddleware` 同挂 → 链上 **同时**存在未闸 `CompactionMiddleware` 与危机闸 | `compactionPlusCrisisGate_ungatedCompactionRemainsOnChain` |
| hook 字段 **final** | `compactionHookField_isFinal` |
| 紧急路径仍不读 RiskLevel | 既有字节码对照（`forceCompactAndRetry`） |

## 明确不做（本专题）

1. 不改常规路径危机闸语义（CRITICAL 仍跳过）  
2. **不**调用 `compaction(config)` 装紧急 hook（会双压 / 打穿常规闸）  
3. 不宣称紧急路径能按 CRITICAL 跳过  
4. 不把 compaction 旋钮放进 Nacos  
5. 不上多 Channel / `mode=mq` / Skills Curator / Remote Subagent  
6. 不 fork / 不改 `agentscope-harness` 源码  
7. 不删 `CompactionHookSurfaceSpikeTest`  

## 落地顺序（一次一块）

1. **spike** — 已完成  
   `CompactionHookSurfaceSpikeTest`；结论见上。  
2. **companion 接线** — **取消**（口径 C）  
3. **③′ 文档收口** — 已完成  
   `agent-runtime` README、`HarnessAgentFactory` / `CrisisGatedCompactionMiddleware` 注释已对齐口径 C；  
   保留 `CompactionHookSurfaceSpikeTest`。  

## 验收要点

- ①：hook / 双压 / CRITICAL 关系有书面结论与 spike 测  
- ②：已取消，无装配变更  
- ③′：文档与 README/注释一致；不宣称已恢复紧急 hook；保留 spike 测  

## 状态

二次 grill 已结束；口径 **C** 定稿。① 与 ③′ 已完成；② 取消。本专题收口。若再改规范，先改本文再动代码。
