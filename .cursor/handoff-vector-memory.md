# Handoff：保留策略停用恢复之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。

下一空 Flyway 版本：**V38**。

## 已做（本块）

数据保留策略：`disable` / `restore`（路径带 dataType，body 带 scope，空白按 `*`）；摘要带回 `enabled`；管理端接口与 `web-admin` 保留策略页动作。仍不执行清理。

## 下一块

以 README「还没有」为准。可接任务调度停用恢复，或评测数据集停用，或后端其它清单缺口。不要开始按策略清理，也不要批准后真删，也不要真正调度消费。

不要做：RocketMQ、服务发现、MCP、配置热更新、连接串进 Nacos、密码登录、JWT、管理员账号登录。

## 另一台机器开场白

拉到含「保留策略停用恢复」的分支后说：「继续」。先读这份 handoff。
