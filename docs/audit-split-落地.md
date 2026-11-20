# 拆分 agent-audit（已锁定）

日期：2026-11-19。  
本文只记 grill 收口与落地顺序。不改架构指南原文。

背景：`agent-audit` 名不副实且职责过大（事件审计 + 租户额度账单 + 附件反馈评测等）。  
目标按「正常业务」：**管理端与用户端各自管理业务代码**；**同库同表**；删除共享业务 jar。

与 [`docs/packaging-落地.md`](packaging-落地.md)「不做抽成 agent-ops」的关系：本专题 **不是** 再造 `agent-ops` 大杂烩，而是把实现 **折进** `agent-admin` / `agent-access` / `agent-runtime`，DDL 进 `agent-db`。该项对 packaging 旧「不做」的覆盖以本文为准。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 数据 | **同库同表**；两端业务 **Java 不共享 jar** |
| 拆法 | **整包拆光** `agent-audit`（最终删除该模块） |
| 代码归属 | **A**：管理能力 → `agent-admin`；用户侧 tenant/quota/附件/登录凭证等 → `agent-access`；事件审计写入、用量、安全干预、模型配置查找等 → `agent-runtime`（runtime ↛ access 不变） |
| DDL | 新建 **`agent-db`**：收拢 **全仓库** 现有 `db/migration`；仅 SQL/资源，无业务 Java；**仅用户进程**跑 Flyway |
| 防漂移 | schema **契约测**（CI）；Flyway 为唯一 DDL 源 |
| 接口 | 各端 **本地 api 包**（可同名不同包）；**不**抽共享 `agent-api` / 不保留 `com.zhu.audit` |
| 缓存 | 原 audit 读穿（ACTIVE 门 / 额度上限）随代码迁到归属模块；键与 TTL 约定见 [`docs/cache-落地.md`](cache-落地.md) 二期；仍只依赖 `cache-api` |
| 对外契约 | **不改** HTTP 路径 / 方法 / JSON 字段名（除非另专题） |

### 归属速查（搬迁时）

| 域（现 audit） | 目标 |
| --- | --- |
| tenant / tenant-user / client-login / quota（含流水写） | access（用户热路径）；admin 侧目录/写接口 → admin |
| attachment / feedback / deletion（用户申请）/ todo | access；deletion 决定等管理动作 → admin |
| billing / model-price / model-config 目录 / eval / retention / schedule / operation-audit 目录 | admin |
| `AuditRecorder`、token 用量写、safety 干预写、`ModelConfigLookup` | runtime |
| 事件查询等只读管理面 | admin |

同一张表可被 admin 写、access/runtime 读（或各端自备 Mapper）；**禁止**再引入共享业务实现模块。

## 明确不做

1. 管理端与用户端 **双库 / 双表真相**  
2. 用户进程经管理端 HTTP 读租户/额度（进程间服务化另专题）  
3. 新建 `agent-ops` / `agent-platform` 类大杂烩替代 audit  
4. 共享业务 api jar（仅允许 `agent-db` 这种无业务 Java 的 DDL 模块）  
5. 顺手改对外 URL / JSON / 额度业务公式  
6. 不改架构指南正文  

## 落地顺序（一次一块）

1. **① 锁文档** — 本块：本文。  
2. **② `agent-db`** — 建模块；收拢全仓 `db/migration`；根 POM / bootstrap 依赖；确认用户进程仍能迁移；admin 不跑 Flyway。  
3. **③ 管理侧迁入** — 原 audit 中管理目录/写能力迁入 `agent-admin`（本地 api + application + mapper）；admin **不再**依赖 `agent-audit`。  
4. **④ 用户侧迁入** — tenant/quota/附件等 → `agent-access`；事件/用量/安全/模型查找 → `agent-runtime`；bootstrap/access/runtime **不再**依赖 `agent-audit`。  
5. **⑤ 删模块 + 文档** — 移除 `agent-audit`；根 POM / ArchUnit / packaging / 图册 / README 对齐。  
6. **⑥ schema 契约测** — 锁关键表/列（或 flyway 版本）与两端 Mapper 用列一致。  

## 状态

grill 已结束；**①～⑥ 全部完成**（含 `agent-db` 的 `SchemaContractTest`：双端 Mapper 列 ⊆ Flyway）。本专题收口。若再改规范，先改本文再动代码。
