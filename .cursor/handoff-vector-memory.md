# Handoff：搭子记忆之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机有未推送提交。不要擅自 push。

**记忆垂直已落地。** 批准删除仍不执行。不要开始真删。

下一空 Flyway 版本：**V31**。

## 已做（确认和任务待办）

`POST /api/todos` 登记 `CONFIRM` / `TASK`。关联运行或任务标识，不核这些对象。同一用户、类型和关联再开一次是冲突。不写截止时间，也不关闭。`GET /api/todos` 列出还开着的审批、确认和任务。审批待办仍由询问事件开、决定后关。

## 下一块候选（选一块）

以 README「还没有」为准，例如：

- `tenant_user`
- 关闭或恢复租户

不要做：RocketMQ 依赖、`mode=mq` 跑起来、按调度触发任务、对象存储、批准后执行删除或工具、Persona 读表升级关系。

## 另一台机器开场白

拉到含确认和任务待办的 `feat/agentscope-platform-base` 后说：「继续」。先读这份 handoff 和各模块 README「还没有」，做一块就停。
