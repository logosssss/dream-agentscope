# web-admin（管理端联调）

Vue 3 + Vite + Pinia + Vue Router + Tailwind v4。同仓页面联调**管理进程**（`8100`），与搭子 `web/`（`8080`）分开入口。

第一期：租户 + 租户用户（列表、登记、状态动作）。鉴权为 `VITE_ADMIN_TOKEN`（对应 `AGENTSCOPE_ADMIN_TOKEN`）+ 可选操作人（`X-Operator-Id`），暂无管理员账号登录。

## 准备

1. 管理进程已在 `8100` 起来（按底座连 Redis / MySQL / Milvus）。
2. 复制 `.env.example` 为 `.env.local`，填写与 `AGENTSCOPE_ADMIN_TOKEN` 一致的 `VITE_ADMIN_TOKEN`。

## 启动

```bash
cd web-admin
pnpm install
pnpm dev
```

浏览器打开 `http://127.0.0.1:5174`。缺 Token 时顶栏红字提示，业务操作禁用。
