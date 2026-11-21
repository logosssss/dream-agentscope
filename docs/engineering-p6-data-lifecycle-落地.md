# P6 数据生命周期（删除请求真执行 + 保留策略真清理）

状态：**已完成**（2026-11-21；真删路径由 Testcontainers 端到端验证，本机无 Docker 时跳过）

## 为什么有这一块

P0–P5 之后还有一个明显的说不过去的地方：**用户点「删除我的数据」，系统只改了个状态**。

- `data_deletion_request` 从 `PENDING` 变成 `APPROVED`，然后就没有下文了；
- `data_retention_policy` 能登记 `retain_days` 与 `action`，但没有任何代码去读它；
- README 的「明确不做」里明写着「保留策略真正清理、删除批准后真删」。

对一个会存对话、附件、知识库、情景记忆的产品，这不是「以后再说」的功能，是合规底线。
这一块把它补上：**批准后真删、按策略真清、删了什么可核对**。

## 已锁定

| 项 | 决定 |
| --- | --- |
| 进程分工 | 管理进程只改状态（`PENDING` → `APPROVED` / `REJECTED`）；真删在**用户进程** |
| 为什么不是管理进程 | 附件对象与知识向量只有用户进程装配了 `ObjectStore` / `VectorStore`；管理进程按设计只探测 Milvus、不建集合，塞进去会打破这条边界 |
| 交接方式 | 表状态：用户进程的执行器每秒级扫 `APPROVED AND executed_at IS NULL`，先抢成 `RUNNING` 再执行，最后写 `DONE` / `FAILED` |
| 可核对 | 管理端 `GET /api/admin/deletion-requests?status=&limit=` 列出近期请求（含 `executedAt` / `executedNote`），批准后可核对真删结果；`DONE` 绿、`FAILED` 红、`RUNNING` 黄 |
| 硬删范围（ALL） | `agent_run_step` → `attachment_ref` → `attachment`（含对象字节）→ `kb_chunk` / `kb_document` / `kb_knowledge_base`（含按 user+agent 删向量）→ `episodic_memory` / `user_profile` → `chat_message` / `agent_run` / `agent_session_meta` / `agent_task` → `agentscope_sessions` / `agentscope_store` → `login_credential`；`tenant_user.status` 改 `DELETED`（保留行） |
| 脱敏保留 | `agent_event_audit` / `safety_intervention` / `approval_record` / `quota_usage_log` / `token_usage` 的 `user_id` 换成 `deleted:<sha256 前 12 位>`：合规和计费要留行，但不该继续关联到人 |
| 类型语义 | `SESSION` 删会话相关（含工作区 KV）；`ATTACHMENT` 删附件与字节；`ALL` 以上全做 + 知识库/记忆/凭据 |
| 租户圈定 | 请求带 `tenant_id` 时所有身份表都加 `AND tenant_id = ?`，避免跨租户误删；审计类表没有租户列，按用户删 |
| 保留策略 | 只执行 `action=DELETE`；`SESSION→agent_session_meta.last_active_at`、`MESSAGE→chat_message.created_at`、`EVENT→agent_event_audit.created_at`、`ATTACHMENT→attachment.created_at`；`scope='*'` 全租户、否则按租户；每表一次最多 `retention-batch-rows` 行 |
| 安全阀 | `agentscope.lifecycle.enabled` 默认 **false**；`dry-run` 默认 **true**（只统计不删）；单表超过 `max-rows-per-table` 整表跳过交人工 |
| 幂等 | `RUNNING` 是抢占状态；dry-run 也写 `executed_at`（只留预览），要再预览就清空该行 `executed_at` |
| 迁移 | `V42__data_deletion_execution.sql`：`executed_at` / `executed_note` + `idx_status_requested` |
| 用户端入口 | `web/src/platform/privacy/PrivacyView.vue`（路由 `/privacy`，左侧轨「数」）：提交 `SESSION`/`ATTACHMENT`/`ALL`，列表展示真实进度，点开看提出时间与执行结果；状态词由 `deletionStatus.ts` 纯函数翻译并有单测 |

## 明确不做

- **X1 不把附件字节与向量做成「异步补偿」**：一次执行里同步删完；某个对象删失败只记进 `executed_note`，不重试队列 —— 现在没有可靠队列，假的重试比不重试更糟。
- **X2 保留策略不处理附件字节**：只删 `attachment` 表行（策略是「按天清」的粗粒度动作，逐对象对账成本高于收益）。要连字节删，走删除请求那条路径。
- **X3 不改管理端的审批语义**：批准仍然只是一次状态变更，执行结果是异步的；管理端不假装同步完成。
- **X4 不做「删除后恢复」**：真删就是真删，没有回收站。合规章节里写清楚。
- **X5 不给删除请求加第二种执行者**：不提供「用户进程挂了由管理进程代执行」的兜底 —— 那意味着把对象存储与向量依赖塞进管理进程。

## 改动清单

1. 迁移 `agent-db/src/main/resources/db/migration/V42__data_deletion_execution.sql`（2 列 + 1 索引）。
2. `agent-access/.../application/lifecycle/UserDataPurger.java`：硬删 + 脱敏 + 附件字节 + 知识向量，`dry-run` 与单表上限统一由内部 `step` 拦。
3. `agent-access/.../application/lifecycle/DeletionExecutionWorker.java`：扫 `APPROVED`、抢 `RUNNING`、写 `DONE` / `FAILED`、`@Scheduled` 入口（开关关着空转）。
4. `agent-access/.../application/lifecycle/RetentionSweeper.java` + `RetentionTargets.java`：策略 → 表/时间列映射（纯函数，单测）+ 按 `DELETE` 分批清理。
5. `agent-access/.../application/lifecycle/LifecycleProperties.java` + `config/LifecycleConfig.java`：六个旋钮，默认全关。
6. `agent-bootstrap/.../AgentPlatformApplication.java`：加 `@EnableScheduling`。
7. `agent-admin/.../MysqlDeletionRequestDecider.java`：注释改成「本进程只改状态，真删在用户进程」。
9. `agent-bootstrap/src/test/java/com/zhu/bootstrap/DataLifecycleE2ETest.java`：真 MySQL + 真 Redis，种一批 PII 行 → 调 `worker.runOnce()` → 断言行消失、审计脱敏、请求 `DONE`。
10. `agent-access/src/test/java/.../RetentionTargetsTest.java`：4 种类型映射 + 未知类型跳过。
11. 用户端：`web/src/platform/privacy/{PrivacyView.vue,deletionStatus.ts,deletionStatus.test.ts}` + `shared/api/deletion-api.ts` + `shared/types/api.types.ts` 两个类型 + 路由与左侧轨入口；`main.css` 补 `--color-warning` / `--color-success` 两个 token。
12. 配置与文档：`deploy/nacos/agent-bootstrap.yaml` 加 `agentscope.lifecycle` 段；`nacos-allowlist.yml` 加六个键；`README.md` 的「明确不做」与能力表改写；`MigrationCatalogTest` 从 V41 提到 V42；本文件 + `docs/INDEX.md`。

## 验收实测

| 项 | 结果 |
| --- | --- |
| `mvn -B -ntp -Pstrict test "-Dtest=RetentionTargetsTest,GateChainTest,AgentRuntimePropertiesTest" …` | `Tests run: 6`（access 4+2）全绿 |
| `DataLifecycleE2ETest`（本机无 Docker） | 编译通过；运行时 `Skipped: 1`（`disabledWithoutDocker`），CI 的 Docker runner 会真跑 |
| 全量回归 | 见本节末尾更新：`mvn -B -ntp -Pstrict test "-Dtest=!OssObjectStoreTest" …` |
| 边界护栏 | `MigrationCatalogTest` 已随 V42 更新为「V1..V42 连续无重复」 |
| 读侧单测 | `DeletionRequestCatalogTest` 5 例：登记无执行信息、状态过滤透传、limit 截到 100、空列表、详情执行列 |

## 残留

1. **dry-run 与真删共用同一行状态**：dry-run 预览会写 `executed_at`，之后要重跑得手工清空；如果以后要反复预览，得加独立列。
2. **附件字节删除是尽力而为**：对象不存在按忽略处理（`ObjectStore.delete` 语义），存储类型不匹配的会记进 `executed_note`，没有对账任务。
3. **保留策略不删向量**：清掉 `kb_document` 行后，Milvus 里可能留下孤儿向量。检索要求回表命中正文，孤儿不可见，但会占存储。
4. **多实例部署**：靠 `UPDATE … WHERE status='APPROVED'` 抢占，正确但同一请求失败最多重试一次（状态是 `FAILED`，要人工改回 `APPROVED`）。
5. **`agentscope_store` 用命名空间段匹配**：按 `0x1F` 分段做 LIKE，如果以后工作区隔离粒度改成别的形态，这里要跟着改。

## 下一步

剩下的「可登记不执行」只剩**调度真消费**（`agent_task_schedule`）；另外 P5 残留的「对话链路端到端」可以用进程内假模型补上。

工程侧后续见 [P7 `agent-ops` 抽取](./engineering-p7-ops-落地.md)。
