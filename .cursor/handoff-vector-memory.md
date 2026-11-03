# Handoff：客户端附件页之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。

下一空 Flyway 版本：**V38**。

## 已做（本块）

客户端 `web`：平台页 `/attachments`（左侧轨「附」入口）。上传 multipart、列表、下载、停用/恢复。服务端列表仍只含 ACTIVE；停用行本页暂留以便恢复。

## 下一块

以 README「还没有」为准。可接知识库/待办客户端页，或后端条件参数范围等缺口。不要开始按策略清理，也不要批准后真删，也不要真正调度消费，也不要接 RocketMQ。

不要做：RocketMQ、服务发现、MCP、配置热更新、连接串进 Nacos、密码登录、JWT、管理员账号登录。

## 另一台机器开场白

拉到含「客户端附件页」的分支后说：「继续」。先读这份 handoff。
