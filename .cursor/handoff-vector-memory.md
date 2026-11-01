# Handoff：搭子记忆之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机有未推送提交。不要擅自 push。

**记忆垂直已落地。** 批准删除仍不执行。不要开始真删。

下一空 Flyway 版本：**V30**。

## 已做（附件元数据）

`POST /api/attachments` 登记 `attachment`。只收文件名和声称的路径，不收文件内容。空白存储类型按 `LOCAL`。不写 `attachment_ref`，也不接对象存储。

## 下一块候选（选一块）

以 README「还没有」为准，例如：

- `attachment_ref`：只登记附件和消息/运行的关联
- 停用租户
- 确认 / 任务待办

不要做：RocketMQ 依赖、`mode=mq` 跑起来、按调度触发任务、对象存储、批准后执行删除或工具、Persona 读表升级关系。

## 另一台机器开场白

拉到含附件元数据的 `feat/agentscope-platform-base` 后说：「继续」。先读这份 handoff 和各模块 README「还没有」，做一块就停。
