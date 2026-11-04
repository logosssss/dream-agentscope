# Handoff：开源前门禁

下一场：用户说「开始验收」时按 `docs/release-gate.md` 跑自动化并协助冒烟。说「继续落地」仍按指南「还没有」做一块（门禁期间优先修 bug + 演示路径优化，不大模块）。

## 已锁定（grill）

- 门禁 = 自动化 + 固定冒烟 + 敏感面；清单见 `docs/release-gate.md`
- 顺序：门禁 → 线上同机部署（已有中间件/Nacos/HTTPS）→ 现仓开源 Apache-2.0 / `main` → 教程另 Agent
- 公开站：注册开放；Caller Token Nginx 代填；管理端强 Admin Token
- 冒烟含：注册登录、对话、待确认、附件、知识库入库+检索、管理审批

## 从哪接着

分支：`feat/agentscope-platform-base`。开源前再合并到 `main`。

## 另一台机器开场白

读 `docs/release-gate.md` 后说：「开始验收」或「按门禁清单跑」。
