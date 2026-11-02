# Handoff：OSS 双后端切换之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机可能有未推送提交。不要擅自 push。

下一空 Flyway 版本：**V37**。

## 已做（用户删除请求列表）

`GET /api/deletion-requests` 列出当前用户在这个租户下的删除请求。默认 50 条，最多 100 条，含已决定的行。仍不执行删除。MySQL 关闭时 503。

## 下一块

以 README「还没有」为准。不要再补管理端 GET 列表。不要开始批准后真删。

管理端登记工具目录还没有，但管理进程不能依赖 `agent-semantic`；不要用这块硬拆模块边界。

不要做：RocketMQ、服务发现、MCP、配置热更新、连接串进 Nacos、密码登录、JWT。

## 另一台机器开场白

拉到含「用户删除请求列表」的分支后说：「继续」。先读这份 handoff。
