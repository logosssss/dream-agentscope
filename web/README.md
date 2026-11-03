# web（客户端联调）

Vue 3 + Vite + Pinia + Vue Router + Tailwind v4。同仓页面联调用户进程。

登录后进入 `AppLayout`：最左 Agent 切换轨（registry）。当前插件：

- `/companion` 智能搭子：会话栏 / 轻提示插槽；助手气泡 Markdown（`markdown-it`，关 html）
- `/sketch` 速记草稿：占位 Agent，无侧栏

当前进度：前端方案 Step 1～6 主路径已落地（Step 6 本块做 Markdown 流式渲染；停止按钮与自动滚动此前已有）。暗黑模式未做。

## 准备

1. 用户进程已在 `8080` 起来（Redis / MySQL / Milvus 按底座要求；需已迁移含 `chat_message` 的 V37）。
2. 本机执行 `deploy/mysql/seed-demo-tenant-user.sql`。
3. 复制 `.env.example` 为 `.env.local`，填写与 `AGENTSCOPE_CALLER_TOKEN` 一致的 `VITE_CALLER_TOKEN`。

## 启动

```bash
cd web
pnpm install
pnpm dev
```

浏览器打开提示的地址（默认 `http://127.0.0.1:5173`），登录后进入 `/companion`。
