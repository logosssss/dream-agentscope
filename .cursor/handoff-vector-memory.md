# Handoff：管理进程 Nacos 启动配置之后继续落地

下一场：用户说「继续」或「继续往下推进」时，按指南清单和各模块 README「还没有」做下一块。不要改 `doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`。

## 下一场建议技能

无。按仓库 `.cursor/rules/continue-landing.mdc` 做一块就停。不要再开 `/grill-me`，除非用户明确要求。

## 从哪接着

分支：`feat/agentscope-platform-base`（不要用 `master`）。

远端：`origin/feat/agentscope-platform-base`。

下一空 Flyway 版本：**V34**（V33 已用于对齐 `agentscope_store` 与 JdbcStore）。

## 已做（本块）

管理进程 `NacosAdminStartupConfigLoader`：`NACOS_ADDR` 有值时拉 `agent-admin.yaml` / `AGENT_PLATFORM`，失败退出。白名单暂空。样例在 `deploy/nacos/agent-admin.yaml`。前端 Step 1 联调页已在 `web/`。

## 下一块

以 README「还没有」为准，优先考虑：知识检索 / `retrieveQuery`，或额度从每日次数扩到 token / 费用。不要再补管理端 GET 列表。

不要做：RocketMQ、服务发现、MCP、配置热更新、连接串进 Nacos、密码登录、JWT。

## 另一台机器开场白

拉到含「管理进程 Nacos 启动配置」的分支后说：「继续」。先读这份 handoff。
