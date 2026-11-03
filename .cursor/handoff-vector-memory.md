# Handoff：客户端 Step 6 Markdown 之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。

下一空 Flyway 版本：**V38**。

## 已做（本块）

客户端前端 Step 6（Markdown）：引入 `markdown-it`；`MarkdownContent` 渲染助手气泡（`html: false`）；流式增量同样走 Markdown。停止按钮与自动滚动沿用已有实现。暗黑模式未做。不改 Java。

## 下一块

以 README「还没有」为准。可接暗黑模式，或管理端额度/审批页，或后端清单缺口。不要开始批准后真删。

不要做：RocketMQ、服务发现、MCP、配置热更新、连接串进 Nacos、密码登录、JWT、管理员账号登录。

## 另一台机器开场白

拉到含「客户端 Step 6 Markdown」的分支后说：「继续」。先读这份 handoff。
