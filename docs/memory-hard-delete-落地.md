# Memory 情景/画像硬删落地（已锁定）

日期：2026-11-16。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Memory 迁入 Harness 已收口（见 `docs/memory-longterm-落地.md`）；跨会话权威是工作区 `MEMORY.md`；对话主路径已不写/不召回情景表与画像。  
本专题做**债清理硬删**：表 / `api.memory` / Middleware / `RecalledMemories` 已删；Milvus `episodic_memory` 停建；`PersonaConsistencyMiddleware` 仅纠正短句 + 满 20 轮。不升产品能力。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 主轴 | **H**：债清理；硬删 Memory 情景/画像遗留链路 |
| 范围 | **S1**：Flyway DROP 表 + 删仓储/Mapper/`CompanionMemoryMiddleware`；**停建** Milvus `episodic_memory`；人设触发改走非表路径 |
| 人设触发 | **P1**：砍掉 `remembered()`；`PersonaConsistencyMiddleware` **只留**纠正短句 + 满 20 轮；不再依赖画像/情景仓储 |
| 存储 | **F1**：Flyway **直接 DROP** `episodic_memory` / `user_profile`；代码停建集合；**不**在启动时 `dropCollection`（孤儿集合运维手工清，文档注明） |
| 文档/切片 | **C1**：另开本文；`memory-longterm-落地.md` 互指；① 锁文 → ② Flyway+后端 → ③ README/handbook |
| 权威不变 | 跨会话记忆仍为用户工作区 `MEMORY.md`（Harness flush/consolidation，`memory_*`） |

## 明确不做（本专题 · X1–X5）

1. **X1** 不改 `MEMORY.md` / `CrisisGatedMemoryFlush` / `memory_*` 权威与装配  
2. **X2** 不把历史情景/画像行迁移进 `MEMORY.md`  
3. **X3** 不借本片做产品 Remote、`mode=mq`、MCP/Sandbox/Wakeup/Curator 开启  
4. **X4** 不探工作区 `MEMORY.md` 做人设触发；不整段卸下人设中间件  
5. **X5** 不删/不停建知识库集合 `knowledge`；不强制大搬家无关 bootstrap 测  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **锁定文** — 已完成  
   本文；`memory-longterm-落地.md` 互指「硬删见另文」。不改代码、不迁库。  
2. **Flyway + 后端清枝** — 已完成  
   `V41__drop_legacy_companion_memory.sql`：`DROP episodic_memory` / `user_profile`。  
   已删 `api.memory`、Mysql·Absent、Mapper/实体/XML、`CompanionMemoryMiddleware`、`RecalledMemories`、`MemoryFilter` 与对照测。  
   `MilvusCollections` 只 ensure `knowledge`；`MilvusVectorStore` 仅知识过滤。  
   `PersonaConsistencyMiddleware` 仅纠正 + 满 20 轮。不改 Memory flush / `memory_*`。  
3. **README / handbook 收口** — 已完成  
   state / vector / runtime / bootstrap / access README、handbook、工程评估文与本文一致：表与遗留集合已硬删/停建；人设触发仅纠正+20 轮；不宣称已迁历史行 / 已开 mq 等。

## 验收要点

- ①：锁文与 grill 一致；长期记忆文互指；无代码变更  
- ②：无表/仓储读写真路径；人设中间件无表依赖；启动不建 `episodic_memory`  
- ③：文档不宣称半截遗留；`MEMORY.md` 权威口径不变  

## 状态

grill 已结束；落地 **①～③ 已完成**。本专题收口。若再改规范，先改本文再动代码。
