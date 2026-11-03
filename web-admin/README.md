# web-admin（管理端联调）

Vue 3 + Vite + Pinia + Vue Router + Tailwind v4。同仓页面联调**管理进程**（`8100`），与搭子 `web/`（`8080`）分开入口。

已接：

- 租户 / 租户用户（列表、登记、状态动作）
- 待审批（列表、批准、拒绝；不触发工具执行）
- 额度（每日请求 / Token / 费用微单位 / 同时运行）
- 模型（配置登记列表；单价只追加新行）
- 审计（安全干预只读；操作审计只读）
- 保留策略（列表、登记；不执行清理）
- 任务调度（列表、登记；不触发执行）
- 评测（数据集列表/登记；追加用例；登记执行并打分；不调用模型）
- 账单（草稿 → 确认 → 开票 → 付款；不重算金额）
- 删除请求（按 ID 批准/拒绝；不执行删除）
- 工具（目录登记；权限规则登记/停用/恢复/改优先级；不执行工具；conditions 固定空）

鉴权为 `VITE_ADMIN_TOKEN`（对应 `AGENTSCOPE_ADMIN_TOKEN`）+ 可选操作人（`X-Operator-Id`），暂无管理员账号登录。

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
