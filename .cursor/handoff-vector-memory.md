# Handoff：搭子记忆之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机有未推送提交。不要擅自 push。

**记忆垂直、危机输出红线、人设规则检查已落地。** 批准删除仍不执行。不要开始真删。

下一空 Flyway 版本：**V32**。

## 已做（人设一致性）

`PersonaConsistencyMiddleware` 在会话已经成功 20 轮后打开标记。执行层按恋人、助手、替人决定三类词表检查回复，命中换成 `PersonaContract` 短基线。流式从第 21 轮起先攒齐再查。危机档不查。不调模型，不另写干预。成功轮次来自 `agent_run`；MySQL 关闭时按 0。

## 下一块

以各模块 README「还没有」为准，例如对话按 `tenant_user` 鉴权。用户纠正偏好和跨会话恢复还不触发人设检查。

不要做：RocketMQ 依赖、`mode=mq` 跑起来、按调度触发任务、对象存储、批准后执行删除或工具、Persona 读表升级关系、语义判定换真模型、Memory Curator、主动唤醒。

## 另一台机器开场白

拉到含人设规则检查的 `feat/agentscope-platform-base` 后说：「继续」。先读这份 handoff 和各模块 README「还没有」，做一块就停。
