# Memory 落地（已锁定 · 迁入 Harness）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：PermissionEngine 专题已收口；优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；MCP / 多 Channel / mq 仍后置 |
| 原生面 | **Memory** |
| LTM | **不做** `LongTermMemory` 适配器（forRemoval） |
| 跨会话终态 | **整段迁到 Harness Memory**：`MemoryConfig` flush → `memory/YYYY-MM-DD.md` → consolidation → `MEMORY.md`；开放 `memory_*` 工具 |
| 摘要策略 | **允许调对话模型**抽事实（废除旧「截断用户句不调模型」） |
| 工作区隔离 | **USER**（`IsolationScope.USER`）：跨会话共享 `MEMORY.md`；接受同用户多会话共用工作区 |
| Flush 触发 | **`FlushTrigger.throttled(10m)`** |
| 危机与 flush | **CRITICAL 绝对禁止入队 flush**；ELEVATED/NORMAL 走 throttled。闸在产品中间件侧 |
| Companion / 表 | **直接拆除**召回与写入（情景表/向量/画像注入）；不双轨。关系阶段另块再用 `MEMORY.md` 约定 |
| Flush 用模 | **与对话同模型**（不配 `MemoryConfig.model`，回落 Agent 模型） |
| 落地形态 | 文档锁定后按块接线；①～④ 对照结论保留作背景 |

## 改锁记录

1. 弃 LTM → Harness spike → 曾锁 Companion 权威 + `disableMemory*`（①～④）。  
2. 再改锁：接受 LLM 记忆，终态 **整段 Harness Memory**。  
3. 分叉收口：USER；throttled(10m)；CRITICAL 禁 flush；Companion 直拆；同对话模型。  
4. 迁入时：**撤销** `disableMemoryTools` / `disableMemoryHooks`；改掉 `sessionScoped()`。

## 明确不做（本专题）

1. 不引入 RocketMQ / MCP / 多 Channel / Subagent  
2. 不实现 `LongTermMemory` 适配器  
3. 不把危机策略塞进 Flush/Consolidator 内部  
4. 不恢复已删除 Hook 作产品主策略  
5. 不双写情景表；本专题不迁移历史情景行进 `MEMORY.md`  
6. 不另配独立记忆模型

## 对照块（已完成）

1. LTM STATIC 接线 spike — 对照  
2. LTM 身份可见性 spike — 对照  
3. Harness Memory 面探测 — 文件 + LLM  
4. Companion 收口 + disableMemory — **迁入时回滚 disable**

## 迁入落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **工作区改 USER + 打开 Harness Memory** — 已完成  
   `WorkspaceFilesystem.userScoped()`；`configureCommon` 撤销 disable、挂 `MemoryConfig` throttled(10m)；测试 `WorkspaceFilesystemTest`、`HarnessMemoryOpenedMigrationTest`。  
2. **危机禁 flush 闸** — 未开始  
   产品侧保证 CRITICAL 不入队 / 不生效（薄中间件或与 Safety 协作）；测 NORMAL 可记、CRITICAL 不记。  
3. **拆除 Companion 召回与写入** — 未开始  
   去掉 `CompanionMemoryMiddleware` 挂载与相关注入；更新 README；表/仓储可留库不强删迁移。  
4. **README / 专题收尾** — 未开始  
   写清权威 = 工作区 `MEMORY.md`；管理端情景 API 若仍在则文档标明遗留。

## 验收要点

- ①：USER 隔离；memory_* 可见；未 disable  
- ②：CRITICAL 无 flush 副作用；NORMAL/ELEVATED 不拦框架 throttled  
- ③：对话路径不再写/召回情景表；无双权威  
- ④：文档与工厂注释一致

## 状态

分叉已钉死；迁入 ① 已完成。下一块：迁入 ② 危机禁 flush 闸。若再改规范，先改本文再动代码。
