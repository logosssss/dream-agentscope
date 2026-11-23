# agent-readmodel

多进程共用表的唯一 owner。同一张表只有一份 entity、一份 Mapper、一份 XML，用户进程与管理进程都从这里取，避免两边各写一遍后悄悄分叉。

坐标：`com.zhu:agent-readmodel`。包名：`com.zhu.readmodel`。

## 依赖

只依赖 `mybatis-plus-spring-boot3-starter`。不依赖 `agent-access`、`agent-admin`、`agent-runtime`、`agent-products`、`agent-state`、`agent-vector`、`agent-object-store`、`agent-cache`，也不依赖 AgentScope。ArchUnit 在 `agent-bootstrap` 里守着这条线。

## 为什么有这个模块

拆进程之后，`agent-access` 与 `agent-admin` 各自持有同一批表的 Mapper，entity 逐行相同、XML 只有 namespace 与 resultType 的 FQN 不同。任何一边改列名，另一边只能靠 `SchemaContractTest` 偶然发现；`agent-runtime` 与 `agent-admin` 的审计、模型、计价、安全干预四组更是整份复制。

现在按「表」而不是按「进程」归属：谁的表谁维护一份，进程只消费。`agent-db/src/test/java/com/zhu/db/SchemaContractTest.java` 同时检查两件事——共享表的 Mapper 必须在本模块存在、且不得在业务模块重复出现。

## 包

| 包 | 内容 | 用途 |
| --- | --- | --- |
| `entity.tenant` | `TenantRecord`、`TenantUser`、`LoginCredential` | 租户、成员、登录凭据 |
| `entity.quota` | `TenantQuotaRecord`、`QuotaUsageLog` | 额度上限与用量流水 |
| `entity.usage` | `TokenUsage` | token / 金额用量 |
| `entity.attachment` | `Attachment`、`AttachmentRef` | 附件目录与引用 |
| `entity.approval` | `UserTodo` | 待办（审批已改为进程内 `PendingAskFacade`） |
| `entity.feedback` | `UserFeedback` | 用户反馈 |
| `entity.deletion` | `DataDeletionRequest` | 删除请求 |
| `entity.event` | `AgentEventAudit` | 审计事件 |
| `entity.model` | `ModelConfig` | 模型配置 |
| `entity.billing` | `ModelPrice` | 每百万 token 单价 |
| `entity.safety` | `SafetyIntervention` | 安全干预记录 |
| `persistence.*` | 对应 `*Mapper` 与 `UsagePeriodTotal` | 只写一条 SQL 级别的查询/更新，不含业务判断 |
| `config` | `ReadModelMapperConfig` | `@MapperScan("com.zhu.readmodel.persistence")`，两个进程共扫同一包 |

资源路径：`src/main/resources/mapper/*.xml`，落在各进程 `mapper-locations: classpath*:/mapper/**/*.xml` 的扫描范围内，不需要额外配置。

## 使用约定

- 表结构以 `agent-db/src/main/resources/db/migration` 为准，本模块不写 DDL。
- 共享表的新列先加迁移，再改 entity/XML；`SchemaContractTest` 会在列名漂移时报错。
- 查询方法按用途命名（`listOwned`、`sumForPeriod`），不要在这里拼跨表业务规则；有判断逻辑就放到 `agent-access` / `agent-admin` 的 application 层。
- 写入方法（如额度上限、模型配置开关）保留在 Mapper，因为两边都要用同一份；但只允许单条语句，不允许在这里做「先查再改」。
- 不要加 `@Service`、`@Transactional` 或领域对象；ArchUnit 会拦。

## 还没有

CQRS 读模型、投影表、缓存层。现在只是「表的唯一 owner」，不是读写分离架构；等真有读放大再谈。
