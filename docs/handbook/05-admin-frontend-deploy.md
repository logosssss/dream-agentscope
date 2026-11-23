# ⑤ 管理端、双前端与部署

## 学习目标

分清管理故障域与用户故障域；能起双前端做冒烟；读懂部署文档边界（不强制现网发布）。

## 必读链接（有序）

1. [`docs/diagrams/architecture.md`](../diagrams/architecture.md) — 管理进程边界（图 3）；[`flows.md`](../diagrams/flows.md) 图 8  
2. [`agent-admin/README.md`](../../agent-admin/README.md)  
3. [根 README — 前端联调（web / web-admin）](../../README.md#前端联调web--web-admin)  
4. [`web/README.md`](../../web/README.md)  
5. [`web-admin/README.md`](../../web-admin/README.md)  
6. （可选前端方案）[`docs/reference/frontend-engineering-plan.md`](../../docs/reference/frontend-engineering-plan.md)  
7. 部署：[`deploy/AGENT-DEPLOY.md`](../../deploy/AGENT-DEPLOY.md)、[`deploy/AGENT-DEPLOY-USAGE.md`](../../deploy/AGENT-DEPLOY-USAGE.md)  
8. [`deploy/nginx/README.md`](../../deploy/nginx/README.md)  
9. （现网技能，只读了解）[`.cursor/skills/dream-agentscope-deploy/SKILL.md`](../../.cursor/skills/dream-agentscope-deploy/SKILL.md)  

## 要搞清的问题

1. 管理进程依赖哪些层？是否依赖运行层 / 接入层 Harness？谁跑 Flyway？  
2. `web-admin` 鉴权用什么 Token？为何不能拿 Caller Token 顶替？  
3. 生产构建为何不应把 `VITE_CALLER_TOKEN` 打进浏览器包？Nginx 如何代填？  
4. 用户端 `/api-console` 与管理端「运维面」职责差在哪里？  
5. 部署脚本入口约定是什么？可否临时拼 scp/java/nohup 替代？  

## 检查点

- 管理进程 `8100`：`/health/live`、`/health/ready` 成功。  
- `web` 能进主界面（登录后看到 Agent 轨 / 搭子等）。  
- `web-admin` 能进主界面（Admin Token 配置正确）。  
- 管理端至少一个只读能力点通（例如租户/额度/审计只读页或对应 API）。  
- `deploy` 文档与 nginx 说明已读；**不强制**执行现网发布。  
