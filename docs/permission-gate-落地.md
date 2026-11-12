# Permission Gate 终态落地（已锁定）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：PermissionEngine / Memory / Compaction / Eviction / Skills（窄）已收口；优化主轴仍为 **框架原生对齐（学习向）**。  
前置文档：[`docs/permission-engine-落地.md`](permission-engine-落地.md)（历史双闸 B；**运行入口已切为本文当前 A**）。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 优化主轴 | **A**：继续框架原生对齐；不上线压力；MCP / 多 Channel / mq / Subagent 仍后置 |
| 原生面 | **PermissionGate 终态**：无条件判定交框架 Gate；消 Middleware 对引擎的二次 `checkPermission` |
| ASK 闭环权威 | **框架 `pendingAsk` / `resumeAgent`**；**弃**产品审批表作为 ASK 权威 |
| 产品条件闸 | Middleware **只 ALLOW / DENY**；带租户 / 角色 / `maxLen`·`minLen` 的仍走 `PermissionRuleService` |
| 产品 ASK | **运行期当 DENY**（打日志）；管理端/种子逐步改掉。有条件的产品 ASK 适用本条 |
| 无条件 ASK | 仍进 `PermissionContext`（如 `echo_text` 空 conditions）→ Gate / `pendingAsk`，**不当**产品 DENY |
| 续跑入口 | 改造现有 **`POST /api/approvals/{id}/decision`**：批准后驱动框架续跑（不再只改库）；列表可过渡到 pending/ASKING |
| 旧审批表 / Recorder | **停写停用主路径**；表与只读查询暂留；**硬删另块** |
| PermissionMode | 沿用已锁 **`BYPASS`**（未匹配 ALLOW；显式 DENY/ASK 仍先生效） |
| 落地形态 | ① spike → ② Middleware 收缩 → ③ 接入层续跑 → ④ 退役标注 + 文档「当前 A」 |

## 明确不做（本专题）

1. 不上 Subagent / MCP / 多 Channel / Wakeup / `mode=mq`  
2. 不补紧急 `compactionHook`；不开 Skills manage/curator  
3. 不硬删 `approval_record` 表与相关迁库（硬删另块）  
4. 不把产品条件（租户/角色/长度）塞进 `ruleContent` DSL  
5. 不在 acting 热路径 `addRule` / 每轮重装整个 context（续跑所需的框架侧规则更新除外，以 spike 为准）  
6. 不改 Memory / Compaction / Eviction / Skills 已锁约定  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **Gate / `pendingAsk` / 续跑面对照 spike** — 已完成  
   `PermissionGateSurfaceSpikeTest`：  
   - Middleware `onActing` **先于** Gate（`actingStream.evaluatePermissions`）；进入中间件时尚未 ASKING。  
   - 无条件 ASK → `RequireUserConfirmEvent` + 会话工具进入 ASKING；工具不执行；无确认再入抛 `IllegalStateException`（文案点名 `agentscope_confirm_results`）。  
   - 无条件 DENY → 工具 DENIED，无 RequireUserConfirm。  
   - 无条件 ALLOW → 工具执行。  
   - 续跑：下一轮用户 Msg 带 `Msg.METADATA_CONFIRM_RESULTS` = `List<ConfirmResult(true, toolCall)>`（toolCall 取自 RequireUserConfirmEvent）后工具执行。  
   - 状态按 `(agentId, sessionId)` 隔离；agentId 复用会串 ASKING。不改工厂主路径、不改审批 API。  
2. **Middleware 收缩** — 已完成  
   `ApprovalMiddleware` 只注入 `PermissionRuleService`；不再 `checkPermission` / 落单 / claim / 发 `ApprovalRequestedEvent` / 标 ASKING。  
   产品判定经 `asAllowOrDeny`：ALLOW 下放，ASK/DENY 从本次输入拿掉（ASK 打日志）。无条件 ASK/DENY/ALLOW 留给后续 Gate。  
   测：`ApprovalMiddlewareTest`、`ApprovalMiddlewareDualGateTest`（引擎 ASK 不能压过产品 ALLOW）。  
3. **接入层续跑** — 已完成  
   流式路径登记 `RequireUserConfirm` → `PendingAskStore`（id=工具调用 id）。  
   `GET /api/approvals/pending` 读进程内活单；`POST .../decision` 取出后经 Gateway `send(Msg)` 带 `METADATA_CONFIRM_RESULTS` / `ConfirmResult` 续跑。  
   旧表同号行尽力 `ApprovalDecider` 同步，失败不回滚。同步 chat 遇 ASK 不登记。测：`ApprovalNotifyTest`。  
4. **退役标注 + 文档收口** — 已完成  
   **运行入口 = 当前 A**（Gate + 产品条件闸 + `PendingAskStore` 续跑）。  
   软退役标注：`ApprovalRecorder` / `MybatisApprovalRecorder`、`ApprovalRequestedEvent`、`ApprovalNotificationListener`、管理端 `AdminApprovalController`（旧表只读/改决定，不续跑）。  
   runtime / access / audit / admin README 与本文对齐；engine 文保持历史 + 指针「已切 A」。  
   **未**硬删 `approval_record` / 通知 / 待办；**未**宣称已接 MCP 等后置项。

## 验收要点

- ①：Gate/`pendingAsk`/续跑面有可复现对照结论（可丢弃 spike 测）  
- ②：Middleware 不再二次 `checkPermission`；产品 ASK 运行期 DENY；主路径不再写审批表  
- ③：批准决策能续跑工具（不再「只改库」）  
- ④：文档与代码一致；不宣称已硬删审批表；不宣称已接 MCP 等后置项  

## 状态

grill 已结束；落地 ①～④ 全部收口。Permission Gate 终态专题完成。硬删审批表 / 通知·待办链路、MCP 等仍后置。若再改规范，先改本文再动代码。
