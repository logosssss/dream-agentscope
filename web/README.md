# web（客户端联调）

Vue 3 + Vite + Pinia + Vue Router + Tailwind v4。同仓页面联调用户进程。

登录后进入 `AppLayout`：最左 Agent 切换轨（registry）。当前插件：

- `/companion` 智能搭子：会话栏 / 轻提示插槽；助手气泡 Markdown（`markdown-it`，关 html）；SSE 默认 `/api/chat/stream`（WEB / ChatUi）
- `/sketch` 速记草稿：占位 Agent，无侧栏
- `/api-console` API 控制台：复用 `chat-core`；侧栏只列 `channel=API`；SSE 仅 `/api/agent/chat/stream`；不替代搭子主聊天

当前进度：前端方案 Step 1～6 主路径已落地（Markdown、自动滚动、停止按钮、暗黑模式）。左侧轨底「深/浅」切换，记住到 localStorage。平台页 `/attachments`：上传、列表、下载、停用/恢复（不删对象）。搭子侧栏「待确认」：自批/拒绝、流内等待确认提示、批准后手动「重发上一条」（不改 SSE、不自动发）。API Channel 专题见 `docs/api-channel-落地.md`（不做 web-admin 控制台、不宣称 API Key / IM 消息对话）。

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

浏览器打开提示的地址（默认 `http://127.0.0.1:5173`）：`/` 为公开介绍页，点「开始使用」进入 `/companion`（未登录会跳转登录/注册）。
