# PermissionEngine 落地（已锁定）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：底座与 Channel Gateway 已立；优化主轴为 **框架原生对齐（学习向）**，第一块原生面为 **PermissionEngine**。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：对齐 AgentScope 原生能力以深入学习；不急着上线；MCP / 多 Channel / mq 等仍后置 |
| 第一原生面 | **PermissionEngine**（相对现 `ApprovalMiddleware` 旁路） |
| 判定终态 | **C**：ALLOW / ASK / DENY 判定权威归 `PermissionEngine`；Middleware 负责审批闭环（落单 / 事件 / ASKING / claim） |
| 未匹配默认 | **ALLOW**（非 trivial 上下文下，没命中规则则允许）；目录外工具靠 registry 不挂载约束，不靠默认 ASK |
| 产品条件 | **双闸 B**：无条件规则进引擎；带租户 / 角色 / 时间窗 / `maxLen`·`minLen` 的仍走 `PermissionRuleService` |
| 双闸合并 | **更严优先**：DENY > ASK > ALLOW；建上下文时尽量只注入无条件规则，减少交叉 |
| 规则绑定 | **仅 build / 排空重建**注入 `PermissionContextState`；运行中不 `addRule`；热更走现有长驻排空重建 |
| 运行入口 | **先 B 后 A**：第一期 Middleware 显式 `checkPermission` + 产品闸合并；文档终态为框架内置 PermissionGate，Middleware 只做闭环与条件闸 |
| 已批准 claim | **ASK→ALLOW 覆盖**：仅合并结果为 ASK 且 claim 成功时放行；DENY 不可被批准单救回 |
| PermissionMode | **主路径用 `BYPASS`**（③ 实测：未匹配 = ALLOW；显式 DENY/ASK 仍先生效）。**不采用 `DONT_ASK`**（未匹配 = DENY）。`DEFAULT` 仅作对照（未匹配 = ASK） |
| 落地形态 | **文档锁定 + 第一块只做可丢弃 spike 测试**，不接审批表 |

## 明确不做（本专题）

1. 不把 `maxLen` / `minLen` / 租户 / 角色塞进 `ruleContent` 自研 DSL  
2. 不在 acting 热路径 `addRule` / 每轮重装整个 context（与长驻 Gateway 锁定一致）  
3. 第一期不切换到框架 PermissionGate 为主路径（终态 A 另块再做）  
4. 不借本专题上 MCP、多 Channel、Subagent、Wakeup、`mode=mq`  
5. 不改对外审批 HTTP / JSON；不改为「批准后自动再执行工具」

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **`DEFAULT` 未匹配行为 spike** — 已完成  
   `PermissionEngineDefaultModeSpikeTest`：  
   - 只设 `PermissionMode.DEFAULT`、零规则 → **仍是 trivial**（不能靠只设 mode 离开 trivial）。  
   - 用另一工具的 ALLOW 规则撑成非 trivial 后，未知工具 → **`ASK`（≠ 产品目标 ALLOW）**。  
   不挂审批表、不改 Gateway。结论：③ 必须评估 `DONT_ASK`（或等价）。  
2. **显式 DENY / ASK 规则可读** — 已完成  
   `PermissionEngineExplicitRuleSpikeTest`：`ruleContent == null` 时 ALLOW / ASK / DENY 均可被 `checkPermission` 读到。  
   要点：`ToolBase.matchRule` **仅 null content 命中**；传 `*` 等非 null 字符串不命中，会落到未匹配 ASK（曾导致伪阳性）。  
3. **是否换 `DONT_ASK`** — 已完成（结论：不换；改锁 `BYPASS`）  
   `PermissionEngineDontAskModeSpikeTest`：  
   - `DONT_ASK` 未匹配 → **DENY**（≠ 产品 ALLOW），显式 DENY 仍有效。  
   - `BYPASS` 未匹配 → **ALLOW**；显式 DENY / ASK 仍先生效（旁路默认在规则检查之后）。  
   原「避免 BYPASS」假设不成立，已改「已锁定」表。  
4. **build 注入无条件规则** — 已完成  
   `UnconditionalPermissionCatalog` + `PermissionContextFactory`；`configureCommon` 挂 `permissionContext`（**BYPASS**，`ruleContent=null`）。  
   配置/表两侧只抽出租户与角色均为 `*` 且无 conditions 的启用行；空目录用占位规则撑非 trivial。热更仍排空重建。  
5. **Middleware 双闸 + claim** — 已完成  
   `ApprovalMiddleware`：引擎 + 产品闸更严合并；ASK 走落单/PENDING/claim；DENY 不可 claim。  
   `ApprovalMiddlewareDualGateTest` 覆盖引擎 ASK 压过产品 ALLOW、产品 DENY 压过引擎 ALLOW、claim 救不了 DENY。  
6. **文档标注终态改框架 Gate** — 未开始  
   验证 Gate 与落单/ASKING 语义一致后再切；本文件终态栏改为「运行入口 A」。

## 验收要点（按块）

- ①～③：测试不连真实 Redis/MySQL/Milvus/Nacos；可直接构造 `PermissionEngine` / `PermissionContextState`  
- ④：长驻 Agent 重建后规则与权限热更一致；trivial 不再作为生产默认  
- ⑤：`echo_text` 等现有审批单测与行为不回退；DENY 仍不可 claim  
- ⑥：无双判不一致；Middleware 不再重复做引擎已覆盖的无条件判定

## 状态

grill 已结束；①～⑤ 已收口（双闸 Middleware 已接引擎）。  
下一块：⑥ 文档标注终态改框架 Gate（本期可只更新叙事，不切主路径）。若改规范，先改本文再动代码。
