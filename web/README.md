# web（客户端联调）

Vue 3 + Vite + Pinia + Vue Router + Tailwind v4。同仓页面联调用户进程。

登录后进入 `AppLayout`：最左 Agent 切换轨（registry）。当前插件：

- `/companion` 智能搭子：会话栏 / 轻提示插槽；助手气泡 Markdown（`markdown-it`，关 html）；SSE 默认 `/api/chat/stream`（WEB / ChatUi）
- `/sketch` 速记草稿：占位 Agent，无侧栏
- `/api-console` API 控制台：复用 `chat-core`；侧栏只列 `channel=API`；SSE 仅 `/api/agent/chat/stream`；不替代搭子主聊天

当前进度：前端方案 Step 1～6 主路径已落地（Markdown、自动滚动、停止按钮、暗黑模式）。左侧轨底「深/浅」切换，记住到 localStorage。平台页 `/attachments`：上传、列表、下载、停用/恢复（不删对象）。`chat-core`：Enter 发送 / Shift+Enter 换行。搭子可随消息传附件（选文件 / 拖拽 / 粘贴图，先上传再带 `attachmentIds`，最多 3；历史气泡可下载）。搭子侧栏「待确认」：自批/拒绝、流内等待确认提示、批准后手动「重发上一条」（不改 SSE、不自动发）。API Channel 专题见 `docs/api-channel-落地.md`（不做 web-admin 控制台、不宣称 API Key / IM 消息对话；不做随消息附件）。

## 准备

1. 用户进程已在 `8080` 起来（Redis / MySQL / Milvus 按底座要求；需已迁移含 `chat_message` 的 V37）。
2. 本机执行 `deploy/mysql/seed-demo-tenant-user.sql`。
3. 复制 `.env.example` 为 `.env.local`：本地直连填 `VITE_CALLER_TOKEN`（与 `AGENTSCOPE_CALLER_TOKEN` 一致）；现网静态构建改设 `VITE_GATEWAY=true`，令牌由 Nginx 注入，**不要把令牌打进前端包**。

## 启动

```bash
cd web
pnpm install
pnpm dev
```

浏览器打开提示的地址（默认 `http://127.0.0.1:5173`）：`/` 为公开介绍页，点「开始使用」进入 `/companion`（未登录会跳转登录/注册）。

## 检查

```bash
pnpm lint          # ESLint（flat config，vue/essential + typescript-eslint recommended）
pnpm test          # Vitest + jsdom，收 src 下 *.test.ts
pnpm build         # vue-tsc -b && vite build
```

`pnpm-workspace.yaml` 里固定了两条与本机/CI 一致的设置：`nodeLinker: hoisted`（Windows 上 Node
解析 junction 布局不稳定，隔离布局会让 eslint/vue-tsc 找不到包）与 `overrides: estree-walker 2.0.2`
（vitest 5 拉 ESM-only 3.x，与 vue-tsc 的 CJS 2.x 冲突）。所以 `pnpm-lock.yaml` 是**唯一**锁文件，
仓库里不再保留 `package-lock.json`。
