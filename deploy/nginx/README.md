# 线上静态站与反代（示例）

面向 `https://www.dream-saas.com/`：介绍页 + 登录后产品，同一套 `web/dist`。

## 发布步骤（手动）

1. 本机或 CI：`cd web && npm ci && npm run build`  
   - **不要**在生产构建里设置 `VITE_CALLER_TOKEN`。
2. 把 `web/dist/*` 同步到服务器 Nginx `root`（见 `dream-saas.com.conf.example`）。
3. 在 Nginx 配置里填入与 Nacos / 进程一致的 Caller Token，仅用于 `proxy_set_header X-Caller-Token`。
4. 把 `deploy/nacos/agent-bootstrap.yaml` 里的演示额度同步到线上 Nacos，并按需执行 `deploy/mysql/seed-demo-tenant-user.sql` 中的 `tenant_quota`。
5. `nginx -t && systemctl reload nginx`（或等价命令）。

管理端不要挂到同一公开 CTA；可另端口或内网。

## 备案脚注

前端公开页（`/`、`/login`、`/register`）已带 ICP / 公安备案号链接。公安备案小图标若管局要求展示，可将官方图标放到 `web/public/` 后再改脚注组件引用。
