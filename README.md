# dream-agentscope

Dream-SaaS 开源仓库：基于 AgentScope 的 Java 多 Agent 平台底座（用户进程 + 管理进程 + `web` / `web-admin`）。

在线站点：<https://www.dream-saas.com/>  
许可证：[Apache-2.0](./LICENSE)

## 仓库结构（简）

| 路径 | 说明 |
|------|------|
| `agent-bootstrap` | 用户进程入口（8080） |
| `agent-admin` | 管理进程入口（8100） |
| `web` | 用户端（介绍页 `/`、登录后进搭子等） |
| `web-admin` | 管理端 |
| `docs/release-gate.md` | 开源前本地/线上门禁清单 |

更细的模块边界见各子目录 `README.md`。

## 本地快速启动（开发机）

前置：JDK 21、Maven、Node；本机可连 MySQL / Redis / Milvus；建议配 Nacos。

1. 复制根目录 `.env.example` 为 `.env`，填写 `NACOS_*`（以及未放进 Nacos 时的令牌兜底）。Caller / Admin 令牌勿用 `change-me*`。
2. 确认 Nacos `AGENT_PLATFORM` 组内有用户侧 `agent-bootstrap-infra.yaml` + `agent-bootstrap.yaml`（管理侧对称）；infra 里令牌先留空再用环境变量覆盖。
3. 执行演示租户种子（如 `deploy/mysql/seed-demo-tenant-user.sql`）。
4. 启动用户进程（模块 `agent-bootstrap`）与可选管理进程（`agent-admin`）。
5. 前端：

```bash
cd web
cp .env.example .env.local   # 填写 VITE_CALLER_TOKEN（仅本地；线上由网关代填）
npm install
npm run dev
```

浏览器打开 Vite 提示的地址：首页为介绍页，点「开始使用」未登录会进入登录/注册。

公开演示前请把 Nacos 业务配置里的 `agentscope.access.daily-limit` / `per-minute` 收到可接受范围（模板见 `deploy/nacos/agent-bootstrap.yaml`），并按需执行 `deploy/mysql/seed-demo-tenant-user.sql` 中的 `tenant_quota` 段。

管理端见 `web-admin/README.md`（`VITE_ADMIN_TOKEN` 与 Caller 不得混用）。

## 测试

仓库根目录、JDK 21：

```bash
mvn test
```

测试不连接真实 Redis / MySQL / Milvus / Nacos。

## 说明

- 公开演示与自建部署时请自行配置额度/限流与密钥轮换。
- 教程与踩坑笔记在公众号发布；本 README 只覆盖把项目跑起来。
