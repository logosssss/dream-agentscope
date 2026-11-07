# Agent 现网部署 SOP（dream-agentscope）

> **给 Cursor Agent 用**：关卡制，失败即停。使用说明见 [AGENT-DEPLOY-USAGE.md](./AGENT-DEPLOY-USAGE.md)。  
> **环境**：`root@47.119.115.162` · 公网 `https://www.dream-saas.com`  
> **前端**：`/opt/dream-saas/web-dist` · **进程目录**：`/opt/dream-agentscope`  
> **密钥（本机）**：`%USERPROFILE%\.ssh\id_ed25519`  
> **技能**：`.cursor/skills/dream-agentscope-deploy/SKILL.md`

## 唯一入口

```powershell
# 仓库根目录；失败即停、零自动重试；现网以 nohup 重启（非 Docker）
.\deploy\scripts\agent-deploy.ps1 -Target frontend
.\deploy\scripts\agent-deploy.ps1 -Target user
.\deploy\scripts\agent-deploy.ps1 -Target admin
.\deploy\scripts\agent-deploy.ps1 -Target full
```

Agent **只准调用该脚本**，禁止手写乱序 scp / java / nohup。

## 目标矩阵

| 目标 | 含义 | 本机构建 | 上传 | 重启 |
|------|------|----------|------|------|
| `frontend` | 主站静态资源 | `cd web && npm ci && npm run build` | `web-dist-scp-deploy.ps1` | 无 |
| `user` | 用户进程 | `mvn -pl agent-bootstrap -am package -DskipTests` | → `boot.jar` | nohup 8093 |
| `admin` | 管理进程 | `mvn -pl agent-admin -am package -DskipTests` | → `admin.jar` | nohup 8100 |
| `full` | 上三者 | 按序 frontend → user → admin | 同上 | 同上 |

## 硬性禁止

1. 服务器上全量 `mvn package`
2. 改 Nginx / 覆盖 `run.env` / 改 Nacos 正文
3. 跳过 Gate 宣称成功
4. 失败后另起一套临时命令空等重试
5. 误杀占用 **8080** 的 Nacos（停进程只按 `boot.jar` / `admin.jar` 路径匹配）

## 关卡表

| Gate | 名称 | 通过条件 | 失败时只看 |
|------|------|----------|------------|
| 1 | Preflight | SSH 通；磁盘未满；`/opt/dream-agentscope` 存在 | SSH 报错；`df -h /` |
| 2 | LocalBuild | `web/dist/index.html` 与/或目标 JAR 存在 | 本机 npm/mvn 末 80 行 |
| 3 | Upload | 远端产物存在；上传前已备份 | scp/ssh；远端 `ls` |
| 4 | Restart | （仅 user/admin/full）进程按配方拉起 | `logs/boot.out` / `logs/admin.out` 末 80 行 |
| 5 | Health | `8093`/`8100` 的 `/health/live`；含前端时公网首页 200 | 同上 + `nginx -t`；error.log |

`frontend` 跳过 Gate4，Gate5 只查公网首页。

### 健康检查示例

```bash
curl -sf http://127.0.0.1:8093/health/live && echo OK
curl -sf http://127.0.0.1:8100/health/live && echo OK
curl -sI https://www.dream-saas.com/ | head -n 5
```

## 回滚入口

- JAR：`/opt/dream-agentscope/backup-<timestamp>/` 内文件拷回 `boot.jar`/`admin.jar` 后，对本脚本加 `-SkipBuild` 再跑对应 Target
- 前端：`/opt/dream-saas/backup/web-dist-<timestamp>.tgz` 解回 `web-dist`

## systemd 模板

见 `deploy/systemd/`。默认**不启用**；迁移时再 `systemctl enable --now`，并把技能 Restart 从 nohup 切到 systemctl（另开任务）。

## 结束汇报模板

```
目标: frontend | user | admin | full
Gate: 1..5 全部通过 / 卡在 GateN
产物: <本地路径>
远端: <路径>
健康检查: <命令 + 结果>
回滚: <备份路径>
```
