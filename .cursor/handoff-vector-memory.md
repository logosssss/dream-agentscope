# Handoff：OSS 双后端切换之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。本机可能有未推送提交。不要擅自 push。

下一空 Flyway 版本：**V37**。

## 已做（通用附件 multipart）

`POST /api/attachments/file` 收 multipart，字节写入 `ObjectStore`，再登记附件。`storage_type` 跟 local/OSS 实现走。对象键 `att/{tenant}/{user}/{id}/{fileName}`，单文件不超过 5MB。目录不可用或超限不写对象。登记失败尝试回滚对象。JSON `POST /api/attachments` 仍只登记元数据。

## 下一块

以 README「还没有」为准，优先：删文档时级联清对象。不要再补管理端 GET 列表。

不要做：RocketMQ、服务发现、MCP、配置热更新、连接串进 Nacos、密码登录、JWT。

## 另一台机器开场白

拉到含「通用附件 multipart」的分支后说：「继续」。先读这份 handoff。
