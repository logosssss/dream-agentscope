# Tool Result Eviction 落地（已锁定）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Compaction 专题已收口，并明确 eviction「另块」；优化主轴仍为 **框架原生对齐（学习向）**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；MCP / 多 Channel / mq 仍后置 |
| 原生面 | **Tool Result Eviction**（过大工具结果截断并落到工作区，缩小进模上下文） |
| 与危机 | **CRITICAL 整段跳过 eviction**；ELEVATED/NORMAL 委托框架 |
| 配置 | **`ToolResultEvictionConfig.defaults()`**（约 80k / 预览 2k / 路径 `large_tool_results`；排除 read/write/edit_file 与 memory_search/get、session_search） |
| 挂载 | **`disableToolResultEviction()`** + 产品 `CrisisGatedToolResultEvictionMiddleware`；非 CRITICAL 再 `new ToolResultEvictionMiddleware(filesystem, defaults()).onReasoning(...)` |
| 文件系统 | 经 `HarnessAgentRegistry` → `WorkspaceManager.getFilesystem()` |
| 范围 | **仅 companion**；echo 保持 disable 且不挂闸。`disable` 放 `configureCommon`，闸挂 `customizeCompanion` |
| 中间件顺序 | SafetyGate 之后：**先 Eviction 闸，再 Compaction 闸**（先截断大结果，再 compact） |
| 落地形态 | ① spike → ② 接线 → ③ README/专题收口 |

## 明确不做（本专题）

1. 不调 max/preview/路径/排除名单（固定 `defaults()`）  
2. 不上 Subagent / MCP / 多 Channel / `mode=mq`  
3. 不切 Permission 终态 Gate；不补紧急 `compactionHook`  
4. 不把 eviction 旋钮放进 Nacos 热更白名单  
5. 不改 Compaction / Memory 已锁危机与 flush 约定  
6. echo 不开 eviction

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **对照 spike** — 已完成  
   `ToolResultEvictionSurfaceSpikeTest`：`defaults()` 为 80k/2k/`large_tool_results` 与排除六工具；  
   **未 disable 时框架已挂** `ToolResultEvictionMiddleware`；`disableToolResultEviction` 后链上消失；  
   filesystem 须从 build 后的 `WorkspaceManager.getFilesystem()` 取（裸 Path 构造可能为空）。不改工厂主路径。  
2. **disable + 危机闸接线** — 已完成  
   `configureCommon`：`disableToolResultEviction()`（框架默认会挂，必须显式关）。  
   companion：`CrisisGatedToolResultEvictionMiddleware`（CRITICAL → `next`；否则委托 `ToolResultEvictionMiddleware` + `defaults()`）。  
   挂在 SafetyGate 之后、Compaction 闸之前。  
   测试：`CrisisGatedToolResultEvictionMiddlewareTest`。  
3. **README / 专题收尾** — 已完成  
   `agent-runtime/README.md` 与工厂注释写清 companion 开、echo 不开、顺序 Eviction→Compaction；  
   `docs/compaction-落地.md` 交叉引用本文。

## 验收要点

- ①：默认是否启用、落盘与排除名单有对照结论  
- ②：CRITICAL 不进入框架 eviction；NORMAL 可委托；顺序 Eviction → Compaction；echo 仍 disable  
- ③：文档与工厂注释一致

## 状态

grill 已结束；落地 ①～③ 已完成。本专题收口。若再改规范，先改本文再动代码。
