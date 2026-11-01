# Handoff：搭子记忆之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。

**搭子第一阶段（人格 + 安全底座）已收口。** 管理目录列表也齐了。批准删除仍不执行。不要开始真删。

下一空 Flyway 版本：**V32**。

## 已做（人设检查补恢复和纠正触发）

本会话第 1 轮且已有画像或情景记忆，或用户原文命中对准身份的纠正短句时，打开人设检查。满 20 轮仍打开。只活这一轮。危机档不打开。不建表，不调模型，不写干预。查库失败当没触发。

## 下一块

先做第一把业务 `@Tool`。目录、注册、审批、待办已经空转，模型这边还没有实现。只挂能跑通审批的工具，不解释 `conditions`，也不在批准后自动执行。

再往后：用户登录 → 知识检索 / `retrieveQuery` → 额度从每日次数扩到 token / 费用。不要再补管理端 GET 列表。

不要做：RocketMQ 依赖、`mode=mq` 跑起来、按调度触发任务、对象存储、批准后执行删除或工具、按保留策略清理数据、Persona 读表升级关系、语义判定换真模型、Memory Curator、主动唤醒。

## 另一台机器开场白

拉到含「下一步先做业务工具」记录的 `feat/agentscope-platform-base` 后说：「继续」。先读这份 handoff 和各模块 README「还没有」，做一块就停。
