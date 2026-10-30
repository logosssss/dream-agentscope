# agent-runtime

执行内核。每次请求创建一次 `HarnessAgent`，执行完就关闭。本模块不认识具体产品，也不提供 HTTP 接口。

坐标：`com.zhu:agent-runtime`。包名：`com.zhu.runtime`。

## 依赖

依赖 `agent-common`、`agent-semantic`、`agent-state`、`agent-audit`、`agentscope-harness`、`agentscope-extensions-model-dashscope`。

对 `agent-semantic` 的 Java 引用只能落在 `com.zhu.semantic.contract`。不要 import `semantic.application` 或 `semantic.infrastructure`。

不能依赖 `agent-access`。审批要通知前端时，发布 `agent-audit` 里的事件，由接入层订阅。

## 调用链

```text
AgentExecutor.execute(AgentRequest)
        │
        ▼
AgentExecutionService
        │  校验 userId、sessionId、userText
        │  构造 RuntimeContext
        ▼
HarnessAgentFactory.build(request)   每次 new Builder，然后 build()
        │
        ▼
agent.call(userText, context).block(timeout)
        │
        finally agent.close()
```

容器里只有 `AgentExecutionService` 和 `HarnessAgentFactory`。没有 `HarnessAgent` 这个 Bean。Builder 是可变的，所以不在多线程之间共享同一个 Builder 再改提示词。

`DistributedStore` 存在时挂到这次构建上。MySQL 开关关闭时没有这个 Bean，Agent 仍能构建，状态只留在本进程。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api` | `AgentExecutor` | 接入层唯一入口 |
| `api` | `AgentRequest` | 执行入参。产品语义由调用方填好 |
| `api` | `AgentResponse` | 运行标识和文本，不泄漏框架的 `Msg` |
| `application` | `AgentExecutionService` | 上面的生命周期 |
| `infrastructure.agent` | `HarnessAgentFactory` | 按请求组装 Agent |
| `config` | `AgentRuntimeProperties` | 前缀 `agentscope.runtime` |
| `config` | `RuntimeConfig` | 只打开配置绑定，不创建 Agent |
| `domain` | `CallIdentity` | 从这次 `RuntimeContext` 读租户、用户、会话，不用 ThreadLocal |
| `domain.middleware` | `AuditMiddleware`、`ApprovalMiddleware` | 执行策略。ASK 时发布审批事件，不调用接入层。不用已标删除的 Hook |
| `task` | `AgentTask`、`AgentTaskPublisher` | 异步任务的形状。还没有同步实现，也没有 RocketMQ |

## 配置

| 项 | 默认 | 说明 |
| --- | --- | --- |
| `agentscope.runtime.model` | `dashscope:qwen-plus`，可用 `AGENTSCOPE_MODEL` 覆盖 | 模型坐标。密钥用环境变量，不写进仓库 |
| `agentscope.runtime.agent-id` | `product-agent` | 状态命名空间。请求里的 `agentId` 非空时以请求为准 |
| `agentscope.runtime.name` | `product-agent` | 展示名 |
| `agentscope.runtime.workspace` | 空 | 空则用框架默认工作区 |
| `agentscope.runtime.max-iters` | `20` | 单次最大推理轮数 |
| `agentscope.runtime.timeout-seconds` | `120` | `block` 的等待上限 |

系统提示词来自 `AgentRequest.systemPrompt`。为空时工厂放一个占位符 `.`，避免空提示词进入模型。真正的产品文案由产品域传入。

`userId`、`sessionId`、`userText` 缺任何一个，抛 `BusinessException(BAD_REQUEST)`。

## 还没有

流式 `Flux`、按产品预构建多份 Builder、工具注册、`AgentTaskPublisher` 的同步或 MQ 实现。SSE 放在 `agent-access`，不要在本模块写控制器。

`ApprovalMiddleware` 在 `ASK` 时把本次工具标成 `ASKING` 并发出事件。它不改框架的 `PermissionEngine`：默认权限上下文是 trivial，临时加规则要么这次判定读不到，要么会让同一次调用里没匹配到的工具也变成询问。框架自 2.0.0 起把 `Hook` 标成删除，执行策略挂在 `Middleware` 上。
