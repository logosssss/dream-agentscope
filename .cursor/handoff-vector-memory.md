# Handoff：搭子记忆之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机有未推送提交。不要擅自 push。

**记忆垂直已落地。** 批准删除仍不执行。不要开始真删。

下一空 Flyway 版本：**V29**。

## 已做（任务调度登记）

`POST /api/admin/task-schedules` 登记 `agent_task_schedule`。类型 `BACKFILL` / `REPORT` / `BATCH`。cron 和延迟二选一。不写任务参数，不算下次触发，不消费。

## 下一块候选（选一块）

以 README「还没有」为准，例如：

- 附件表：只登记元数据，不接对象存储
- 停用租户
- 确认 / 任务待办

不要做：RocketMQ 依赖、`mode=mq` 跑起来、按调度触发任务、批准后执行删除或工具、Persona 读表升级关系。

## 另一台机器开场白

拉到含调度登记的 `feat/agentscope-platform-base` 后说：「继续」。先读这份 handoff 和各模块 README「还没有」，做一块就停。
