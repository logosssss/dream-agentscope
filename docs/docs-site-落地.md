# 文档站

状态：**已完成**

用户进程上的只读文档站。Git 仓库是唯一可编辑来源，MySQL 里只放可丢弃的正文和切块。配置写在本地 yml，用环境变量覆盖，不进 Nacos 热更白名单。

## 入口

- 页面：`/docs`、`/docs/:topic`、`/docs/doc/:id`（公开，不登录也能看公开文档）
- `GET /api/docs/tree`
- `GET /api/docs/doc/{id}`
- `POST /api/docs/search`
- `POST /api/docs/doc/{id}/ask`（必须登录；额度动作名 `docs-ask`）
- `POST /api/docs/sync`、`GET /api/docs/sync/status`（仅 `docs.owner-user-ids`）

提问只走文档接口。通用对话点名 `docs-v1` 按不存在处理。

## 明确不做

1. 不把文档源、白名单放进 Nacos
2. 不做检索分数阈值和重排（六条样例还没在活索引上打分）
3. 不把 `docs-v1` 放进前端 Agent 列表，也不从 `/api/agent/chat` 调用它
4. 管理进程不依赖文档模块，也不做同步
5. 不改架构指南

## 后续

用户进程起来并完成一次同步后，用 `engineering-p3-testing-落地.md` 核对两句提问：为什么要五个窄端口；这篇里 Kubernetes 怎么部署。
