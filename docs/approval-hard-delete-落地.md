# 审批软退役硬删落地（已锁定）

日期：2026-11-16。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：Permission Gate 终态已收口（见 `docs/permission-gate-落地.md`）；`approval_record` / 通知 / `APPROVAL` 待办为**软退役**；ASK 权威在运行层 `PendingAsk*`。  
本专题做**债清理硬删**，不升产品能力。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 主轴 | **H**：债清理；硬删软退役审批链路 |
| 范围 | **S2**：硬删审批单 + 审批旁路；**保留** `user_todo` 的 `CONFIRM` / `TASK` |
| 库表 | **F1**：Flyway **直接 `DROP TABLE approval_record`**；接受历史审批行丢失 |
| 管理端 | **A1**：删 `AdminApprovalController` 与 Decider 装配；`web-admin` 去掉审批页与导航（不留空壳） |
| 通知 | **N1**：**`DROP approval_notification`**；`GET /api/notifications*` **固定空列表**（不下线路由） |
| 待办历史 | **T2**：Flyway **物理删** `user_todo` 中 `todo_type='APPROVAL'`；代码与用户端去掉 APPROVAL 分支；禁再写入该类型 |
| 文档/切片 | **C1**：另开本文；`permission-gate-落地.md` 互指；① 锁文 → ② Flyway+后端 → ③ 前端/README |
| 用户侧活单 | **保留** `/api/approvals*` + `PendingAskFacade` 续跑契约；只去掉旧表「尽力同步」 |

## 明确不做（本专题 · X1–X5）

1. **X1** 不改 `PendingAskStore` / `PendingAskFacade` 权威与用户侧 `/api/approvals*` 续跑契约  
2. **X2** 不硬删 Memory 情景表 / 画像 / Milvus `episodic_memory`  
3. **X3** 不借本片做产品 Remote、`mode=mq`、MCP/Sandbox/Wakeup 产品开启  
4. **X4** 不强制大搬家 bootstrap 旧测；本片只改/删被硬删牵连的测  
5. **X5** 不把 `user_todo` 整表拆模块/改名；`CONFIRM` / `TASK` 产品行为保持  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **锁定文** — 已完成  
   本文；`permission-gate-落地.md` 互指「硬删见另文」。不改代码、不迁库。  
2. **Flyway + 后端清枝** — 已完成  
   `V40__drop_legacy_approval.sql`：`DROP approval_record` / `approval_notification`，删 `user_todo` 中 `APPROVAL` 行。  
   已删 Recorder/Event/Listener/Notifier/Decider/AdminApprovalController 与相关 Mapper/实体；`AuditQuery` 不再查审批表。  
   `ApprovalDecisionAppService` / `ApprovalController` 只走 PendingAsk；通知 API 固定空列表；待办 `listOpen` 仅 CONFIRM/TASK。  
   牵连单测已改。不改 PendingAsk 权威。  
3. **前端 / README 收口** — 已完成  
   `web-admin` 去掉审批页、路由、API 客户端与导航；用户端 `TodosView` 去掉 APPROVAL 标签分支；通知客户端仍可调空列表 API。  
   runtime / access / audit / admin / web-admin README、handbook、packaging 口径与本文一致：已硬删旧审批表与通知表；不宣称已删情景表 / 已开 mq 等。

## 验收要点

- ①：锁文与 grill 一致；Gate 文互指；无代码变更  
- ②：迁库与后端无审批单/通知写读权威；PendingAsk 续跑仍绿  
- ③：管理端无旧审批 UI；文档不宣称半截软退役  

## 状态

grill 已结束；落地 **①～③ 全部完成**。本专题收口。若再改规范，先改本文再动代码。
