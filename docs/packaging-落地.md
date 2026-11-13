# 全项目分包落地（已锁定）

日期：2026-11-12。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 分包轴 | **混合 C**：对外门面（`controller` / `dto` / `api`）跟路由或资源域；对内实现（`application` / `persistence`）跟业务域，允许与门面不完全同名 |
| 深度 | **C**：`controller` / `dto` / `api` 必须有域子包；`application` / `persistence` 仅热点（单目录类多）或与已有 api 域对齐时才拆；`config` / `util` / `health` / `support` 等横切可扁平或单独横切包 |
| 域名词表 | **C**：admin 能对齐 access / audit 的用同名；管理独有另起（如 `tool`、`billing`、`eval`） |
| 落地顺序 | **A（由外到内）**：admin 门面 → access dto → audit persistence → 其余 application / contract / task 热点 |
| admin 第一块形态 | `api.controller.<域>` + `api.dto.<域>` 平行；`AdminAuditController` **拆类**为 `event` + `approval`，**URL 不变** |
| 文档 | 本文件为总锁定；各模块 README 包表随块更新 |

## 明确不做

1. 不改任何对外 URL / HTTP 方法 / JSON 字段名  
2. 不改类的业务前缀命名（仍叫 `AdminXxx` 等）  
3. 不顺手重写业务逻辑、合并或删除接口  
4. 不把 `agent-audit` 里「以后才抽到 agent-ops」的代码真拆成新模块  
5. 不改 MyBatis XML 在 namespace 以外的 SQL（persistence 后拆时只动 Java 包与 namespace）  
6. **第一块**只动 `agent-admin` 的 `api.controller` + `api.dto`（及必要 import / README），不动 `application` / `nacos`

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **admin 门面** — 已完成  
   拆 `controller` / `dto` 域子包；`AdminAuditController` → `event.AdminAuditController` + `approval.AdminApprovalController`。  
2. **access dto** — 已完成  
   `api.dto` 按已有 controller 域平行拆：`account` / `auth` / `chat` / `attachment` / `knowledge`。  
3. **audit persistence** — 已完成  
   `infrastructure.persistence.<域>` / `entity.<域>` 对齐 `api.*`；XML 仍扁平，只改 namespace / type。  
4. **access.application.chat 细分** — 已完成  
   `gateway` / `session` / `message` / `run`；`ChatAppService`、`ApprovalNotificationListener` 仍留在 `application.chat`。  
5. **state.api** — 已完成  
   `api.store` / `api.memory` / `api.knowledge`。  
6. **semantic.api** — 已完成  
   `api.prompt` / `api.tool`。  
7. **semantic.contract** — 已完成  
   `contract.product` / `tool` / `permission` / `safety`。  
8. **access.infrastructure.persistence** — 已完成  
   `persistence.session` / `message` / `run` / `task`，`entity` 同域；XML 仍扁平，只改 namespace / type。  
9. **access.application.auth 细分** — 已完成  
   `auth.session` / `auth.wx` / `auth.gate`。  
10. **runtime.task 细分** — 已完成  
   根留 `AgentTask`；`task.publish` / `handle` / `log`。  
11. **其余热点（深度 C）** — 已完成；顺序 **⑪a → ⑪b → ⑪c**  
    - **⑪a `semantic.config`** — 已完成  
      按 `contract` 对齐：`config.permission` / `config.product` / `config.safety`；  
      `SemanticConfig` 留 `config` 根；本块无 tool 配置类则**不建**空 `config.tool`。  
      域表见下「⑪a 域表」。  
    - **⑪b `semantic.application`** — 已完成  
      `application.prompt` / `application.tool` / `application.permission`；  
      `package-info` 留 `application` 根；`MysqlToolPermissionCatalog` 进 `permission`；  
      `CatalogQueryLimits` 因跨包引用改为 `public`（仍在 `tool`）。  
      域表见下「⑪b 域表」。  
    - **⑪c `runtime.application`** — 已完成  
      `application.turn` / `application.stream` / `application.usage`；  
      `ResolvedChatModel` 进 `turn`；`AgentTurnSupport` 跨包引用 stream/usage。  
      域表见下「⑪c 域表」。  
    - **每块边界**：搬家 + 全仓库 import + **该模块** README 包表 + 本文进度 + 相关 `mvn test`；  
      不改 URL / JSON / 业务逻辑；不扩到下一块；不必改根 README / release-gate。  
    - **明确不拆（本轮）**：`admin.application`（仅 3 类，深度 C 不够热）；`runtime.domain` 根护栏类暂留。

## ⑪a 域表（`semantic.config`）

| 子包 | 类 |
| --- | --- |
| `config`（根） | `SemanticConfig` |
| `config.permission` | `PermissionProperties`、`PermissionMapperConfig`、`ConfiguredPermissionService`、`ConfiguredUnconditionalPermissionCatalog` |
| `config.product` | `CompanionPersona`、`PlatformSemanticProvider` |
| `config.safety` | `AbsentSemanticRiskJudge`、`ClasspathCrisisPrompt` |

## ⑪b 域表（`semantic.application`）

| 子包 | 类 |
| --- | --- |
| `application`（根） | `package-info` |
| `application.prompt` | `PromptAppService` |
| `application.tool` | `MysqlToolRegistryCatalog`、`StoredToolCatalog`、`CatalogQueryLimits` |
| `application.permission` | `StoredPermissionService`、`StoredUnconditionalPermissionCatalog`、`MysqlToolPermissionCatalog` |

## ⑪c 域表（`runtime.application`）

| 子包 | 类 |
| --- | --- |
| `application.turn` | `AgentTurnSupport`、`AbsentSessionTurnCount`、`ResolvedChatModel` |
| `application.stream` | `AgentStreamEvents`、`CrisisReplyOutlet` |
| `application.usage` | `AuditUsageMeter`、`ModelCallUsage` |

## admin 第一块域表

| 域 | controller（拟） | dto（拟） |
| --- | --- | --- |
| `event` | `AdminAuditController`（仅 `GET /api/admin/audits`）、`AdminOperationAuditController` | `OperationAuditRequest` |
| `approval` | `AdminApprovalController`（`GET/POST …/approvals…`） | `ApprovalDecisionRequest` |
| `billing` | `AdminBillingController`、`AdminModelPriceController` | `BillingDraftRequest`、`ModelPriceRequest` |
| `model` | `AdminModelConfigController` | `ModelConfigRequest` |
| `quota` | `AdminTenantQuotaController`、`AdminTenantTokenQuotaController`、`AdminTenantCostQuotaController`、`AdminTenantConcurrentQuotaController`、`AdminQuotaUsageController` | `TenantQuotaRequest` |
| `tenant` | `AdminTenantController`、`AdminTenantUserController` | `TenantRequest`、`TenantUserRequest` |
| `deletion` | `AdminDeletionRequestController` | `DeletionDecisionRequest` |
| `eval` | `AdminEvalDatasetController`、`AdminEvalCaseController`、`AdminEvalRunController` | `EvalDatasetRequest`、`EvalCaseRequest`、`EvalRunRequest`、`EvalScoreRequest` |
| `retention` | `AdminRetentionPolicyController` | `RetentionPolicyRequest`、`RetentionPolicyScopeRequest` |
| `schedule` | `AdminTaskScheduleController` | `TaskScheduleRequest` |
| `safety` | `AdminSafetyInterventionController` | （无独立 dto 则空） |
| `tool` | `AdminToolRegistryController`、`AdminToolPermissionController` | `ToolRegistryRequest`、`ToolPermissionRequest`、`ToolPermissionPriorityRequest`、`ToolPermissionScopeRequest` |
| `health` | `AdminLivenessController`、`AdminReadinessController`、`AdminInfoController` | `AdminInfo` |
| `support` | `AdminExceptionHandler`、`AdminOperationAuditAdvice` | （无） |

## 验收要点（第一块）

- 管理进程 HTTP 路径与响应 JSON 与拆包前一致  
- 类仍带 `Admin` 前缀；审批从原 Audit 控制器拆出后行为不变  
- `mvn test`（JDK 21）相关模块通过；不连真实中间件  
- 只改 admin 门面与引用方 import / README，不改 admin `application`

## 状态

grill 已结束（含 ⑪ 二次收口）。规范、顺序与 admin / ⑪a～⑪c 域表定稿。  
①～⑩、⑪a～⑪c 已收口。本轮 packaging ⑪ 热点分包完成。若改规范，先改本文再动代码。
