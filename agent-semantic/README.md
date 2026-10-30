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
| `ToolCatalogService` | 当前启用的工具，不含停用项 |

### `config`

`AllowByDefaultPermissionService`：还没有产品规则时一律 `ALLOW`。容器里只能有一个 `PermissionRuleService`，产品域接上规则后替换这个类。

### `application` 与 `infrastructure`

现在只有 `package-info`，用来占住边界。运行层的架构测试禁止依赖这两个包。业务工具、Nacos 监听和 Mapper 以后放 `infrastructure`，不要放进 `agent-runtime`。

## 还没有

`ProductSemanticProvider` 的实现、Nacos 提示词、`@Tool` 业务工具、按租户和角色匹配的权限规则。提示词的自然语言可以以后外置；工具名、JSON 结构和安全护栏留在代码里。
