# web（搭子联调 Step 1）

Vue 3 + Vite + Pinia + Vue Router + Tailwind v4。同仓页面联调用户进程。

## 准备

1. 用户进程已在 `8080` 起来（Redis / MySQL / Milvus 按底座要求）。
2. 本机执行 `deploy/mysql/seed-demo-tenant-user.sql`。
3. 复制 `.env.example` 为 `.env.local`，填写与 `AGENTSCOPE_CALLER_TOKEN` 一致的 `VITE_CALLER_TOKEN`。

## 启动

```bash
cd web
pnpm install
pnpm dev
```

浏览器打开提示的地址（默认 `http://127.0.0.1:5173`），登录后进入 `/companion`。
