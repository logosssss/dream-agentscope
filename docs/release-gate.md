# 开源前门禁清单

本地验收通过后再做线上部署与开源。本清单对应已锁定口径：自动化全绿 + 固定冒烟；「还没有」的能力文档化即可，不挡门禁。

线上域名（部署后写入公开说明）：`https://www.dream-saas.com/`  
默认开源分支目标：`main`（由 `feat/agentscope-platform-base` 合并而来）  
许可证目标：Apache-2.0  

门禁期间只修冒烟/检查缺陷，并做**演示路径**上的功能与前端体验优化；不新开大模块。

---

## 0. 环境前提（勾选表示本机已具备）

- [x] JDK 21；仓库根目录可执行 `mvn`
- [x] Node / pnpm；`web`、`web-admin` 可 `pnpm install`
- [x] 本机可连 **Redis、MySQL、Milvus**（与线上同一类实例配置即可）
- [x] **Nacos** 已部署且本机 `NACOS_ADDR` 指向同一套；能拉到启动配置（用户：`agent-bootstrap-infra.yaml` + `agent-bootstrap.yaml`；管理：`agent-admin-infra.yaml` + `agent-admin.yaml`，group `AGENT_PLATFORM`）
- [x] 根目录 `.env` 至少含 Nacos 四件套（及未迁 infra 时的连接/密钥兜底）；`AGENTSCOPE_CALLER_TOKEN` 与 `AGENTSCOPE_ADMIN_TOKEN` 必须不同（可在对应 infra yaml）
- [x] 模型可用（如百炼）；本地联调注意月额度与租户每人额度
- [x] MySQL 已迁移；已执行 `deploy/mysql/seed-demo-tenant-user.sql`（或等价演示租户）
- [x] 用户进程 `8080`、管理进程 `8100` 能起来；`web`（5173）、`web-admin`（5174）能起来
- [x] 本地前端：`web/.env.local` 的 `VITE_CALLER_TOKEN` = 用户进程 Caller；`web-admin/.env.local` 的 `VITE_ADMIN_TOKEN` = **Admin** Token（勿与 CALLER 混用）

说明：线上公网站点 **Caller Token 由 Nginx 代填**，浏览器包内不放 `VITE_CALLER_TOKEN`。本地开发可用 Vite 代理或 `.env.local`，与线上注入方式分开验收。

---

## 1. 自动化（必须全绿）

在仓库根目录、**JDK 21**：

```bash
mvn test
```

- [x] `mvn test` 通过（含 ArchUnit 等，不连真实中间件）

前端：

```bash
cd web && pnpm exec vue-tsc -b
cd ../web-admin && pnpm exec vue-tsc -b
```

可选加强：

```bash
cd web && pnpm build
cd ../web-admin && pnpm build
```

- [x] `web` 类型检查通过
- [x] `web-admin` 类型检查通过
- [ ] （可选）两端 `pnpm build` 通过

---

## 2. 开源敏感面（必须）

- [x] 工作区 / 将公开的分支中**没有** `.env`、真实 Token、模型 Key、内网账号密码
- [x] `.gitignore` 已忽略 `.env`、`.env.local` 等；历史中若曾误提交，已轮换密钥并清理
- [x] 存在可用的 `.env.example` / 前端 `.env.example`（仅占位，无真实值）（根目录与 `web/`、`web-admin/` 均有）
- [x] `.cursor/handoff*` 等内部交接私货已从将公开默认分支移除或停止跟踪（已 `git rm --cached` 并写入 `.gitignore`）
- [x] 根目录 `LICENSE`（Apache-2.0）、面向陌生人的 README 启动说明

---

## 3. 手测冒烟 — 基础设施

- [x] `GET http://127.0.0.1:8080/health/live` 正常
- [x] `GET http://127.0.0.1:8080/health/ready` 正常（MySQL/Redis/Milvus 可达）
- [x] `GET http://127.0.0.1:8100/health/live` 正常
- [x] `GET http://127.0.0.1:8100/health/ready` 正常
- [x] 管理端带正确 Admin Token 访问业务接口**不再** `caller token is invalid`
- [x] 用 **CALLER** Token 打管理审批接口应失败；用 **ADMIN** Token 应成功（确认两者未串）

---

## 4. 手测冒烟 — 用户端（`web` / 8080）

### 4.1 注册与登录

- [x] 打开注册页，新账号注册成功并进入系统（`gatesmoke1104`）
- [x] 退出后密码登录成功
- [x] 错误密码有明确提示，且不泄露「用户是否存在」以外的敏感信息（保持现有文案口径即可）（API：`账号或密码不正确`）

### 4.2 搭子对话

- [x] `/companion` 可发消息；SSE 有增量或终态回复
- [x] 会话侧栏可新对话、切换历史（若有历史）
- [ ] 停止生成可用（若当次有长回复）（本轮短回复未点停）

### 4.3 待确认（`echo_text` 为 ASK）

- [x] 触发 `echo_text` 后，流内有等待确认类提示
- [x] 侧栏「待确认」出现条目（同会话同工具**不应**再刷出十几张新单；历史堆积可先拒绝清掉）
- [x] **批准**后出现「重发上一条」，**不会**自动续跑
- [x] 点「重发上一条」后工具被放行，能看到回声结果
- [x] **拒绝**走站内确认层（非浏览器原生丑窗），拒绝后条目消失

### 4.4 附件

- [x] 平台页附件：上传成功并出现在列表（API 冒烟上传成功；UI 文件选择器未点通）
- [x] 下载文件名可读（非乱码/`=?UTF-8?Q?...`）（`Content-Disposition` 使用 `filename*=UTF-8''…`）
- [x] 停用后可恢复（或不再出现在启用列表且恢复可用）

### 4.5 知识库（入库 + 对话检索）

- [x] 客户端知识库页：建库成功（API 建库成功）
- [x] 写入纯文本或上传文本文件；文档列表可见；索引状态可观察到进展或成功（重启后 `PENDING`→`SUCCESS`）
- [x] 在搭子中提问库内内容，能触发知识检索且回复能用上库内信息（或明确看到检索工具成功）（SSE：`knowledge_search` + 答出「紫丁香花开时分」）

---

## 5. 手测冒烟 — 管理端（`web-admin` / 8100）

- [x] 顶栏 Admin Token 有效，业务页可加载
- [x] **审批**：能看到待审批列表；批准或拒绝成功（操作人空时默认 `admin`）（本轮列表为空属正常；Token 与接口 200；用户端批准/拒绝已验）
- [x] 其它已接页面（租户、用户、工具、额度、模型等）**点开不白屏、不 401**（不要求每条都做写操作）（租户/审批/工具 UI + 多条 admin API 200）

---

## 6. 门禁通过后的下一步（本清单不执行，仅备忘）

1. 线上同机部署双 Java + Nginx 前端；复用服务器已有 Redis/MySQL/Milvus/Nacos  
2. HTTPS（已具备）下开放注册；Nginx **代填**用户端 Caller Token；管理端子域/子路径 + 强 Admin Token  
3. 清理私货 → 合并功能分支到 **`main`** 并设为默认 → 加 Apache-2.0 LICENSE → 公开仓库  
4. 系列教程交其他 Agent，不挡本门禁  

---

## 7. 已知不挡门禁（文档化即可）

以下保持「还没有 / 弱依赖 / 非目标」，不因未做而判门禁失败：

- RocketMQ、服务发现、MCP  
- Nacos **infra**（连接/密钥）热更新；业务 yaml 热更新已落地（非法拒收、仅影响新请求）
- 管理员账号登录页（管理端现阶段靠 Admin Token）  
- 按保留策略真正清理、批准删除后真删、调度真正消费  
- 教程正文与配图（另开 Agent）  

---

## 签字

| 项 | 记录 |
| --- | --- |
| 日期 | 2026-11-04 |
| 分支 / 提交 | `feat/agentscope-platform-base` @ `b8fe762`（工作区含 KB 集合名修复，未提交） |
| 自动化 | 通过 |
| 冒烟 | **通过**（4.5 重启后复验通过） |
| 敏感面 | **部分通过**（handoff 仍跟踪；LICENSE / 根 `.env.example` 开源前补；不挡本地冒烟） |
| 备注（缺陷与是否已修） | KB 硬编码集合名已修并经重启复验：索引 SUCCESS，对话 `knowledge_search` 命中。开源前仍须：停跟踪 handoff、补 LICENSE、可选根 `.env.example`。 |
