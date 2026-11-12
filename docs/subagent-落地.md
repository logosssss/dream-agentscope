# Subagent 落地（已锁定）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Memory / Compaction / Eviction / Skills（窄）/ Permission Gate 已收口；优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；MCP / 多 Channel / mq / Wakeup 仍后置 |
| 原生面 | **Subagent**：关动态；companion 挂 **1** 个最小静态声明 |
| 动态子代理 | **`disableDynamicSubagents()`** 放 `configureCommon`（companion + echo）。框架默认开，必须显式关 |
| 静态声明 | **仅 companion** 挂一份；**echo 不挂** |
| 工作区 | **`WorkspaceMode.ISOLATED`**（不与父用户 `MEMORY.md` 树 SHARED） |
| 权限 | **`inheritParentPermissions(true)`**（沿用父 BYPASS / Gate 规则面） |
| 工具 | **零工具**（白名单空；不挂业务工具、不挂读写文件工具） |
| 身份 | **`subagent-probe`**：对照用占位；提示词极简「直接短答」；不承诺产品能力 |
| 会话 | **`persistSession(false)`**（沿用框架 Builder 默认 false，显式写清） |
| 嵌套 | **禁止（产品意图）**：探针 `Mode.SUBAGENT` + 空 `tools` 收敛；**已知限制**：若框架仍向子代理注入 `agent_*`，则无法在本专题硬禁嵌套 spawn |
| 落地形态 | ① spike → ② 工厂接线 → ③ README / 专题收口 |

## 明确不做（本专题）

1. 不上 Remote 子代理（url / remoteStreaming / RemoteAskPolicy）  
2. 不挂多角色 / 多份 `SubagentDeclaration`  
3. 不恢复动态 Subagent；不用 `disableSubagents()` 整面杀掉静态面  
4. 子代理不开 Skills manage / Curator；不挂 skill 名单（零工具已覆盖）  
5. 不上 MCP / 多 Channel / Wakeup / `mode=mq`  
6. 不补紧急 `compactionHook`；不硬删审批表  
7. 不改 Memory / Compaction / Eviction / Skills / Permission Gate 已锁约定  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **对照 spike** — 已完成  
   `SubagentSurfaceSpikeTest`：  
   - 默认（零静态声明）挂 `DynamicSubagentsMiddleware`，有 `agent_spawn` / `agent_send` 等；无静态条目时不一定挂 `SubagentsMiddleware`。  
   - `disableDynamicSubagents` 后无 Dynamic 中间件；`agent_*` 工具仍可在；未声明则无 `subagent-probe`。  
   - 关动态 + `subagent(probe)`：`AgentManager.hasAgent` / `getDeclaration` 可召回；ISOLATED、继承权限、`persistSession=false`、tools 空、非 Remote。  
   - 对照：`disableSubagents` 整面关掉（本专题不用）。不改工厂主路径。  
2. **工厂接线** — 已完成  
   `HarnessAgentFactory.configureCommon`：`disableDynamicSubagents()`。  
   `customizeCompanion`：`subagent(subagentProbe())`（`SUBAGENT_PROBE_NAME` / ISOLATED / 继承权限 / 零工具 / `Mode.SUBAGENT` / `persistSession=false`）。  
   `customizeEcho` 不挂声明。测：`DisableDynamicSubagentsWiringTest`。  
3. **README / 专题收口** — 已完成  
   `agent-runtime/README.md` 与 `HarnessAgentFactory` 注释写清：动态已关、companion 仅 `subagent-probe`、echo 不挂。  
   明确不做 Remote / 多角色 / 动态恢复；嵌套为已知限制（见上表）。

## 验收要点

- ①：动态关断与静态可召回有对照结论（可丢弃 spike）  
- ②：common 已 `disableDynamicSubagents`；companion 仅一探针声明；echo 无声明  
- ③：文档与代码一致；不宣称已接 Remote / 多角色 / 动态恢复  

## 状态

grill 已结束；落地 ①～③ 已完成。本专题收口。若再改规范，先改本文再动代码。
