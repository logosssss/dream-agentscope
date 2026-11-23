# P16：管理端运行详情（P0 → P16 的第 16 块）

状态：**已完成**（2026-11-23）。上游依赖 P13（请求号）、P14（审计归属）、P15（审计查询）。

## 为什么有这一块

P13 让每一轮带上 `request_id`，P14 让审计行带上 `run_id`，P15 让管理端能按运行号过滤审计。
但管理端**拿不到运行本身**：`agent_run` / `agent_run_step` 的 Mapper 只存在于 `agent-access`，
管理进程就算知道运行号，也只能看到审计行、看不到「这一轮的结论、耗时、错误码和步骤」。

这次把这两张表的形状收进 `agent-readmodel`（两个进程都要读同一张表，不能各写一份），
并在管理端补上只读的运行列表与详情，让报障链路闭环：
**用户给出请求号 → 管理端查运行 → 用运行号看审计事件**。

## 已锁定

| 决定 | 说明 |
| --- | --- |
| `agent_run` / `agent_run_step` 形状归 `agent-readmodel` | access 的写入路径与 admin 的读取路径共用一份 entity / Mapper / XML |
| 管理端只读 | 没有任何修改运行或步骤的接口；权限由 Admin Token 把住 |
| 列表条件三个都可选 | `userId` / `sessionId` / `status`，空串转 `null`（否则 SQL 会去匹配空串） |
| 条数收口 | 走 `QueryLimits.normalize`：`< 1` 当 50，超过 100 截到 100 |
| 详情不按用户过滤 | 运维要看别人的失败；`findByRunId` 只按运行号 |
| 找不到给 404 | 不返回空对象，调用方要能区分「没有这一轮」 |
| 管理进程显式导入 `ReadModelMapperConfig` | 管理进程只扫 `com.zhu.admin`，共享表 Mapper 不在扫描范围（见下） |
| 时间给 ISO 字符串 | 空值给空串，前端不用判 `null` |

## 顺带修掉的真实缺陷

**管理进程在 MySQL 打开时其实起不来。**

- `agent-admin/src/main/resources/application.yml` 里 `agentscope.distributed.mysql.enabled: true`；
- `AdminMapperConfig` 只 `@MapperScan("com.zhu.admin.infrastructure.persistence")`；
- `agent-readmodel` 的 `ReadModelMapperConfig`（`@MapperScan("com.zhu.readmodel.persistence")`）在 `com.zhu.readmodel.config` 包，
  **管理进程的组件扫描只到 `com.zhu.admin`，从来没人导入它**；
- 而 `MybatisAuditQuery`、`MybatisDeletionRequestCatalog` 等十几个 Bean 都注入 readmodel 的 Mapper。

于是启动时报 `NoSuchBeanDefinitionException: No qualifying bean of type 'com.zhu.readmodel.persistence.event.AgentEventAuditMapper' available`，
`adminAuditController` → `mybatisAuditQuery` 一路建不起来。只是因为管理端此前很少在 MySQL 打开的情况下被真正启动过，这个缺陷一直没暴露。

修法：`AdminApplication` 的 `@Import` 加上 `ReadModelMapperConfig.class`，并加
`agent-admin/src/test/java/com/zhu/admin/AdminMySqlWiringTest.java` 守住：
`@SpringBootTest` 里打开 MySQL 开关、把数据源指向不可达地址（`initialization-fail-timeout=-1`，不连真库），
断言 `AgentEventAuditMapper` / `AgentRunMapper` / `AuditQuery` 各有 1 个 Bean。

顺带把 `TestDependencyDoubles` 从 `@Configuration` 改成 `@TestConfiguration`：
它原来会被组件扫描捎进每个 `@SpringBootTest`，与真实 `MybatisAuditQuery` 撞成 2 个 `AuditQuery` Bean。

## 改动

1. `agent-readmodel` 新增 `entity/run/{AgentRun,AgentRunStep}`、`persistence/run/{AgentRunMapper,AgentRunStepMapper}`、
   `resources/mapper/{AgentRunMapper,AgentRunStepMapper}.xml`（从 `agent-access` 迁入并改包）。
2. `AgentRunMapper` 新增 `search(userId, sessionId, status, limit)` 与 `findByRunId(runId)`。
3. access 侧三处引用改到 readmodel 新包：`MysqlRunQuery`、`MysqlRunLog`、`MysqlSessionTurnCount`；两个 bootstrap 测试同步。
4. `agent-db` 的 `SchemaContractTest.READMODEL_OWNED` 增加 `agent_run` / `agent_run_step`（现 18 张表）。
5. 管理端新增 `api/run/{RunRow,RunStepRow,RunDetail,AdminRunQuery}`、`application/run/MybatisAdminRunQuery`、
   `api/controller/run/AdminRunController`（`GET /api/admin/runs`、`GET /api/admin/runs/{runId}`）。
6. `web-admin` 新增 `shared/api/runs-api.ts`、`RunRow` / `RunStepRow` / `RunDetail` 类型、`views/RunsView.vue`、路由 `/runs` 与导航「运行」。
   详情面板把 `agent_run` 的步骤与 `agent_event_audit` 的同一 `run_id` 事件并排显示。
7. 新测 `agent-admin/src/test/java/com/zhu/admin/MybatisAdminRunQueryTest.java`（3 例）。
8. `AdminApplication` 显式导入 `ReadModelMapperConfig`；`AdminMySqlWiringTest` 新增；`TestDependencyDoubles` 改 `@TestConfiguration`。

## 明确不做

- **X1 不做运行的写操作**（重跑、取消）：需要执行面配合，单开一块。
- **X2 不做全文检索**：`input_summary` / `output_summary` 只是摘要，正文在 `chat_message`。
- **X3 不做导出 / 分页游标**：先按 `limit` 收口，超过 100 条的分页需求等真有量再说。
- **X4 不在列表里带步骤**：列表 50 行 × 每轮多步会把响应撑大，步骤只在详情里查。
- **X5 不改用户端**：用户端 `/api/runs` 仍是「只看自己的」，租户/用户收口逻辑不动。

## 验收

| 项 | 结果 |
| --- | --- |
| 编译 | `mvn -B -ntp -Pstrict -DskipTests test-compile` BUILD SUCCESS |
| 管理端单测 | `MybatisAdminRunQueryTest` 3 例、`AdminMySqlWiringTest` 1 例、`AdminContextTest` 1 例全绿 |
| 全量 | `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest"` BUILD SUCCESS（bootstrap 455 例 / 0 失败 / 7 跳过） |
| 前端 | `web-admin` 的 `pnpm exec vue-tsc -b` 与 `pnpm lint` 均 exit 0 |

## 残留

1. 管理端没有「按请求号直接跳运行」的输入框——现在要先在「运行记录」里按用户/会话筛，或直接改 URL；
   请求号列已展示，但还不能一键反查。
2. 详情页的审计事件只在 P14 之后落库的行上有效；更早的行要去「审计 → 执行事件」按会话查。
3. `agent_run_step` 没有 `created_at`，步骤顺序只能靠 `step_no`。
4. 管理端仍没有「这一轮花了多少 token / 多少钱」的展示，`token_usage` 要按 `run_id` 关联才行（当前 `token_usage` 没有 `run_id` 列）。

## 下一步

按阻塞程度：先看 CI 的 `docker-java-probe` 结论（为什么 shell 有 Docker 而 Testcontainers 判没有），
再把「按请求号反查运行」补进管理端输入框；之后考虑 `token_usage.run_id` 与成本列。
