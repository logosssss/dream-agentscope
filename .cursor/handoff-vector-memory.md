# Handoff：搭子记忆之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。

**搭子第一阶段（人格 + 安全底座）已收口。** 管理目录列表也齐了。批准删除仍不执行。不要开始真删。

下一空 Flyway 版本：**V33**。

## 已做（用户登录会话）

`POST /api/login` 核对租户用户后签发 Redis 不透明令牌；`Authorization: Bearer` 还原身份；`POST /api/logout` 删键。无密码、无 JWT。调用方令牌仍要过。请求头身份仍可用。

## 下一块

知识检索 / `retrieveQuery`。

再往后：额度从每日次数扩到 token / 费用。不要再补管理端 GET 列表。

不要做：RocketMQ 依赖、`mode=mq` 跑起来、按调度触发任务、对象存储、批准后执行删除或工具、按保留策略清理数据、Persona 读表升级关系、语义判定换真模型、Memory Curator、主动唤醒、密码登录、JWT。

## 另一台机器开场白

拉到含「用户登录会话」的 `feat/agentscope-platform-base` 后说：「继续」。先读这份 handoff 和各模块 README「还没有」，做一块就停。
