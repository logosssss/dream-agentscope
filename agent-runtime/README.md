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

AgentExecutor.stream(AgentRequest)
        │  同样先校验并构造 RuntimeContext
        ▼
agent.streamEvents(userText, context)
        │  取消、完成或失败时 close
        ▼
AgentStreamEvent   接入层再写成 SSE
```

容器里只有 `AgentExecutionService` 和 `HarnessAgentFactory`。没有 `HarnessAgent` 这个 Bean。Builder 是可变的，所以不在多线程之间共享同一个 Builder 再改提示词。

正式启动会挂上 `DistributedStore`，并把工作区设成 `RemoteFilesystemSpec`，隔离粒度固定 `SESSION`。文件进 MySQL 的 `agentscope_store`，不落在本机盘。开关关闭时没有这个 Bean，只留给测试。

停机时 `AgentShutdown` 让进程不再接新的 Agent 调用，并按 `timeout-seconds` 等待已经开始的调用。Web 容器使用 `server.shutdown=graceful`。容器的终止宽限不要短于这个等待时间。

## 包

| 包 | 类型 | 作用 |
| --- | --- | --- |
| `api` | `AgentExecutor` | 接入层唯一入口 |
| `api` | `AgentRequest` | 执行入参。产品语义由调用方填好 |
| `api` | `AgentResponse` | 运行标识和文本，不泄漏框架的 `Msg` |
| `api` | `AgentStreamEvent` | 流式事件的类型和短文本 |
| `application` | `AgentExecutionService` | 同步 `block` 和流式 `streamEvents`，结束时关闭 Agent |
| `application` | `AgentStreamEvents` | 框架事件收成 `AgentStreamEvent` |
| `infrastructure.agent` | `HarnessAgentFactory`、`WorkspaceFilesystem`、`AgentShutdown` | 按请求组装 Agent。有状态库时工作区按对话隔离。停机时等待在途调用 |
| `config` | `AgentRuntimeProperties` | 前缀 `agentscope.runtime` |
| `config` | `RuntimeConfig` | 只打开配置绑定，不创建 Agent |
| `domain` | `CallIdentity` | 从这次 `RuntimeContext` 读租户、用户、会话，不用 ThreadLocal |
| `domain.middleware` | `AuditMiddleware`、`ApprovalMiddleware`、`FileWriteLimitMiddleware` | 执行策略。ASK 时发布审批事件。写文件超过 `max-write-bytes` 时丢掉，不调用接入层。不用已标删除的 Hook |
| `task` | `AgentTask`、`AgentTaskPublisher` | 任务消息体和发布口。业务层调用，工厂和 Middleware 不感知 |
| `task` | `SyncTaskPublisher`、`SyncAgentTaskHandler` | `mode=sync` 时当前线程执行，只记审计，不调用模型 |
| `task` | `RocketMqTaskPublisher` | `mode=mq` 时的占位。启动即失败，topic 预留为 `agentscope-tasks`，不引入 RocketMQ 依赖 |

## 配置

| 项 | 默认 | 说明 |
| --- | --- | --- |
| `agentscope.runtime.model` | `dashscope:qwen-plus`，可用 `AGENTSCOPE_MODEL` 覆盖 | 模型坐标。密钥用环境变量，不写进仓库 |
| `agentscope.runtime.agent-id` | `product-agent` | 状态命名空间。请求里的 `agentId` 非空时以请求为准 |
| `agentscope.runtime.name` | `product-agent` | 展示名 |
| `agentscope.runtime.workspace` | 空 | 空则用框架默认工作区 |
| `agentscope.runtime.max-iters` | `20` | 单次最大推理轮数 |
| `agentscope.runtime.max-write-bytes` | `5242880` | 单次写文件内容上限，5MB。小于 1 时仍按 5MB |
| `agentscope.runtime.timeout-seconds` | `120` | 同步 `block` 和流式 `timeout` 的上限 |
| `agentscope.task.mode` | `sync` | 当前线程执行任务。`mq` 会拒绝启动 |

系统提示词来自 `AgentRequest.systemPrompt`。为空时工厂放一个占位符 `.`，避免空提示词进入模型。真正的产品文案由产品域传入。

`userId`、`sessionId`、`userText` 缺任何一个，抛 `BusinessException(BAD_REQUEST)`。

一次调用结束且回复带 token 用量时，`AuditUsageMeter` 写一条动作名为 `model.usage` 的审计。明细只有模型名、输入 token、输出 token 和耗时，不放提示词。没有用量就不记。审计写失败不改变已经生成的回复。还没有独立计费表，也没有接 OpenTelemetry。

切到 `agentscope.task.mode=mq` 之前要先做完这三步，否则 `RocketMqTaskPublisher` 会让进程起不来：

1. 在 `agent-runtime` 引入 `rocketmq-spring-boot-starter`。现在不要加。
2. 配置 `rocketmq.name-server`，地址只来自环境变量。不要把地址写进仓库。
3. 增加消费者，监听 topic `agentscope-tasks`，收到消息后调用现有的 `AgentTaskHandler.handle`。消息 key 用 `taskId`。不要再注册第二个 `AgentTaskHandler`。

## 还没有

按产品预构建多份 Builder、工具注册。RocketMQ 客户端和消费者还没接。`stream` 返回 `Flux<AgentStreamEvent>`，SSE 控制器放在 `agent-access`，不要在本模块写控制器。产品任务替换 `SyncAgentTaskHandler`，不要再注册第二个 `AgentTaskHandler`。

`ApprovalMiddleware` 在 `ASK` 时把本次工具标成 `ASKING` 并发出事件，在 `DENY` 时把这次工具从后续执行里拿掉。它不改框架的 `PermissionEngine`：默认权限上下文是 trivial，临时加规则要么这次判定读不到，要么会让同一次调用里没匹配到的工具也变成询问。框架自 2.0.0 起把 `Hook` 标成删除，执行策略挂在 `Middleware` 上。
