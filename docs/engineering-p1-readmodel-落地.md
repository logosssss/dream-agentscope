# 工程护栏 P1：`agent-readmodel` 抽取（已完成）

状态：**已完成**（2026-11-21）。

日期：2026-11-21。
背景：拆进程之后，同一张表在 `agent-access` / `agent-admin` / `agent-runtime` 各有一份 entity、Mapper 与 XML。
`agent-access` 与 `agent-admin` 有 67 个同名类，其中 15 张共享表的 Mapper 逐行相同（entity 只差 package 行，XML 只差 namespace/resultType 的 FQN）。
列名一改，另一边只能等 `SchemaContractTest` 偶然发现；`agent-runtime` 与 `agent-admin` 的审计/模型/计价/安全记录四组则是整份复制。
本专题按「表」归属，把共享表收进一个模块，不动查询语义。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 新模块 | `agent-readmodel`，坐标 `com.zhu:agent-readmodel`，包名 `com.zhu.readmodel`；在根 `pom.xml` 的 `agent-common` 之后 |
| 依赖 | 只依赖 `mybatis-plus-spring-boot3-starter`；不依赖 access / admin / runtime / products / state / vector / object-store / cache，也不依赖 AgentScope |
| 表清单 | `tenant`、`tenant_user`、`tenant_quota`、`quota_usage_log`、`login_credential`、`attachment`、`attachment_ref`、`data_deletion_request`、`user_todo`、`user_feedback`、`token_usage`、`agent_event_audit`、`model_config`、`model_price`、`safety_intervention`（15 张） |
| 包布局 | `entity.<域>` 放行映射，`persistence.<域>` 放 Mapper，`persistence.usage.UsagePeriodTotal` 放查询 DTO，`config.ReadModelMapperConfig` 做 `@MapperScan` |
| 扫描条件 | `ReadModelMapperConfig` 带 `@ConditionalOnProperty(name="agentscope.distributed.mysql.enabled", havingValue="true")`；MySQL 关闭时不得创建 Mapper（否则降级启动挂在 `Property 'sqlSessionFactory' ... are required`） |
| 命名归一 | access 的 `AccessTokenUsageMapper` 与 runtime 的 `RuntimeTokenUsageMapper` 合并为 `persistence.usage.TokenUsageMapper`；XML namespace 同步 |
| 模块依赖 | access / admin / runtime 在 `agent-common` 之后加 `com.zhu:agent-readmodel` |
| 契约测 | `SchemaContractTest` 由「各端 Mapper 互相比对」改为「readmodel 单边 `column=` ⊆ Flyway DDL」+「共享表 XML 不得出现在 access/admin/runtime」 |
| ArchUnit | `READMODEL_MUST_NOT_DEPEND_ON_BUSINESS`；另有 `readModelHoldsNoBusinessLogic`（readmodel 里不得出现 `@Service` / `@Component` / `@Transactional`，`config` 包除外） |

## 明确不做（本专题）

1. **X1** 不做读写分离、不加投影表、不加二级缓存；本模块只是「表的唯一 owner」。
2. **X2** 不把 application 层逻辑搬进来：`TenantAppService`、`QuotaGuard` 等仍留在 access/admin。
3. **X3** 不改表结构、不改列名、不合并表。
4. **X4** 不改 access/admin 的 controller 与路由。
5. **X5** 不追求「零重复」：各进程内私有表（`agent_run*`、`chat_message`、`eval_*`、`operation_audit`、`kb_*` 等）继续留在原模块。

## 迁移步骤（已完成）

1. 新建模块与 `ReadModelMapperConfig`，在根 POM 注册。
2. 从 access / admin / runtime 复制 15 张共享表的 entity（16 个类）、Mapper（16 个接口，其中 token 用量两处合并）、XML（15 个）到 readmodel。
3. 删除三模块内的重复文件 94 个（63 个 Java：entity 27、mapper 36；31 个 XML），并重写引用：
   `com.zhu.<mod>.infrastructure.persistence.entity.<域>.*` → `com.zhu.readmodel.entity.<域>.*`；
   `com.zhu.<mod>.infrastructure.persistence.<域>.*` → `com.zhu.readmodel.persistence.<域>.*`。
4. 把 `AccessTokenUsageMapper` / `RuntimeTokenUsageMapper` 的调用点（`MysqlTokenUsageQuery`、`MysqlTokenUsageWriter`）与 `bootstrap` 测试改为 `TokenUsageMapper`。
5. 修 Mapper 包名（复制脚本漏了 `.persistence` 段）与 XML namespace。
6. 给 `MybatisAuditRecorder`、`MysqlTokenUsageWriter` 补跨包 import（原来同包不需要）。

## 验收与实测

| 检查 | 结果 |
| --- | --- |
| `mvn -B -ntp -Pstrict -DskipTests compile` | 通过（17 个模块） |
| `mvn -B -ntp -Pstrict -pl agent-db test` | 3 个用例通过（Schema 契约 + 迁移目录 + 单 owner） |
| `mvn -B -ntp -Pstrict -pl agent-bootstrap -am -Dtest=ArchitectureTest test` | 2 个用例通过（含新增 readmodel 规则） |
| `mvn -B -ntp -Pstrict test "-Dtest=!OssObjectStoreTest"` | **430 个用例，0 失败 0 错误** |
| 共享表 Mapper 唯一性 | access / admin / runtime 的 `src/main/resources/mapper` 已无 15 张表的同名 XML |
| `agent-db` 迁移 | 仍为 41 个 SQL、版本连续（未改 DDL） |

## 仍未做 / 已知残留

1. `agent-admin` 与 `agent-access` 的 `README.md` 包表里仍列着已迁走的 `infrastructure.persistence.*` 行（本轮只改了「依赖」段与一句话说明），下次改这两个文档时逐行核对。
2. access 与 admin 仍有 **45 个同名类**，全部在 application / port 层（`TenantCatalog`、`TenantGate`、`MysqlTenantDirectory`、`MysqlAttachmentCatalog`、`FeedbackRecorder`、`TokenUsageQuery` 等）。它们是两个进程各自的策略与查询组合，本专题不合并；真要收，得先分清哪些是纯查询、哪些带进程策略。
3. `web` / `web-admin` 无前端改动，本专题不涉及。

## 后续块

P2 [`docs/engineering-p2-product-module-落地.md`](./engineering-p2-product-module-落地.md)（`ProductModule` 聚合，合并 `ResidentAgentBinding` 与 `ProductSemanticProvider`，让 WEB 通道显式只认 main）→
P3 [`docs/engineering-p3-testing-落地.md`](./engineering-p3-testing-落地.md)（`GateChain` / `agent-test-support` / access 首批测试）已完成 → P4 配置拆分校验与文档 status/链接检查。
