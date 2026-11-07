---
name: dream-agentscope-deploy
description: >-
  Deploy dream-agentscope to production (47.119.115.162 / dream-saas.com). Use when
  the user asks to deploy, 上线, 发到现网, 部署生产, deploy frontend/user/admin/full,
  or ship to the live server. Always run deploy/scripts/agent-deploy.ps1 — never
  improvise scp/java/nohup sequences.
---

# dream-agentscope 现网部署 Skill

## 硬性入口

用户要求现网部署时，**只执行**（仓库根目录）：

```powershell
.\deploy\scripts\agent-deploy.ps1 -Target <frontend|user|admin|full>
```

未指定目标且用户只改了前端/某一进程时，选对应 Target；否则用 `full`（顺序：frontend → user → admin）。

## 现网常量（勿猜）

| 项 | 值 |
|----|-----|
| SSH | `root@47.119.115.162`，密钥 `%USERPROFILE%\.ssh\id_ed25519` |
| 前端远端 | `/opt/dream-saas/web-dist`（整目录替换，先备份） |
| 本机构建 | `web/dist`（**禁止**构建时设置 `VITE_CALLER_TOKEN`） |
| 用户 JAR | `/opt/dream-agentscope/boot.jar`，端口 **8093** |
| 管理 JAR | `/opt/dream-agentscope/admin.jar`，端口 **8100** |
| 环境文件 | `/opt/dream-agentscope/run.env`（只远端 source，不上传、不打印） |
| 进程托管 | 现网 **nohup**；`deploy/systemd/*.service` 仅模板，默认不启用 |

`8080` 是 Nacos，不是用户进程。

## 禁止

- 手写乱序：`scp` JAR、`nohup java`、服务器 `mvn package`
- 改 Nginx（含整文件覆盖 `dream-saas-ssl.conf`）
- 覆盖或读取进对话 `run.env` / Nacos 正文
- 跳过 Gate；失败后自动换另一套命令重试
- 未过健康检查宣称成功
- 擅自 `git commit` / `git push`（除非用户另提）
- 调用 `E:\Dream-SaaS` 旧 `agent-deploy.ps1`（旧栈模块，会打错服务）

## 失败时

脚本失败即停并打印「只看」。Agent **只读该提示**，修好后**再次调用同一脚本**（可加 `-SkipBuild` / `-SkipFrontend` 等），不要另起炉灶。

## 参考

- `deploy/AGENT-DEPLOY-USAGE.md` — 使用与验证（优先）
- `deploy/AGENT-DEPLOY.md` — 关卡说明
- systemd 模板：`deploy/systemd/`（迁移时再用，本技能默认 nohup）

## 结束汇报

```
目标: frontend | user | admin | full
Gate: 1..5 全部通过 / 卡在 GateN
产物: <本地路径>
远端: web-dist / boot.jar / admin.jar
健康检查: <命令 + 结果>
回滚: <backup 目录或时间戳包>
```
