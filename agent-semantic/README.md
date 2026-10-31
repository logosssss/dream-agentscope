# agent-semantic

产品语义。工具目录、权限决定和提示词契约放在这里。运行层只许依赖 `contract` 包，接入层可以依赖 `api`。

坐标：`com.zhu:agent-semantic`。包名：`com.zhu.semantic`。

## 依赖

依赖 `agent-common`。默认权限实现用到 Spring Boot 的条件装配。

不能依赖 `agent-runtime`、`agent-access`。否则产品语义会被执行流程和 HTTP 细节污染。

## 包

### `contract`：给运行层的缝

| 类型 | 作用 |
| --- | --- |
| `ProductSemanticProvider` | 运行层能看见的产品语义：Agent 标识、工具、停止条件、提示词兜底 |
| `ToolDescriptor` | 一条工具描述。运行层不认识具体业务工具类 |
| `CompletionCriteria` | 何时停止，目前只有最大推理轮数 |
| `PermissionDecision` | `ALLOW` / `ASK` / `DENY` |
| `PermissionRuleService` | 按工具、租户、角色给出决定。运行层审批 Hook 用这个契约 |

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

### `application`

`PromptAppService` 实现 `PromptService`。外部模板用 `{{变量}}` 替换。来源缺失、空白、抛错，或替换后仍有占位符时返回 fallback，并打一条只含 key 的警告。不把 `{{name}}` 原文交给模型。fallback 为空则拒绝。容器里只能有一个 `PromptService`。

### `infrastructure.nacos`

`PromptSource` 是外部原文的缝。`AbsentPromptSource` 在没有 Nacos 地址时始终返回 null，不访问网络。配上服务端地址后再换成监听器，不要同时注册两个来源。当前不引入 Nacos 客户端，避免写死 namespace 和 server 地址。

运行层的架构测试禁止依赖 `application` 和 `infrastructure`。业务工具和 Mapper 以后放 `infrastructure`，不要放进 `agent-runtime`。

`PlatformSemanticProvider` 是 `ProductSemanticProvider` 的默认实现。Agent 标识和最大轮数读 `agentscope.runtime` 的同名配置，无效时分别用 `product-agent` 和 `20`。提示词兜底是 `.`。`NoToolCatalog` 目前不返回工具。

## 还没有

真正的 Nacos 监听、`@Tool` 业务工具，以及把目录里的工具注册进运行层。`conditions` 还不能解释。提示词的自然语言可以以后外置；工具名、JSON 结构和安全护栏留在代码里。
