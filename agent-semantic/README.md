# agent-semantic

产品语义。工具目录、权限决定和提示词契约放在这里。运行层只许依赖 `contract` 包，接入层可以依赖 `api`。

坐标：`com.zhu:agent-semantic`。包名：`com.zhu.semantic`。

## 依赖

依赖 `agent-common` 和 `agentscope-extensions-nacos-prompt`。默认权限实现用到 Spring Boot 的条件装配。管理进程不依赖本模块，所以不带这个客户端。

不能依赖 `agent-runtime`、`agent-access`。否则产品语义会被执行流程和 HTTP 细节污染。

## 包

### `contract`：给运行层的缝

| 类型 | 作用 |
| --- | --- |
| `ProductSemanticProvider` | 运行层能看见的产品语义：Agent 标识、工具、停止条件、提示词兜底 |
| `ToolDescriptor` | 一条工具描述。运行层用它决定哪些实现可以注册 |
| `ProductTool` | 业务工具实例。`toolId` 必须和 `@Tool` 的 name、目录标识一致 |
| `CompletionCriteria` | 何时停止，目前只有最大推理轮数 |
| `PermissionDecision` | `ALLOW` / `ASK` / `DENY` |
| `PermissionRuleService` | 按工具、租户、角色给出决定。运行层审批 Hook 用这个契约 |
| `PersonaContract` | 锁定人格。改核心身份必须升 `agentId` |
| `RiskLevel` | 安全门档位。`CRITICAL` 换危机话术 |
| `CrisisPrompt` | 危机话术。只从 classpath 读 |

`agentId()` 是状态命名空间，不是展示名。改展示名不能改这个值，否则旧会话会对到空状态。

### `api`：给接入层的缝

| 类型 | 作用 |
| --- | --- |
| `PromptService` | 按 key 取提示词。外部配置失败时必须退回调用方传入的 fallback，不能返回空串 |
| `PlatformPrompt` | 请求没带系统提示词时的键和兜底。产品话术不放这里 |
| `ToolCatalogService` | 当前启用的工具，不含停用项 |

### `config`

`ConfiguredPermissionService`：MySQL 关闭时按 `agentscope.permission.rules` 匹配。没有命中时一律 `ALLOW`。

`StoredPermissionService`：MySQL 打开时改为读 `tool_permission`。配置里的规则不再参与。表由用户进程的 Flyway `V4__tool_permission.sql` 创建。`conditions` 非空的行先跳过。命中的 `decision` 写错时这次调用失败，不当成允许。容器里两种实现不会同时存在。

`NoToolCatalog`：MySQL 关闭时目录为空。

`StoredToolCatalog`：MySQL 打开时读 `tool_registry` 的启用行，表由 `V6__tool_registry.sql` 创建。空白标识跳过。表是空的则目录为空。参数结构只保存在表里，不参与注册。没有对应 `ProductTool` 时运行层仍不把名字交给模型。

### `application`

`PromptAppService` 实现 `PromptService`。外部模板用 `{{变量}}` 替换。来源缺失、空白、抛错，或替换后仍有占位符时返回 fallback，并打一条只含 key 的警告。不把 `{{name}}` 原文交给模型。fallback 为空则拒绝。容器里只能有一个 `PromptService`。

### `infrastructure.nacos`

`PromptSource` 是外部原文的缝。`agentscope.nacos.server-addr` 为空时用 `AbsentPromptSource`，始终返回 null，不访问网络。配了地址才注册 `NacosPromptSource`，两者不要同时存在。监听器建不起来或拉取失败时，`PromptService` 仍用 fallback。不做动态配置、服务发现和 MCP。地址、命名空间和账号只来自环境变量。

`src/main/resources/prompts` 里的 `.md` 和 `.json` 由 `PromptTemplateCheck` 在测试中校验。键必须是 `prompt.{agent}.{purpose}`，占位符必须在 `PlatformPrompt` 里登记。`.json` 把 `{{变量}}` 换成 `sample` 后必须能解析。`PromptRelease` 在校验通过后才把原文交给发布端。`prompt.safety.crisis` 会校验，但不发布。`NacosPromptPublisher` 向管理接口新建一个版本并发布，进程启动时不调用它。地址没配或对方拒绝时这次发布失败，不影响已经在跑的监听。

运行层的架构测试禁止依赖 `application` 和 `infrastructure`。业务工具和 Mapper 以后放 `infrastructure`，不要放进 `agent-runtime`。

`PlatformSemanticProvider` 是 `ProductSemanticProvider` 的默认实现。Agent 标识和最大轮数读 `agentscope.runtime` 的同名配置，无效时分别用 `companion-v1` 和 `20`。`PersonaContract` 由 `CompanionPersona` 提供锁定身份。主对话键是 `prompt.companion.system`。危机话术键是 `prompt.safety.crisis`，由 `ClasspathCrisisPrompt` 从 classpath 读取，不走 Nacos。改核心身份必须升 `agentId`，不要把 userId 拼进去。MySQL 关闭时目录为空；打开时目录来自 `tool_registry`。

## 还没有

`@Tool` 业务工具。目录里出现且有对应实现的工具，由运行层注册进这次 Agent 的 Toolkit。`conditions` 还不能解释。`param_schema` 只保存。动态配置、服务发现和 MCP 不走这次的提示词客户端。提示词发布不在进程启动时发生。主对话话术在 `prompts/prompt.companion.system.md`。危机话术在 `prompts/prompt.safety.crisis.md`，只从 classpath 读。语义判定和干预落库还没有。工具名、JSON 结构和安全护栏留在代码里。
