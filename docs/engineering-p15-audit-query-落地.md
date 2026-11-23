# P15 审计查询：拿运行号看一轮到底做了些什么

状态：**已完成**（2026-11-23）

## 为什么有这一块

P14 让审计行带上了 `run_id` / `session_id`，但管理端当时只有一个「最近 N 条」的
`GET /api/admin/audits?limit=`。运维从报障里拿到请求号（P13 的 `X-Request-Id`）后，
能查到 `agent_run` 那一轮的结果，却**没法顺着运行号看中间发生了什么**——因为接口只能取「最新的」，
拿不到「某一轮的」。`web-admin` 的审计页也没有执行事件的页签。

这一块把「按运行号 / 会话 / 操作者回查审计」补齐，并在页面上暴露出来。

## 已锁定

| 决定 | 口径 |
|------|------|
| 过滤条件 | `userId`、`sessionId`、`runId` 三个都可有可无，**互相 AND** |
| 空值处理 | 空白串在查询前转成 `null`（SQL 里 `''` 不等于「没传」） |
| 条数 | 复用 `QueryLimits.normalize`：小于 1 按 50，大于 100 截到 100 |
| 顺序 | 按自增主键从新到旧，和「最近事件」一致 |
| 索引 | 新增 `V47__agent_event_audit_run_index.sql`：`KEY idx_run (run_id, created_at)` |
| 兼容 | `AuditQuery.events(...)` 是 `default` 方法，管理进程的测试替身不用为了新条件改签名 |
| 对外形状 | `AuditEventRow` 增加 `runId`（老行可以为空），其余字段不动 |
| 只读 | 不改写入路径，也不做导出 |

## 明确不做

- **X1 不做全文检索**：`payload` 是 JSON 短说明，不做 `LIKE` 匹配与结构化解析。
- **X2 不做导出 / 下载**：需要的话先从库里捞，接口只负责「看」。
- **X3 不做游标分页**：当前只取最近 N 条，按运行号查通常几条就够；量大再加。
- **X4 不改用户端**：用户端只有自己的运行详情，事件审计只给管理进程。
- **X5 不做租户维度的强校验**：管理端本来就是跨租户只读，鉴权在 Admin Token 上。

## 改动

1. `agent-readmodel/src/main/java/com/zhu/readmodel/persistence/event/AgentEventAuditMapper.java`：
   新增 `search(userId, sessionId, runId, limit)`。
2. `agent-readmodel/src/main/resources/mapper/AgentEventAuditMapper.xml`：新增 `search`，
   `<where>` + 三个 `<if>`，`ORDER BY id DESC LIMIT #{limit}`。
3. `agent-admin/src/main/java/com/zhu/admin/api/event/AuditEventRow.java`：加 `runId`。
4. `agent-admin/src/main/java/com/zhu/admin/api/event/AuditQuery.java`：加 `default events(...)`。
5. `agent-admin/src/main/java/com/zhu/admin/application/event/MybatisAuditQuery.java`：
   `recentEvents` 退化成 `events(null, null, null, limit)`，`events` 做空白转 `null` 与条数收口。
6. `agent-admin/src/main/java/com/zhu/admin/api/controller/event/AdminAuditController.java`：
   `GET /api/admin/audits` 增加 `userId` / `sessionId` / `runId` 三个可选参数。
7. `agent-db/src/main/resources/db/migration/V47__agent_event_audit_run_index.sql`：补索引；
   `MigrationCatalogTest` 改到 V47。
8. `agent-admin/src/test/java/com/zhu/admin/MybatisAuditQueryTest.java`：3 例（过滤条件下沉、
   空白转 `null` 且条数收口、`recentEvents` 等价于无过滤）。
9. `web-admin`：`api.types.ts` 加 `AuditEventRow`；`audit-api.ts` 加 `listAuditEvents({runId, sessionId, userId, limit})`；
   `views/AuditsView.vue` 增加「执行事件」页签（三个过滤输入 + 查询 / 清空 + 六列表格）。

## 验收实测

| 检查 | 结果 |
|------|------|
| `mvn -B -ntp -Pstrict -pl agent-admin -am -Dtest=MybatisAuditQueryTest` | 3 例全绿 |
| `web-admin` 的 `pnpm exec vue-tsc -b` | exit 0 |
| `web-admin` 的 `pnpm lint` | exit 0 |
| `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest"` | BUILD SUCCESS（bootstrap 454 例 / 0 失败 / 7 跳过，access 43、admin 46、runtime 29、ops 12） |
| `main` 的 `.github/workflows/ci.yml` 探针 | 改为把 `docker version` / `docker info` / `docker run hello-world` 的结论打成 `::warning title=docker-probe::` 注解，并给 verify job 显式 `DOCKER_HOST=unix:///var/run/docker.sock` |

## 残留

1. 只有「最近 N 条」，没有分页：按运行号查够用，按用户看历史会截断。
2. `payload` 原样显示 JSON 字符串，没有按事件类型展开字段。
3. 管理端还没有「运行详情」页：现在要人工把 `runId` 粘进审计页签（下一步）。
4. 更早的审计行 `run_id` 为空，按运行号查不到——这是 P14 之前的历史数据，不回填。

## 下一步

- 管理端加运行详情页（`agent_run` + 步骤 + 审计三块），从列表一键跳到「这一轮的事件」。
- 有按运行号回查的量之后再谈分页与 `payload` 结构化。
