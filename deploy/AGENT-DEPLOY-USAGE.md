# Agent 现网部署 — 使用与验证

> 配套：`deploy/AGENT-DEPLOY.md` · `.cursor/skills/dream-agentscope-deploy/SKILL.md`  
> 环境：`root@47.119.115.162` · `https://www.dream-saas.com` · 本机密钥 `%USERPROFILE%\.ssh\id_ed25519`

## 1. 唯一入口

在仓库根目录执行（Windows PowerShell）：

```powershell
.\deploy\scripts\agent-deploy.ps1 -Target <目标>
```

| `-Target` | 做什么 |
|-----------|--------|
| `frontend` | 本机 `web` 构建 → 整目录替换 `web-dist`（先备份） |
| `user` | 编/传 `boot.jar` → nohup 重启 :8093 |
| `admin` | 编/传 `admin.jar` → nohup 重启 :8100 |
| `full` | frontend → user → admin |

**Agent 规则**：用户说部署/上线/发到现网时，只准调本脚本，禁止手写乱序 scp / `java -jar`。

### 常用参数

| 参数 | 含义 |
|------|------|
| `-SkipBuild` | 跳过本机构建（产物已存在） |
| `-SkipFrontend` | `full` 时不带前端 |
| `-SkipUpload` | 跳过上传（少用） |
| `-HealthWaitSec 180` | Gate5 等待冷启动秒数 |
| `-Server` / `-SshKey` | 覆盖默认主机与密钥 |

示例：

```powershell
.\deploy\scripts\agent-deploy.ps1 -Target frontend
.\deploy\scripts\agent-deploy.ps1 -Target user -SkipBuild
.\deploy\scripts\agent-deploy.ps1 -Target full -SkipFrontend
```

## 2. 关卡行为

| Gate | 名称 | 通过条件 |
|------|------|----------|
| 1 | Preflight | SSH、磁盘、进程目录 |
| 2 | LocalBuild | `web/dist/index.html` / 模块 `target/*.jar` |
| 3 | Upload | 备份后替换远端；校验文件存在 |
| 4 | Restart | user/admin nohup 拉起（frontend 跳过） |
| 5 | Health | `/health/live`；含前端时公网首页 200 |

**失败即停、零自动重试。** 修好后再次执行同一命令。

### Gate4/5 失败只看

| 服务 | 命令 |
|------|------|
| user | `tail -n 80 /opt/dream-agentscope/logs/boot.out` |
| admin | `tail -n 80 /opt/dream-agentscope/logs/admin.out` |
| Nginx | `nginx -t`；`tail -n 40 /var/log/nginx/error.log` |

## 3. 验证清单

终端应依次 `PASS Gate1` … `PASS Gate5`，最后 `ALL GATES PASS`。

```bash
curl -sf http://127.0.0.1:8093/health/live && echo OK
curl -sf http://127.0.0.1:8100/health/live && echo OK
curl -sI https://www.dream-saas.com/ | head -n 3
```

### 产物落点

| 产物 | 远端路径 |
|------|----------|
| 前端 | `/opt/dream-saas/web-dist/` |
| 用户 JAR | `/opt/dream-agentscope/boot.jar` |
| 管理 JAR | `/opt/dream-agentscope/admin.jar` |
| 备份 | `/opt/dream-agentscope/backup-<ts>/`；`/opt/dream-saas/backup/web-dist-<ts>.tgz` |

## 4. 不做的事

- 不同步 Nacos / `run.env` / Nginx
- 不部署 `web-admin` 静态资源（本技能 Target `admin` 仅管理进程 JAR）
- 不启用 `deploy/systemd/`（模板仅供后续迁移）
