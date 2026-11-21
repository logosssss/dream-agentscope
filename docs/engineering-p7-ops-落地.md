# P7 `agent-ops` 抽取

状态：**已完成**（2026-11-21）

## 为什么有这一块

P1 把「同一张表两份 mapper」收掉了，但读写这些表的**行为**仍各写一份：

- `agent-access/src/main/java/com/zhu/access/application/**` 与 `agent-admin/src/main/java/com/zhu/admin/application/**` 有 **45 个同名类**；
- 归一包名、`QueryLimits` 类名与 `ReadThroughCache` 类名后逐对比对：**42 对零差异**；
- 只有删除请求三件套不同（管理端多 `recent(status, limit)` 与执行结果列，是超集）。

两份「零差异」的代码靠人肉同步，任何一边改语义（例如额度上限的 `normalize` 从 100 改成 200，或者状态闸门多判一个 `SUSPENDED`），另一边不会跟着变；同一租户在用户端与管理端会看到不同结果。这不是风格问题，是漂移源。

## 已锁定

| 项 | 决定 |
| --- | --- |
| 新模块 | `agent-ops`，位于 `agent-readmodel` 之后、`agent-db` 之前 |
| 依赖 | `agent-readmodel` + `agent-cache-api` + `agent-common`，仅此三个内部模块 |
| 包 | `com.zhu.ops.<域>`：`tenant` / `quota` / `attachment` / `approval` / `deletion` / `usage` / `health` / `support` |
| 删除请求 | 取管理端超集：`submit` / `owned` / `ownedOne` / `recent`，摘要与详情都带 `executedAt` / `executedNote` |
| 分页上限 | 两个 `*QueryLimits` 合成 `com.zhu.ops.support.QueryLimits`（`<1` → 50，上限 100） |
| 读穿缓存 | 两个 `*ReadThroughCache` 合成 `com.zhu.ops.support.ReadThroughCache` |
| 谁还能改策略 | 进程模块：批准在 `agent-admin` 的 decider，真删在 `agent-access` 的 `DeletionExecutionWorker` |
| 依赖方向 | `access → ops → readmodel`、`admin → ops → readmodel`；`ops` 不回头依赖任何进程模块 |
| 构建期护栏 | `ArchitectureTest.OPS_MUST_NOT_DEPEND_ON_BUSINESS` + 组件存在性断言 |

## 明确不做

- **X1 不合并两个进程的 controller / DTO**。用户的接口与管理员的接口本来就该不一样（鉴权头、审计口径、字段暴露范围都不同）。
- **X2 不把 `agent-ops` 做成「领域服务层」**。这里只放「两个进程都要的读写行为」，不做编排、不发事件。
- **X3 不引入 `@Transactional` 跨模块事务**。写多张表的用例（删除执行）仍在进程模块里显式控制。
- **X4 不迁移缓存实现**。`agent-ops` 只依赖 `CacheFacade`，Redis 实现留在 `agent-cache-redis`。
- **X5 不动 mapper**。SQL 与列映射仍在 `agent-readmodel`，`agent-ops` 只调用。

## 改动

1. 新建 `agent-ops/pom.xml`，根 `pom.xml` 的 `<modules>` 与 `dependencyManagement` 各加一条。
2. 45 个类迁到 `com.zhu.ops.<域>`，包名、跨类 FQN、两个 support 类名同步改写；同包冗余 import 清掉。
3. 删除请求三件套以管理端版本为准合并（带 `recent` 与执行列）。
4. 删除 `agent-access` 与 `agent-admin` 里全部 90 个原件（45 × 2）。
5. `agent-access/pom.xml`、`agent-admin/pom.xml` 加 `com.zhu:agent-ops`。
6. 全仓引用改写：`com.zhu.{access,admin}.{api,application}.<域>.<类>` → `com.zhu.ops.<域>.<类>`；同包隐式引用改为显式 import。
7. `agent-bootstrap/src/test/java/com/zhu/bootstrap/AuditReadThroughCacheTest.java` 改名 `OpsReadThroughCacheTest.java`，类名同步。
8. `ArchitectureTest` 加 `OPS_MUST_NOT_DEPEND_ON_BUSINESS`，断言里加 `com.zhu.ops.tenant.MysqlTenantUserCatalog`、`com.zhu.ops.deletion.DeletionRequestCatalog`、`com.zhu.ops.support.QueryLimits`。
9. `README.md` 模块表加 `agent-ops` 行；新建 `agent-ops/README.md`；本索引加 P7 行。

## 验收

| 项 | 结果 |
| --- | --- |
| `mvn -B -ntp -Pstrict -DskipTests compile` | BUILD SUCCESS（20 个 reactor 项） |
| `mvn -B -ntp -Pstrict -DskipTests test-compile` | BUILD SUCCESS |
| `mvn -B -ntp -Pstrict -pl agent-bootstrap -am -Dtest=ArchitectureTest test` | `Tests run: 2, Failures: 0, Errors: 0` |
| `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest"` | BUILD SUCCESS；agent-bootstrap `Tests run: 434, Failures: 0, Errors: 0, Skipped: 2` |
| 同名类数量 | access ↔ admin 从 45 → **0**（`agent-ops` 里各一份） |

两个 E2E 用例在本机跳过（无 Docker），由 CI 实际执行。

## 残留

1. `agent-access` 与 `agent-admin` 仍有同名的 **controller / DTO**（对外形状本就应该不同，未合并）。
2. `agent-ops` 目前没有自己的单元测试：行为由两个进程原有的 bootstrap 用例覆盖（`TenantQuotaTest`、`TenantDirectoryTest`、`UserTodoTest`、`TokenUsage*Test` 等）。
3. `MysqlDeletionRequestCatalog` 的 javadoc 提到了 `DeletionExecutionWorker`，是文本引用，不构成依赖。
4. `agent-ops` 未进部署包（没有 `spring-boot-maven-plugin` 配置），随 access/admin 打进各自 jar。

## 下一步

- 推 CI（本地 4 个提交未推：`444fb5e`、`0b783f3`，以及本次 P7）。
- 之后候选：对话链路端到端（会话 → 一轮 chat → run/message 落库）或「调度真消费」。
