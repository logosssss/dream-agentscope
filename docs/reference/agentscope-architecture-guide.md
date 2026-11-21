# <font style="color:rgb(15, 17, 21);">AgentScope Java 2.0.4 产品级架构与工程落地指南</font>
<font style="color:rgb(15, 17, 21);">基准版本：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">2.0.4</font>`<font style="color:rgb(15, 17, 21);">（2026-10-08 发布）。JDK 17+，推荐 Maven 3.9+。</font><font style="color:rgb(15, 17, 21);">  
</font><font style="color:rgb(15, 17, 21);">文档定位：工程落地蓝图，面向架构师和后端负责人，不是 API 参考。</font><font style="color:rgb(15, 17, 21);">  
</font><font style="color:rgb(15, 17, 21);">核心主张：</font>**<font style="color:rgb(15, 17, 21);">用 HarnessAgent 做执行内核，Spring Boot 只做接入与工程化，产品语义归产品域，状态归 DistributedStore。</font>**

## <font style="color:rgb(15, 17, 21);">一、先明确一件事：HarnessAgent 是“引擎”，不是“会话单例”</font>
<font style="color:rgb(15, 17, 21);">这是整个产品级设计的地基，必须先纠正一个常见误解。</font>

`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">HarnessAgent</font>**_`_**<font style="color:#DF2A3F;"> 是一个无状态的执行引擎，它不持有任何“当前会话”的可变状态。所有 per-call 状态（</font>**_`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">AgentState</font>**_`_**<font style="color:#DF2A3F;">、</font>**_`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">PermissionEngine</font>**_`_**<font style="color:#DF2A3F;">、事件 sink）都封装在单次执行内部，通过显式传入的 </font>**_`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">RuntimeContext</font>**_`_**<font style="color:#DF2A3F;"> 区分用户和会话。</font>**_

<font style="color:rgb(15, 17, 21);">官方 Quickstart 给出的标准用法是：</font>

_**<font style="color:#DF2A3F;">在应用启动时定义共享 </font>**_`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">Builder</font>**_`_**<font style="color:#DF2A3F;">，每次请求用 </font>**_`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">builder.build()</font>**_`_**<font style="color:#DF2A3F;"> 创建新的 Agent，执行完成后关闭。Builder 保存通用配置，请求身份放在独立的 </font>**_`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">RuntimeContext</font>**_`_**<font style="color:#DF2A3F;"> 中。</font>**_

<font style="color:rgb(15, 17, 21);">这意味着：</font>

+ **<font style="color:rgb(15, 17, 21);">不需要在 Spring 容器里持有一个全局 Agent 单例</font>**<font style="color:rgb(15, 17, 21);">；</font>
+ **<font style="color:rgb(15, 17, 21);">不需要为并发用户做 Agent 级别的加锁</font>**<font style="color:rgb(15, 17, 21);">；</font>
+ **<font style="color:rgb(15, 17, 21);">同一份 Builder 配置可以服务任意多个 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">(userId, sessionId)</font>**`<font style="color:rgb(15, 17, 21);">；</font>
+ <font style="color:rgb(15, 17, 21);">Agent 实例是轻量的，关闭是幂等的，每请求 build/close 是官方推荐路径。</font>

<font style="color:rgb(15, 17, 21);">产品级架构的起点就是：</font>**<font style="color:rgb(15, 17, 21);">把 Builder 当作不可变配置持有，把 Agent 实例当作请求级资源。</font>**

## <font style="color:rgb(15, 17, 21);">二、目标架构：四层 + 一个内核</font>
<font style="color:rgb(15, 17, 21);">text</font>

```plain
┌──────────────────────────────────────────────────────────┐
│  接入层（gateway / product-controller）                     │
│  Spring MVC / WebFlux、SSE、Channel、登录、租户、额度        │
│  认证后构造 RuntimeContext → 交给运行层                     │
└──────────────────────┬───────────────────────────────────┘
                       │ AgentRequest(spec, runtimeContext, input)
┌──────────────────────▼───────────────────────────────────┐
│  运行层（agent-runtime）                                    │
│  AgentFactory 持有 Builder；AgentRun 管理单次执行生命周期   │
│  不持有产品语义，不持有会话状态                              │
└──────────────────────┬───────────────────────────────────┘
                       │ 请求能力
┌──────────────────────▼───────────────────────────────────┐
│  能力层（capability/*）                                     │
│  model · vision · image · retrieve · mcp · workspace      │
│  memory · guardrail · billing · observe                   │
│  各自独立、可单独依赖、可单独测试                             │
└──────────────────────┬───────────────────────────────────┘
                       │ 读写
┌──────────────────────▼───────────────────────────────────┐
│  基础设施层（infra/*）                                      │
│  DistributedStore（StateStore / BaseStore / Snapshot /    │
│  ExecutionGuard）· 向量库 · 对象存储 · MySQL · Redis       │
└──────────────────────────────────────────────────────────┘
```

### <font style="color:rgb(15, 17, 21);">各层职责边界</font>
| <font style="color:rgb(15, 17, 21);">层</font> | <font style="color:rgb(15, 17, 21);">做什么</font> | <font style="color:rgb(15, 17, 21);">不做什么</font> |
| --- | --- | --- |
| **<font style="color:rgb(15, 17, 21);">接入层</font>** | <font style="color:rgb(15, 17, 21);">认证、租户识别、额度扣减、SSE 编码、Channel 适配、构造 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext</font>` | <font style="color:rgb(15, 17, 21);">不碰 AgentScope 类型</font> |
| **<font style="color:rgb(15, 17, 21);">运行层</font>** | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentFactory</font>`<br/><font style="color:rgb(15, 17, 21);"> 管理 Builder、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRun</font>`<br/><font style="color:rgb(15, 17, 21);"> 管理单次执行、事件转发、trace 收口</font> | <font style="color:rgb(15, 17, 21);">不认识任何产品名、不持有会话状态</font> |
| **<font style="color:rgb(15, 17, 21);">能力层</font>** | <font style="color:rgb(15, 17, 21);">模型路由、视觉、生图、检索、MCP、工作区、记忆、护栏、计费、观测</font> | <font style="color:rgb(15, 17, 21);">不依赖产品域、不依赖接入层</font> |
| **<font style="color:rgb(15, 17, 21);">基础设施层</font>** | <font style="color:rgb(15, 17, 21);">DistributedStore、向量库、对象存储、MySQL、Redis</font> | <font style="color:rgb(15, 17, 21);">不写业务逻辑</font> |


**<font style="color:rgb(15, 17, 21);">关键约束</font>**<font style="color:rgb(15, 17, 21);">：运行层</font>**<font style="color:rgb(15, 17, 21);">不持有会话状态</font>**<font style="color:rgb(15, 17, 21);">。所有状态通过 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext</font>`<font style="color:rgb(15, 17, 21);"> 传入，由框架的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<font style="color:rgb(15, 17, 21);"> 负责持久化和跨副本恢复。这是多副本水平扩展的前提。</font>

## <font style="color:rgb(15, 17, 21);">三、Maven 工程结构</font>
<font style="color:rgb(15, 17, 21);">text</font>

```plain
dream-agentscope
├── app/
│   ├── product-app             可运行产品进程
│   └── admin-app               管理进程（可选）
│
├── gateway/                    接入层
│   ├── auth                    登录、令牌
│   ├── tenant                  租户识别、RuntimeContext 构造
│   ├── quota                   额度（Redis / 本地可切换）
│   └── web                     Spring MVC / WebFlux、SSE
│
├── runtime/                    运行层
│   ├── agent-factory           HarnessAgent.Builder 持有与 build
│   ├── agent-run               单次执行生命周期（AgentRun / RunControl）
│   └── call-observer           CallObserver / UsageMeter 接口定义
│
├── capability/                 能力层
│   ├── model                   模型路由（ModelRegistry）
│   ├── vision                  看图
│   ├── image                   生图
│   ├── retrieve                检索（向量 + 语料）
│   ├── mcp                     MCP 装配
│   ├── workspace               工作区路径 + MEMORY.md（如自管）
│   ├── memory                  长期记忆配置
│   ├── guardrail               进/出模型检查
│   ├── billing                 UsageMeter 实现
│   └── observe                 CallObserver 实现（OTel）
│
├── product/                    产品域（只声明 AgentSpec，不碰 AgentScope）
│   ├── review
│   ├── companion
│   ├── knowledge
│   └── content
│
└── infra/                      基础设施
    ├── distributed-store       DistributedStore 配置
    ├── vector                  向量库客户端
    ├── object-store            对象存储
    └── common                  公共工具（UserDirectory 等）
```

### <font style="color:rgb(15, 17, 21);">与“把业务全塞 base/model”的旧结构的关键区别</font>
| <font style="color:rgb(15, 17, 21);">维度</font> | <font style="color:rgb(15, 17, 21);">旧结构</font> | <font style="color:rgb(15, 17, 21);">新结构</font> |
| --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">模型调用入口</font> | <font style="color:rgb(15, 17, 21);">一个 950 行的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ModelCall</font>`<br/><font style="color:rgb(15, 17, 21);"> 包揽生图/视觉/装配/序列化</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<br/><font style="color:rgb(15, 17, 21);"> 薄层只做生命周期；生图/视觉/装配各自独立</font> |
| <font style="color:rgb(15, 17, 21);">产品语义</font> | <font style="color:rgb(15, 17, 21);">base 持有 15 个 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentProfile</font>`<br/><font style="color:rgb(15, 17, 21);"> 常量 + 字符串判断</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentSpec</font>`<br/><font style="color:rgb(15, 17, 21);"> 由产品域构造，runtime 不持有任何产品常量</font> |
| <font style="color:rgb(15, 17, 21);">会话状态</font> | <font style="color:rgb(15, 17, 21);">本地 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">state/*.json</font>`<br/><font style="color:rgb(15, 17, 21);"> 文件</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<br/><font style="color:rgb(15, 17, 21);"> 按 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">(userId, sessionId)</font>`<br/><font style="color:rgb(15, 17, 21);"> 持久化</font> |
| <font style="color:rgb(15, 17, 21);">观测/计费</font> | <font style="color:rgb(15, 17, 21);">base 反向依赖 chain</font> | <font style="color:rgb(15, 17, 21);">接口定义在 runtime，实现在 capability，boot 装配</font> |
| <font style="color:rgb(15, 17, 21);">多租户隔离</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">visible()</font>`<br/><font style="color:rgb(15, 17, 21);"> 等补偿函数</font> | <font style="color:rgb(15, 17, 21);">写入强制带 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">userId/tenantId</font>`<br/><font style="color:rgb(15, 17, 21);">，检索强制过滤</font> |


## <font style="color:rgb(15, 17, 21);">四、产品语义：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentSpec</font>`<font style="color:rgb(15, 17, 21);"> 由产品域持有，runtime 不认识产品</font>
<font style="color:rgb(15, 17, 21);">这是从旧工程必须纠正的核心问题。</font>

### <font style="color:rgb(15, 17, 21);">4.1 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentSpec</font>`<font style="color:rgb(15, 17, 21);"> 的形状</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
// runtime/agent-factory 里只定义形状，不含任何产品常量
public record AgentSpec(
    String id,                          // 应用编号
    boolean retrieve,                   // 要不要应用层检索
    boolean tools,                      // 要不要挂工具
    boolean jsonSchema,                 // 要不要结构化输出
    boolean disableFilesystem,          // 要不要关文件系统工具
    boolean planMode,                   // 要不要 plan/task
    boolean connectMcp,                 // 要不要连 MCP
    Set<String> allowedTools,           // 工具白名单
    IsolationScope isolationScope       // 隔离粒度
) {}
```

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">IsolationScope</font>`<font style="color:rgb(15, 17, 21);"> 使用 Harness 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SESSION / USER / AGENT / GLOBAL</font>`<font style="color:rgb(15, 17, 21);"> 枚举，显式声明隔离粒度，而不是靠运行时推断</font><font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">4.2 产品域声明</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
// product/review
public final class ReviewSpec {
    public static final AgentSpec SPEC = new AgentSpec(
        "review", false, false, true, false, false, false,
        Set.of(), IsolationScope.SESSION);
}

// product/companion
public final class CompanionSpec {
    public static final AgentSpec SPEC = new AgentSpec(
        "companion", true, true, false, true, false, true,
        Set.of("recall", "retrieve"), IsolationScope.USER);
}
```

**<font style="color:rgb(15, 17, 21);">收益</font>**<font style="color:rgb(15, 17, 21);">：</font>

+ <font style="color:rgb(15, 17, 21);">runtime 不需要任何 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">"review".equals(...)</font>`<font style="color:rgb(15, 17, 21);"> 字符串判断；</font>
+ <font style="color:rgb(15, 17, 21);">新增产品</font>**<font style="color:rgb(15, 17, 21);">不需要改 runtime 或 capability</font>**<font style="color:rgb(15, 17, 21);">；</font>
+ <font style="color:rgb(15, 17, 21);">漏填字段编译不通过，而不是运行时行为异常。</font>

### <font style="color:rgb(15, 17, 21);">4.3 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRequest</font>`<font style="color:rgb(15, 17, 21);"> 与检索问句</font>
<font style="color:rgb(15, 17, 21);">检索触发条件由产品域决定，不由 runtime 分析文本：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
public record AgentRequest(
    AgentSpec spec,
    RuntimeContext runtimeContext,
    String systemPrompt,
    String userText,
    String retrieveAgent,      // null = 不检索；非空 = 从该应用知识检索
    String retrieveQuery       // null = 用 userText；非空 = 显式问句
) {}
```

**<font style="color:rgb(15, 17, 21);">关键</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">retrieveQuery</font>`<font style="color:rgb(15, 17, 21);"> 由产品域构造 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRequest</font>`<font style="color:rgb(15, 17, 21);"> 时传入，runtime 只消费。这样“搭子问知识库”这种跨应用检索可以显式表达为 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">retrieveAgent = "knowledge"</font>`<font style="color:rgb(15, 17, 21);">，而不需要 runtime 去猜。</font>

## <font style="color:rgb(15, 17, 21);">五、运行层：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentFactory</font>`<font style="color:rgb(15, 17, 21);"> + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRun</font>`
### <font style="color:rgb(15, 17, 21);">5.1 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentFactory</font>`<font style="color:rgb(15, 17, 21);">：持有 Builder，每请求 build</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
@Component
public class AgentFactory {

    private final HarnessAgent.Builder builder;

    public AgentFactory(ModelProperties props, InfraConfig infra,
                        MemoryConfig memory, CompactionConfig compaction,
                        PermissionContextState permission) {
        this.builder = HarnessAgent.builder()
        .name("product-agent")                    // 固定，工作区按它分桶
        .model(props.modelString())               // "dashscope:qwen-plus"
        .workspace(infra.workspacePath())          // 本地或远端
        .stateStore(infra.stateStore())            // DistributedStore 注入
        .filesystem(infra.filesystemSpec())         // 本地 / Remote / Docker
        .memory(memory)
        .compaction(compaction)
        .permissionContext(permission)
        .middleware(new OtelTracingMiddleware());
    }

    public HarnessAgent build(AgentSpec spec) {
        // 按 spec 做可选装配，但 Builder 是共享的，不在这里 new Builder
        // 注意：HarnessAgent.Builder 不可变，build() 产出独立实例
        return builder.build();
    }
}
```

<font style="color:rgb(15, 17, 21);">注意：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent.Builder</font>`<font style="color:rgb(15, 17, 21);"> 是可变配置对象。如果 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentSpec</font>`<font style="color:rgb(15, 17, 21);"> 的差异需要不同 Builder 配置（比如 review 要 JSON Schema、content 不连 MCP），正确做法是</font>**<font style="color:rgb(15, 17, 21);">每种 spec 预构建一个 Builder</font>**<font style="color:rgb(15, 17, 21);">，而不是在请求线程里改共享 Builder。</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
@Component
public class AgentFactory {

    private final Map<String, HarnessAgent.Builder> builders = new ConcurrentHashMap<>();

    public HarnessAgent build(AgentSpec spec) {
        return builders.computeIfAbsent(spec.id(), id -> configuredBuilder(spec)).build();
    }
}
```

### <font style="color:rgb(15, 17, 21);">5.2 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRun</font>`<font style="color:rgb(15, 17, 21);">：单次执行的生命周期</font>
<font style="color:rgb(15, 17, 21);">2.0.4 新增了 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRun / RunControl</font>`<font style="color:rgb(15, 17, 21);"> 单次执行控制</font><font style="color:rgb(15, 17, 21);">。运行层用它来管理：</font>

+ <font style="color:rgb(15, 17, 21);">取消正在运行的执行；</font>
+ <font style="color:rgb(15, 17, 21);">隔离并发调用的上下文；</font>
+ <font style="color:rgb(15, 17, 21);">收口 trace 和计费；</font>
+ <font style="color:rgb(15, 17, 21);">确保 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AutoCloseable</font>`<font style="color:rgb(15, 17, 21);"> 在所有终态关闭。</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
public class AgentRun implements AutoCloseable {

    private final HarnessAgent agent;
    private final RunControl control;
    private final CallObserver observer;
    private final UsageMeter meter;
    private final String traceId;
    private final AtomicBoolean closed = new AtomicBoolean();

    public Flux<AgentEvent> stream(AgentRequest req, ModelSink sink) {
        observer.onStart(traceId, req.spec().id());
        return agent.streamEvents(new UserMessage(req.userText()), req.runtimeContext())
        .doOnNext(sink::onEvent)
        .doOnComplete(() -> observer.onFinish(traceId, "ok"))
        .doOnError(e -> observer.onFinish(traceId, "error"));
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            control.cancel();
            agent.close();
        }
    }
}
```

### <font style="color:rgb(15, 17, 21);">5.3 为什么不纵向切 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">stream</font>`<font style="color:rgb(15, 17, 21);"> 链</font>
`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">stream</font>`<font style="color:rgb(15, 17, 21);"> 是一条</font>**<font style="color:rgb(15, 17, 21);">事务性生命周期</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">observer.onStart</font>`<font style="color:rgb(15, 17, 21);"> 之后，任何一步失败都必须 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">onFinish</font>`<font style="color:rgb(15, 17, 21);">；</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AutoCloseable</font>`<font style="color:rgb(15, 17, 21);"> 必须在所有终态关闭；四个出口（missingKey / rejected / finishOk / finishError）的语义必须一致。纵向切开后，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">traceId</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">started</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AtomicBoolean</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent</font>`<font style="color:rgb(15, 17, 21);"> 引用要在 5 个类之间传递，复杂度只会上升。</font>

<font style="color:rgb(15, 17, 21);">正确做法是</font>**<font style="color:rgb(15, 17, 21);">按技术栈横向切</font>**<font style="color:rgb(15, 17, 21);">：运行层只留生命周期，生图、视觉、Harness 装配、事件序列化各自独立。</font>

## <font style="color:rgb(15, 17, 21);">六、能力层：五个独立模块</font>
<font style="color:rgb(15, 17, 21);">旧工程 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRuntime</font>`<font style="color:rgb(15, 17, 21);"> 800 行同时管工作区、向量、MCP、A2A、健康、目录。产品级拆成：</font>

| <font style="color:rgb(15, 17, 21);">模块</font> | <font style="color:rgb(15, 17, 21);">职责</font> | <font style="color:rgb(15, 17, 21);">依赖</font> |
| --- | --- | --- |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/model</font>**` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ModelRegistry</font>`<br/><font style="color:rgb(15, 17, 21);"> 路由、模型配置</font> | <font style="color:rgb(15, 17, 21);">AgentScope model 扩展</font> |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/vision</font>**` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">see</font>`<br/><font style="color:rgb(15, 17, 21);"> + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">joinVision</font>` | <font style="color:rgb(15, 17, 21);">AgentScope core</font> |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/image</font>**` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">paint</font>`<br/><font style="color:rgb(15, 17, 21);"> + 下载 + JPEG 转换</font> | <font style="color:rgb(15, 17, 21);">DashScope SDK + AWT</font> |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/retrieve</font>**` | <font style="color:rgb(15, 17, 21);">Milvus/InMemory 管理、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ingest</font>`<br/><font style="color:rgb(15, 17, 21);">/</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">forget</font>`<br/><font style="color:rgb(15, 17, 21);">/</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">retrieve</font>`<br/><font style="color:rgb(15, 17, 21);">、两个集合</font> | <font style="color:rgb(15, 17, 21);">AgentScope rag + Milvus 客户端</font> |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/mcp</font>**` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ToolsConfig</font>`<br/><font style="color:rgb(15, 17, 21);"> 装配、握手状态</font> | <font style="color:rgb(15, 17, 21);">AgentScope harness</font> |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/workspace</font>**` | <font style="color:rgb(15, 17, 21);">路径计算、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MEMORY.md</font>`<br/><font style="color:rgb(15, 17, 21);"> 读写、会话 JSON、技能手册</font> | <font style="color:rgb(15, 17, 21);">仅 JDK（或 Harness 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">WorkspaceManager</font>`<br/><font style="color:rgb(15, 17, 21);">）</font> |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/memory</font>**` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MemoryConfig</font>`<br/><font style="color:rgb(15, 17, 21);"> 配置、长期记忆策略</font> | <font style="color:rgb(15, 17, 21);">AgentScope harness</font> |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/guardrail</font>**` | <font style="color:rgb(15, 17, 21);">进/出模型检查</font> | <font style="color:rgb(15, 17, 21);">无外部依赖</font> |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/billing</font>**` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">UsageMeter</font>`<br/><font style="color:rgb(15, 17, 21);"> 实现</font> | <font style="color:rgb(15, 17, 21);">无外部依赖</font> |
| `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/observe</font>**` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">CallObserver</font>`<br/><font style="color:rgb(15, 17, 21);"> 实现（OTel Span 桥接）</font> | <font style="color:rgb(15, 17, 21);">OTel SDK</font> |


**<font style="color:rgb(15, 17, 21);">收益</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">product/content</font>`<font style="color:rgb(15, 17, 21);"> 的封面功能只依赖 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability/image</font>`<font style="color:rgb(15, 17, 21);">，classpath 不需要 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent</font>`<font style="color:rgb(15, 17, 21);">、不需要 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRuntime</font>`<font style="color:rgb(15, 17, 21);">、不需要 Milvus 客户端。这是最直接的 classpath 污染治理。</font>

## <font style="color:rgb(15, 17, 21);">七、基础设施层：DistributedStore 一键配置</font>
### <font style="color:rgb(15, 17, 21);">7.1 为什么必须用 DistributedStore</font>
<font style="color:rgb(15, 17, 21);">多副本部署时，以下四个组件必须跨节点共享</font><font style="color:rgb(15, 17, 21);">：</font>

| <font style="color:rgb(15, 17, 21);">组件</font> | <font style="color:rgb(15, 17, 21);">不共享的后果</font> |
| --- | --- |
| **<font style="color:rgb(15, 17, 21);">AgentStateStore</font>** | <font style="color:rgb(15, 17, 21);">请求落到另一台机器就“失忆”；pod 重启后会话丢失</font> |
| **<font style="color:rgb(15, 17, 21);">BaseStore</font>** | <font style="color:rgb(15, 17, 21);">多副本看不到同一份 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MEMORY.md</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">skills/</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">sessions/</font>` |
| **<font style="color:rgb(15, 17, 21);">SandboxSnapshotSpec</font>** | <font style="color:rgb(15, 17, 21);">沙箱容器销毁后工作区无法恢复</font> |
| **<font style="color:rgb(15, 17, 21);">SandboxExecutionGuard</font>** | <font style="color:rgb(15, 17, 21);">同一沙箱 slot 被多个副本并发操作，竞态</font> |


`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<font style="color:rgb(15, 17, 21);"> 一行配置同时覆盖四个组件：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
DistributedStore store = RedisDistributedStore.fromJedis(jedis);
HarnessAgent.builder()
.distributedStore(store)   // 自动注入 stateStore + baseStore + snapshotSpec + executionGuard
.build();
```

### <font style="color:rgb(15, 17, 21);">7.2 生产组件选型</font>
| <font style="color:rgb(15, 17, 21);">组件</font> | <font style="color:rgb(15, 17, 21);">开发默认</font> | <font style="color:rgb(15, 17, 21);">生产推荐</font> | <font style="color:rgb(15, 17, 21);">注意</font> |
| --- | --- | --- | --- |
| **<font style="color:rgb(15, 17, 21);">AgentStateStore</font>** | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">JsonFileAgentStateStore</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RedisAgentStateStore</font>`<br/><font style="color:rgb(15, 17, 21);">（多副本首选）</font> | <font style="color:rgb(15, 17, 21);">Redis / Postgres / MySQL / InMemory 支持 CAS；JSON 文件、OSS、COS 是 last-writer-wins</font> |
| **<font style="color:rgb(15, 17, 21);">Filesystem</font>** | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">LocalFilesystemSpec</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RemoteFilesystemSpec</font>`<br/><font style="color:rgb(15, 17, 21);">（共享 KV）或 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DockerFilesystemSpec</font>`<br/><font style="color:rgb(15, 17, 21);">（沙箱）</font> | <font style="color:rgb(15, 17, 21);">多副本 + 本地文件 = 请求飘走就失忆</font> |
| **<font style="color:rgb(15, 17, 21);">SandboxSnapshot</font>** | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NoopSnapshotSpec</font>`<br/><font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">LocalSnapshotSpec</font>` | <font style="color:rgb(15, 17, 21);">OSS 等远端（大对象走对象存储）</font> | <font style="color:rgb(15, 17, 21);">不要把大快照写 Redis</font> |
| **<font style="color:rgb(15, 17, 21);">ExecutionGuard</font>** | <font style="color:rgb(15, 17, 21);">单进程内</font> | <font style="color:rgb(15, 17, 21);">Redis / JDBC 锁</font> | <font style="color:rgb(15, 17, 21);">OSS 不适合做分布式锁；用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore.builder()</font>`<br/><font style="color:rgb(15, 17, 21);"> 混入 Redis guard</font> |


### <font style="color:rgb(15, 17, 21);">7.3 框架的配置一致性校验</font>
**<font style="color:rgb(15, 17, 21);">装配期直接抛 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">IllegalStateException</font>**`<font style="color:rgb(15, 17, 21);"> 的场景：</font>

+ <font style="color:rgb(15, 17, 21);">分布式文件系统（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RemoteFilesystemSpec</font>`<font style="color:rgb(15, 17, 21);">） + 本地 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">JsonFileAgentStateStore</font>`<font style="color:rgb(15, 17, 21);">；</font>
+ <font style="color:rgb(15, 17, 21);">沙箱 + 本地 state store（构建期 warn，提示不能跨 JVM 恢复）。</font>

<font style="color:rgb(15, 17, 21);">这是框架强制的配置一致性，比“MySQL 连不上就降级到内存”更严格，但也更安全。产品级</font>**<font style="color:rgb(15, 17, 21);">不要用 try-catch 绕过这个校验</font>**<font style="color:rgb(15, 17, 21);">，而是让配置在启动时就失败。</font>

## <font style="color:rgb(15, 17, 21);">八、接入层：Spring Boot 与 SSE</font>
### <font style="color:rgb(15, 17, 21);">8.1 依赖坐标</font>
<font style="color:rgb(15, 17, 21);">最小依赖：</font>

<font style="color:rgb(15, 17, 21);">xml</font>

```xml
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-harness</artifactId>
  <version>2.0.4</version>
</dependency>
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-model-dashscope</artifactId>
  <version>2.0.4</version>
</dependency>
```

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agentscope-harness</font>`<font style="color:rgb(15, 17, 21);"> 自动拉入 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agentscope-core</font>`<font style="color:rgb(15, 17, 21);">。</font>

<font style="color:rgb(15, 17, 21);">Spring Boot Starter（可选，适合快速起步）：</font>

<font style="color:rgb(15, 17, 21);">xml</font>

```xml
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-spring-boot-starter</artifactId>
  <version>2.0.4</version>
</dependency>
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-dashscope-spring-boot-starter</artifactId>
  <version>2.0.4</version>
</dependency>
```

<font style="color:rgb(15, 17, 21);">但产品级建议</font>**<font style="color:rgb(15, 17, 21);">手写 AgentFactory Bean</font>**<font style="color:rgb(15, 17, 21);">，而不是完全依赖 starter 自动装配——因为 starter 默认的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ReActAgent</font>`<font style="color:rgb(15, 17, 21);"> Bean 没有接 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<font style="color:rgb(15, 17, 21);">，重启即失忆。</font>

### <font style="color:rgb(15, 17, 21);">8.2 SSE Controller</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
@RestController
public class ChatController {

    private final AgentFactory factory;
    private final AgentRuntime runtime;   // 运行层

    @GetMapping(value = "/api/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<AgentEvent>> stream(@RequestParam String q,
                                                    @AuthenticationPrincipal User user) {
        AgentSpec spec = CompanionSpec.SPEC;
        RuntimeContext ctx = RuntimeContext.builder()
        .userId(user.id())
        .sessionId(sessionIdOf(user, q))
        .build();

        AgentRun run = runtime.start(spec, ctx);
        return run.stream(new AgentRequest(spec, ctx, systemPrompt, q, null, null))
        .map(e -> ServerSentEvent.<AgentEvent>builder()
             .event(e.getType().name().toLowerCase())
             .data(e)
             .build())
        .doOnTerminate(run::close);
    }
}
```

**<font style="color:rgb(15, 17, 21);">关键点</font>**<font style="color:rgb(15, 17, 21);">：</font>

+ **<font style="color:rgb(15, 17, 21);">绝不 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">.block()</font>**`<font style="color:rgb(15, 17, 21);"> 在 WebFlux 请求线程上；</font>
+ **<font style="color:rgb(15, 17, 21);">每请求 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime.start</font>**`<font style="color:rgb(15, 17, 21);">，返回 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Flux</font>`<font style="color:rgb(15, 17, 21);">，由框架订阅；</font>
+ `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">doOnTerminate</font>**`**<font style="color:rgb(15, 17, 21);"> 关闭 AgentRun</font>**<font style="color:rgb(15, 17, 21);">，确保资源释放；</font>
+ <font style="color:rgb(15, 17, 21);">SSE event 名用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentEventType</font>`<font style="color:rgb(15, 17, 21);"> 小写，前端按 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">event</font>`<font style="color:rgb(15, 17, 21);"> 字段区分 delta / tool_call / end。</font>

### <font style="color:rgb(15, 17, 21);">8.3 Channel 模式（多 Agent 路由）</font>
<font style="color:rgb(15, 17, 21);">如果产品需要多 Agent 路由、per-session 排队、IM 通道适配，使用 Channel：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
HarnessAgent agent = factory.build(spec);
ChatUiChannel chat = agent.channel(ChatUiChannel.create());

Msg reply = chat.send(SendOptions.userId("user-1"), "你好").block();
```

<font style="color:rgb(15, 17, 21);">Channel 内部自动创建 Gateway，负责会话映射、同 session 并发排队、多 Agent 路由</font><font style="color:rgb(15, 17, 21);">。产品级可以按需选择：</font>

+ **<font style="color:rgb(15, 17, 21);">简单产品</font>**<font style="color:rgb(15, 17, 21);">：直接用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent.call(...)</font>`<font style="color:rgb(15, 17, 21);"> + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext</font>`<font style="color:rgb(15, 17, 21);">；</font>
+ **<font style="color:rgb(15, 17, 21);">多 Agent / 多通道</font>**<font style="color:rgb(15, 17, 21);">：用 Channel，但注意 Channel 模式下 Agent 实例可以复用（Gateway 按 session 排队）</font><font style="color:rgb(15, 17, 21);">。</font>

## <font style="color:rgb(15, 17, 21);">九、产品语义与能力供给的分离</font>
### <font style="color:rgb(15, 17, 21);">9.1 产品域只依赖运行层接口</font>
<font style="color:rgb(15, 17, 21);">产品域不依赖 AgentScope 类型，只依赖运行层定义的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentSpec</font>`<font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRequest</font>`<font style="color:rgb(15, 17, 21);">：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
// product/review/ReviewController
@PostMapping("/api/review/analyze")
public Flux<ServerSentEvent<AgentEvent>> analyze(@RequestBody ReviewRequest req) {
    AgentRun run = runtime.start(ReviewSpec.SPEC, runtimeContext(req));
    return run.stream(new AgentRequest(
        ReviewSpec.SPEC, runtimeContext(req), ReviewPrompt.SYSTEM, req.diff(), null, null
    )).map(this::toSse).doOnTerminate(run::close);
}
```

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ReviewPrompt</font>`<font style="color:rgb(15, 17, 21);"> 住在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">product/review</font>`<font style="color:rgb(15, 17, 21);">，不在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">base/prompt</font>`<font style="color:rgb(15, 17, 21);">。</font>**<font style="color:rgb(15, 17, 21);">base 不认识审查的提示词。</font>**

### <font style="color:rgb(15, 17, 21);">9.2 能力按需注入</font>
<font style="color:rgb(15, 17, 21);">产品域只注入自己需要的能力：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
// product/content 需要生图
@Service
public class ContentCoverService {
    private final ImageCall imageCall;   // capability/image
    // 不需要 HarnessAgent、不需要 AgentRuntime
}
```

### <font style="color:rgb(15, 17, 21);">9.3 跨应用检索的显式表达</font>
<font style="color:rgb(15, 17, 21);">“搭子问知识库”不再靠 runtime 分析文本：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
// product/companion
AgentRequest req = new AgentRequest(
    CompanionSpec.SPEC,
    ctx,
    CompanionPrompt.SYSTEM,
    userText,
    "knowledge",          // retrieveAgent：从知识库应用检索
    extractQuery(userText) // retrieveQuery：产品域决定的问句
);
```

<font style="color:rgb(15, 17, 21);">runtime 只消费，不猜测。</font>

## <font style="color:rgb(15, 17, 21);">十、多租户隔离：从补偿逻辑变成框架强制</font>
### <font style="color:rgb(15, 17, 21);">10.1 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext</font>`<font style="color:rgb(15, 17, 21);"> 强制携带身份</font>
<font style="color:rgb(15, 17, 21);">2.0.4 把 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext</font>`<font style="color:rgb(15, 17, 21);"> 改为</font>**<font style="color:rgb(15, 17, 21);">显式传递</font>**<font style="color:rgb(15, 17, 21);">，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContextAware</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ToolInjection</font>`<font style="color:rgb(15, 17, 21);"> 等隐式注入 API 被删除</font><font style="color:rgb(15, 17, 21);">。这意味着：</font>

+ <font style="color:rgb(15, 17, 21);">工具方法上直接声明 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext ctx</font>`<font style="color:rgb(15, 17, 21);"> 参数，框架按类型注入，不加 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@ToolParam</font>`<font style="color:rgb(15, 17, 21);">，模型看不到；</font>
+ <font style="color:rgb(15, 17, 21);">middleware 回调签名本来带 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext ctx</font>`<font style="color:rgb(15, 17, 21);">；</font>
+ **<font style="color:rgb(15, 17, 21);">不存在“忘了传 context 导致串用户”的隐式路径</font>**<font style="color:rgb(15, 17, 21);">，因为框架不再帮你藏。</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
@Tool(name = "list_my_orders", description = "查询当前用户的订单")
public String listOrders(RuntimeContext ctx,
                         @ToolParam(name = "status") String status) {
    return query(ctx.getUserId(), status);
}
```

### <font style="color:rgb(15, 17, 21);">10.2 写入强制带隔离字段，检索强制过滤</font>
<font style="color:rgb(15, 17, 21);">向量入库时 metadata 必须带 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">userId</font>`<font style="color:rgb(15, 17, 21);"> + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">tenantId</font>`<font style="color:rgb(15, 17, 21);"> + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent</font>`<font style="color:rgb(15, 17, 21);">：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
new DocumentMetadata(content, docId, chunkId, Map.of(
    "user", UserDirectory.name(userId),
    "agent", agentId
));
```

<font style="color:rgb(15, 17, 21);">检索时 filter 强制带上：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
RetrieveConfig.builder()
.filter("user == \"%s\" && agent == \"%s\"".formatted(user, agent))
.build();
```

**<font style="color:rgb(15, 17, 21);">不要再用 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">visible()</font>**`**<font style="color:rgb(15, 17, 21);"> 这类“查询后过滤”的补偿函数</font>**<font style="color:rgb(15, 17, 21);">。隔离由存储层保证。</font>

### <font style="color:rgb(15, 17, 21);">10.3 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">IsolationScope</font>`<font style="color:rgb(15, 17, 21);"> 上线前定死</font>
`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SESSION / USER / AGENT / GLOBAL</font>`<font style="color:rgb(15, 17, 21);"> 四档</font>**<font style="color:rgb(15, 17, 21);">上线后不要改</font>**<font style="color:rgb(15, 17, 21);">，改了旧数据不会自动迁移</font><font style="color:rgb(15, 17, 21);">。</font>

+ <font style="color:rgb(15, 17, 21);">多用户 SaaS：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SESSION</font>`<font style="color:rgb(15, 17, 21);"> 或 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">USER</font>`<font style="color:rgb(15, 17, 21);">；</font>
+ <font style="color:rgb(15, 17, 21);">公共知识库型 Agent：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AGENT</font>`<font style="color:rgb(15, 17, 21);">；</font>
+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">GLOBAL</font>`<font style="color:rgb(15, 17, 21);"> 慎用，有并发写竞争。</font>

## <font style="color:rgb(15, 17, 21);">十一、观测与计费：接口反转</font>
### <font style="color:rgb(15, 17, 21);">11.1 接口定义在 runtime，实现在 capability</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
// runtime/call-observer
public interface CallObserver {
    void onStart(String traceId, String agentId);
    void onStep(String traceId, String step, String detail);
    void onFinish(String traceId, String outcome);
}

public interface UsageMeter {
    void onCall(String model, Usage usage);   // 拿得到 token 数，不是只有模型名
}
```

<font style="color:rgb(15, 17, 21);">java</font>

```java
// capability/observe 实现 CallObserver（桥接 OTel Span）
// capability/billing 实现 UsageMeter（写 scope_usage 表或推送计费系统）
// app 装配
```

**<font style="color:rgb(15, 17, 21);">依赖方向从 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">base → chain → base</font>**`**<font style="color:rgb(15, 17, 21);"> 的环变成 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">capability → runtime</font>**`**<font style="color:rgb(15, 17, 21);">（单向）</font>**<font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">11.2 OTel 是默认能力</font>
<font style="color:rgb(15, 17, 21);">框架默认集成 OpenTelemetry，观测数据可上报到任何兼容 OTEL 的平台（LangFuse、Jaeger、阿里云 Agent Loop 等）</font><font style="color:rgb(15, 17, 21);">。产品级只需：</font>

<font style="color:rgb(15, 17, 21);">java</font>

<font style="color:rgb(15, 17, 21);">.middleware(new OtelTracingMiddleware())</font>

+ <font style="color:rgb(15, 17, 21);">标准 OTel 环境变量（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">OTEL_EXPORTER_OTLP_ENDPOINT</font>`<font style="color:rgb(15, 17, 21);">、service name）。</font>

### <font style="color:rgb(15, 17, 21);">11.3 计费拿得到 token 数</font>
<font style="color:rgb(15, 17, 21);">2.0.4 公开了详细的 chat usage token metrics</font><font style="color:rgb(15, 17, 21);">。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">UsageMeter.onCall</font>`<font style="color:rgb(15, 17, 21);"> 应该记录 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">inputTokens</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">outputTokens</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">model</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">latency</font>`<font style="color:rgb(15, 17, 21);">，而不是只有模型名。</font>

## <font style="color:rgb(15, 17, 21);">十二、安全与沙箱</font>
### <font style="color:rgb(15, 17, 21);">12.1 沙箱只约束“会触达文件/进程”的工具</font>
<font style="color:rgb(15, 17, 21);">Harness 自带的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">read_file/write_file/shell</font>`<font style="color:rgb(15, 17, 21);"> 等受沙箱约束；普通 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@Tool</font>`<font style="color:rgb(15, 17, 21);"> 方法（调内部 API、查数据库）在宿主 JVM 内执行，不受沙箱文件系统约束</font><font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">12.2 生产配置</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
.filesystem(new DockerFilesystemSpec()
            .image("ubuntu:24.04")
            .isolationScope(IsolationScope.SESSION)     // 按对话隔离
            .memorySizeBytes(512L * 1024 * 1024)        // 512MB
            .cpuCount(1L)
            .workspaceRoot("/workspace"))
```

<font style="color:rgb(15, 17, 21);">沙箱参数集中在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SandboxFilesystemSpec</font>`<font style="color:rgb(15, 17, 21);"> 上，包括镜像、内存/CPU 限额、端口、环境变量、执行锁</font><font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">12.3 快照与多副本</font>
+ <font style="color:rgb(15, 17, 21);">每次 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">call()</font>`<font style="color:rgb(15, 17, 21);"> 结束把工作区打成快照；</font>
+ <font style="color:rgb(15, 17, 21);">快照落点由 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">snapshotSpec</font>`<font style="color:rgb(15, 17, 21);"> 决定：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NoopSnapshotSpec</font>`<font style="color:rgb(15, 17, 21);">（不存）、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">LocalSnapshotSpec</font>`<font style="color:rgb(15, 17, 21);">（本机）、OSS 等远端；</font>
+ <font style="color:rgb(15, 17, 21);">沙箱元数据（容器 ID、快照指针）与 Agent 状态</font>**<font style="color:rgb(15, 17, 21);">存在同一个 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentStateStore</font>**`<font style="color:rgb(15, 17, 21);">，配了 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<font style="color:rgb(15, 17, 21);"> 后跨副本 resume 自动可用；</font>
+ <font style="color:rgb(15, 17, 21);">2.0.4 支持按沙箱自动清理旧的 E2B 原生快照。</font>

## <font style="color:rgb(15, 17, 21);">十四、部署形态与运维</font>
### <font style="color:rgb(15, 17, 21);">14.1 无状态水平扩展</font>
+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent</font>`<font style="color:rgb(15, 17, 21);"> 每请求 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">build()</font>`<font style="color:rgb(15, 17, 21);">；</font>
+ <font style="color:rgb(15, 17, 21);">状态全在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<font style="color:rgb(15, 17, 21);">；</font>
+ <font style="color:rgb(15, 17, 21);">Pod 可以随意扩缩容；</font>
+ <font style="color:rgb(15, 17, 21);">同 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">(userId, sessionId)</font>`<font style="color:rgb(15, 17, 21);"> 的并发请求依赖 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SessionTurnGate</font>`<font style="color:rgb(15, 17, 21);"> 或 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ConflictPolicy.FAIL</font>`<font style="color:rgb(15, 17, 21);"> 减少重复 LLM 轮次，正确性由 CAS 保证</font><font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">14.2 健康检查分离</font>
+ **<font style="color:rgb(15, 17, 21);">liveness</font>**<font style="color:rgb(15, 17, 21);">：只看进程活；</font>
+ **<font style="color:rgb(15, 17, 21);">readiness</font>**<font style="color:rgb(15, 17, 21);">：看 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<font style="color:rgb(15, 17, 21);">（Redis/MySQL）、向量库是否可用；</font>
+ <font style="color:rgb(15, 17, 21);">依赖抖动不要触发 liveness 失败，否则 k8s 会误杀。</font>

### <font style="color:rgb(15, 17, 21);">14.3 优雅停机</font>
<font style="color:rgb(15, 17, 21);">java</font>

```plain
GracefulShutdownManager manager = new GracefulShutdownManager(config);
// 或使用框架默认注册的 JVM hook
```

<font style="color:rgb(15, 17, 21);">SIGTERM 后不再接新调用，等待在途 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRun</font>`<font style="color:rgb(15, 17, 21);"> 完成</font><font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">14.4 密钥管理</font>
| <font style="color:rgb(15, 17, 21);">项</font> | <font style="color:rgb(15, 17, 21);">开发态</font> | <font style="color:rgb(15, 17, 21);">生产态</font> |
| --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">模型密钥</font> | <font style="color:rgb(15, 17, 21);">环境变量</font> | <font style="color:rgb(15, 17, 21);">KMS / Vault / Nacos 加密配置</font> |
| <font style="color:rgb(15, 17, 21);">StateStore</font> | <font style="color:rgb(15, 17, 21);">本地文件</font> | <font style="color:rgb(15, 17, 21);">Redis / MySQL（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<br/><font style="color:rgb(15, 17, 21);">）</font> |
| <font style="color:rgb(15, 17, 21);">向量库</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">InMemoryStore</font>` | <font style="color:rgb(15, 17, 21);">Milvus / pgvector</font> |
| <font style="color:rgb(15, 17, 21);">对象存储</font> | <font style="color:rgb(15, 17, 21);">本地 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">data/</font>` | <font style="color:rgb(15, 17, 21);">OSS / S3</font> |


## <font style="color:rgb(15, 17, 21);">附录：2.0.4 关键变更速查</font>
| <font style="color:rgb(15, 17, 21);">变更</font> | <font style="color:rgb(15, 17, 21);">影响</font> | <font style="color:rgb(15, 17, 21);">迁移动作</font> |
| --- | --- | --- |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext</font>`<br/><font style="color:rgb(15, 17, 21);"> 改为显式传递</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContextAware</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ToolInjection</font>`<br/><font style="color:rgb(15, 17, 21);"> 删除</font> | <font style="color:rgb(15, 17, 21);">工具方法加 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext</font>`<br/><font style="color:rgb(15, 17, 21);"> 参数；middleware 从签名取</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRun / RunControl</font>`<br/><font style="color:rgb(15, 17, 21);"> 新增</font> | <font style="color:rgb(15, 17, 21);">单次执行可独立取消、隔离</font> | <font style="color:rgb(15, 17, 21);">运行层用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRun</font>`<br/><font style="color:rgb(15, 17, 21);"> 管理生命周期</font> |
| <font style="color:rgb(15, 17, 21);">请求级 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ToolRequestConfig</font>` | <font style="color:rgb(15, 17, 21);">工具配置按请求隔离，不再共享 Toolkit</font> | <font style="color:rgb(15, 17, 21);">需要按请求改工具时用它，不要改共享 Toolkit</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<br/><font style="color:rgb(15, 17, 21);"> 一键配置</font> | <font style="color:rgb(15, 17, 21);">StateStore / BaseStore / Snapshot / Guard 一起注入</font> | <font style="color:rgb(15, 17, 21);">生产用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RedisDistributedStore.fromJedis(jedis)</font>` |
| <font style="color:rgb(15, 17, 21);">OTel 默认集成</font> | <font style="color:rgb(15, 17, 21);">观测数据可上报任意兼容平台</font> | <font style="color:rgb(15, 17, 21);">加 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">OtelTracingMiddleware</font>`<br/><font style="color:rgb(15, 17, 21);"> + OTel 环境变量</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">returnDirect</font>`<br/><font style="color:rgb(15, 17, 21);"> 工具结果</font> | <font style="color:rgb(15, 17, 21);">工具结果可直接结束推理循环</font> | <font style="color:rgb(15, 17, 21);">适合“查完就返回”的工具</font> |
| <font style="color:rgb(15, 17, 21);">Token metrics 公开</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">UsageMeter</font>`<br/><font style="color:rgb(15, 17, 21);"> 拿得到 input/output token</font> | <font style="color:rgb(15, 17, 21);">计费不再只有模型名</font> |


---

_**<font style="color:#DF2A3F;">一句话总结：AgentScope 2.0.4 已经把“从 Demo 到生产”的工程化缺口补得差不多了——</font>**_`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">DistributedStore</font>**_`_**<font style="color:#DF2A3F;"> 一行配置解决状态共享，</font>**_`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">RuntimeContext</font>**_`_**<font style="color:#DF2A3F;"> 显式传递解决多租户隔离，OTel 默认集成解决可观测，</font>**_`_**<font style="color:#DF2A3F;background-color:rgb(235, 238, 242);">HarnessAgent</font>**_`_**<font style="color:#DF2A3F;"> 每请求 build 解决并发安全。产品级架构的重点不是“自己再实现一套”，而是把 Spring Boot 的接入/计费/观测与 HarnessAgent 的无状态内核正确对齐：产品语义归产品，执行生命周期归 runtime，能力归 capability，状态归 DistributedStore。</font>**_

## <font style="color:rgb(15, 17, 21);">一、三个决策的架构收敛</font>
| <font style="color:rgb(15, 17, 21);">决策</font> | <font style="color:rgb(15, 17, 21);">收敛结果</font> | <font style="color:rgb(15, 17, 21);">影响面</font> |
| --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">① 快照放 MySQL</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<br/>**<font style="color:rgb(15, 17, 21);">全量用 MySQL</font>**<font style="color:rgb(15, 17, 21);">，不引入 Redis 混合后端</font> | <font style="color:rgb(15, 17, 21);">依赖少一个；快照大小需约束</font> |
| <font style="color:rgb(15, 17, 21);">② RocketMQ 预留</font> | <font style="color:rgb(15, 17, 21);">定义 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentTaskPublisher</font>`<br/><font style="color:rgb(15, 17, 21);"> 接口 + 消息模型，当前用</font>**<font style="color:rgb(15, 17, 21);">同步直调实现</font>**<font style="color:rgb(15, 17, 21);">，MQ 实现留空</font> | <font style="color:rgb(15, 17, 21);">接口先定，实现后补</font> |
| <font style="color:rgb(15, 17, 21);">③ Nacos Prompt 替代常量</font> | <font style="color:rgb(15, 17, 21);">提示词全部迁 Nacos，代码只保留 </font>**<font style="color:rgb(15, 17, 21);">fallback 默认值</font>** | <font style="color:rgb(15, 17, 21);">新增 Prompt 加载层 + 发布流程</font> |


<font style="color:rgb(15, 17, 21);">最终基础设施：</font>

<font style="color:rgb(15, 17, 21);">text</font>

```plain
MySQL    → AgentStateStore + BaseStore + SandboxSnapshot + SandboxExecutionGuard（全量）
Redis    → 业务缓存、额度计数、幂等键（不承担 AgentScope 状态）
Milvus   → 向量检索
Nacos    → 配置 + 提示词热更新 +（未来）A2A 注册
RocketMQ → 预留，接口先行，实现后补
```

---

## <font style="color:rgb(15, 17, 21);">二、MySQL：全量 DistributedStore</font>
### <font style="color:rgb(15, 17, 21);">2.1 一行装配</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
@Bean
public DistributedStore distributedStore(DataSource dataSource) {
MysqlDistributedStore store = MysqlDistributedStore.create(dataSource);
return DistributedStore.builder()
.agentStateStore(store.agentStateStore())
.baseStore(store.baseStore())
.sandboxSnapshotSpec(store.sandboxSnapshotSpec())
.sandboxExecutionGuard(store.sandboxExecutionGuard())
.build();
}
```

<font style="color:rgb(15, 17, 21);">四个组件全在 MySQL，不再需要 Redis 做锁或快照。</font>**<font style="color:rgb(15, 17, 21);">沙箱执行锁用 MySQL 的 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">GET_LOCK()</font>**`**<font style="color:rgb(15, 17, 21);"> 或表级行锁</font>**<font style="color:rgb(15, 17, 21);">，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MysqlDistributedStore</font>`<font style="color:rgb(15, 17, 21);"> 内部已处理，不需要额外配置。</font>

### <font style="color:rgb(15, 17, 21);">2.2 表结构（Flyway 管理，关闭自动建表）</font>
<font style="color:rgb(15, 17, 21);">sql</font>

```sql
-- 会话状态
CREATE TABLE agentscope_sessions (
  session_id   VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
  state_key    VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
  item_index   INT NOT NULL DEFAULT 0,
  state_data   LONGTEXT NOT NULL,
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (session_id, state_key, item_index)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 工作区文件 KV（MEMORY.md、sessions、skills）
CREATE TABLE agentscope_store (
  namespace_path VARCHAR(512) COLLATE utf8mb4_bin NOT NULL,
  key_name       VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
  value_data     LONGBLOB NOT NULL,
  created_at     DATETIME DEFAULT CURRENT_TIMESTAMP,
  updated_at     DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (namespace_path, key_name)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 沙箱快照（MysqlDistributedStore 默认表名）
CREATE TABLE agentscope_sandbox_snapshots (
  snapshot_key VARCHAR(255) COLLATE utf8mb4_bin NOT NULL,
  snapshot_data LONGBLOB NOT NULL,
  created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (snapshot_key)
) DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

<font style="color:rgb(15, 17, 21);">配置里关闭自动建表：</font>

<font style="color:rgb(15, 17, 21);">yaml</font>

```plain
agentscope:
  distributed:
    mysql:
      create-if-not-exist: false
```

### <font style="color:rgb(15, 17, 21);">2.3 快照放 MySQL 的三个硬约束（必须提前处理）</font>
<font style="color:rgb(15, 17, 21);">MySQL 存快照不是“换个后端”就完事，有三个必须约束的点：</font>

**<font style="color:rgb(15, 17, 21);">① </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">max_allowed_packet</font>**`**<font style="color:rgb(15, 17, 21);"> 必须调大</font>**

<font style="color:rgb(15, 17, 21);">默认 64MB，快照走 JDBC 写 BLOB 时按 packet 分片。建议：</font>

<font style="color:rgb(15, 17, 21);">ini</font>

```plain
# my.cnf
max_allowed_packet = 256M
```

<font style="color:rgb(15, 17, 21);">应用侧 JDBC URL 加 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">rewriteBatchedStatements=true</font>`<font style="color:rgb(15, 17, 21);">，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MysqlDistributedStore</font>`<font style="color:rgb(15, 17, 21);"> 的批量写入会受益。</font>

**<font style="color:rgb(15, 17, 21);">② 快照大小必须约束在工作区层面</font>**

<font style="color:rgb(15, 17, 21);">沙箱工作区里一旦有 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">node_modules</font>`<font style="color:rgb(15, 17, 21);">、大日志、模型生成的二进制，快照会迅速膨胀到几百 MB，MySQL 写入延迟从毫秒级掉到秒级。</font>**<font style="color:rgb(15, 17, 21);">在 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SandboxFilesystemSpec</font>**`**<font style="color:rgb(15, 17, 21);"> 上限制工作区，并在工具层禁止写大文件</font>**<font style="color:rgb(15, 17, 21);">：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```plain
.filesystem(new DockerFilesystemSpec()
    .image("ubuntu:24.04")
    .workspaceRoot("/workspace")
    .memorySizeBytes(512L * 1024 * 1024))
```

<font style="color:rgb(15, 17, 21);">同时在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MiddlewareBase.onActing</font>`<font style="color:rgb(15, 17, 21);"> 里对 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">write_file</font>`<font style="color:rgb(15, 17, 21);"> 的入参做大小检查（比如单文件 > 5MB 直接拒绝）。</font>**<font style="color:rgb(15, 17, 21);">没有这个约束，MySQL 存快照会在生产环境出问题</font>**<font style="color:rgb(15, 17, 21);">。</font>

**<font style="color:rgb(15, 17, 21);">③ 恢复延迟要接受</font>**

<font style="color:rgb(15, 17, 21);">MySQL BLOB 读取 10MB 快照约 50–200ms（视网络和存储），比 Redis 慢一个数量级。对“低频沙箱”可接受；如果未来沙箱变成高频能力（每秒多次 resume），再考虑把 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">sandboxSnapshotSpec</font>`<font style="color:rgb(15, 17, 21);"> 单独切到 Redis 或对象存储——</font>**<font style="color:rgb(15, 17, 21);">混合后端只换这一个组件，不影响其他三个</font>**<font style="color:rgb(15, 17, 21);">。</font>

---

## <font style="color:rgb(15, 17, 21);">三、Redis：重新定位，不承担 AgentScope 状态</font>
<font style="color:rgb(15, 17, 21);">全 MySQL 后，Redis 从“AgentScope 存储”退回到它的本职——</font>**<font style="color:rgb(15, 17, 21);">缓存中间件</font>**<font style="color:rgb(15, 17, 21);">。在这个架构里承担四件事：</font>

| <font style="color:rgb(15, 17, 21);">用途</font> | <font style="color:rgb(15, 17, 21);">Key 设计</font> | <font style="color:rgb(15, 17, 21);">TTL</font> |
| --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">当日额度计数</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">quota:{tenant}:{user}:{yyyyMMdd}</font>` | <font style="color:rgb(15, 17, 21);">48h</font> |
| <font style="color:rgb(15, 17, 21);">幂等键（SSE 重连、重复提交）</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">idem:{requestId}</font>` | <font style="color:rgb(15, 17, 21);">10min</font> |
| <font style="color:rgb(15, 17, 21);">会话列表缓存（避免每次查 MySQL）</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">session-list:{userId}</font>` | <font style="color:rgb(15, 17, 21);">5min，写时失效</font> |
| <font style="color:rgb(15, 17, 21);">限流计数器</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">rate:{endpoint}:{user}:{window}</font>` | <font style="color:rgb(15, 17, 21);">窗口长度</font> |


**<font style="color:rgb(15, 17, 21);">Redis 不承担</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentStateStore</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">BaseStore</font>`<font style="color:rgb(15, 17, 21);">、沙箱快照、沙箱锁。这四个全在 MySQL。</font>

<font style="color:rgb(15, 17, 21);">如果未来 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentStateStore</font>`<font style="color:rgb(15, 17, 21);"> 的读延迟成为瓶颈（比如每轮推理都要读一次全量 state），再单独把它切到 Redis——</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore.builder()</font>`<font style="color:rgb(15, 17, 21);"> 支持按组件混合，</font>**<font style="color:rgb(15, 17, 21);">只换这一个</font>**<font style="color:rgb(15, 17, 21);">：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```plain
// 未来优化路径，当前不做
.agentStateStore(RedisDistributedStore.fromJedis(jedis).agentStateStore())
.baseStore(mysql.baseStore())
```

---

## <font style="color:rgb(15, 17, 21);">四、RocketMQ：接口先行，实现后补</font>
### <font style="color:rgb(15, 17, 21);">4.1 定义接口与消息模型</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
// runtime/task
public interface AgentTaskPublisher {
    void publish(AgentTask task);
}

public record AgentTask(
    String taskId,
    String type,              // "doc-ingest" / "batch-review" / "report-gen"
    String tenantId,
    String userId,
    String agentId,
    String sessionId,
    Map<String, Object> payload,
    Instant createdAt
) {}
```

### <font style="color:rgb(15, 17, 21);">4.2 当前实现：同步直调（预留期）</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
@Component
@ConditionalOnProperty(name = "agentscope.task.mode", havingValue = "sync", matchIfMissing = true)
public class SyncTaskPublisher implements AgentTaskPublisher {
    private final AgentTaskHandler handler;

    @Override
    public void publish(AgentTask task) {
        handler.handle(task);   // 当前同步执行
    }
}
```

### <font style="color:rgb(15, 17, 21);">4.3 预留实现：RocketMQ（暂不启用）</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
@Component
@ConditionalOnProperty(name = "agentscope.task.mode", havingValue = "mq")
public class RocketMqTaskPublisher implements AgentTaskPublisher {
    private final RocketMQTemplate mq;

    @Override
    public void publish(AgentTask task) {
        mq.syncSend("agentscope-tasks", MessageBuilder
                    .withPayload(task)
                    .setKeys(task.taskId())
                    .build());
    }
}
```

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentTaskHandler</font>`<font style="color:rgb(15, 17, 21);"> 是消费者逻辑，当前 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SyncTaskPublisher</font>`<font style="color:rgb(15, 17, 21);"> 直接调它；未来切 RocketMQ 时，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RocketMqTaskPublisher</font>`<font style="color:rgb(15, 17, 21);"> 发消息，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@RocketMQMessageListener</font>`<font style="color:rgb(15, 17, 21);"> 消费后调同一个 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentTaskHandler</font>`<font style="color:rgb(15, 17, 21);">。</font>**<font style="color:rgb(15, 17, 21);">业务代码不变，只换 Publisher 实现</font>**<font style="color:rgb(15, 17, 21);">。</font>

<font style="color:rgb(15, 17, 21);">配置切换：</font>

<font style="color:rgb(15, 17, 21);">yaml</font>

```plain
agentscope:
  task:
    mode: sync   # 当前；未来改 mq 并引入 rocketmq-spring-boot-starter
```

### <font style="color:rgb(15, 17, 21);">4.4 不要现在做的事</font>
+ **<font style="color:rgb(15, 17, 21);">不要引入 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">rocketmq-spring-boot-starter</font>**`<font style="color:rgb(15, 17, 21);">。接口和模型先定，依赖等到真正切 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">mode: mq</font>`<font style="color:rgb(15, 17, 21);"> 时再加。</font>
+ **<font style="color:rgb(15, 17, 21);">不要在 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MiddlewareBase</font>**`**<font style="color:rgb(15, 17, 21);"> 里发 MQ 消息</font>**<font style="color:rgb(15, 17, 21);">。事件桥接（把 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentEndEvent</font>`<font style="color:rgb(15, 17, 21);"> 推到 MQ）是另一种需求，和“任务异步化”不同。当前两者都不做。</font>
+ **<font style="color:rgb(15, 17, 21);">不要在 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentFactory</font>**`**<font style="color:rgb(15, 17, 21);"> 里感知 Publisher</font>**<font style="color:rgb(15, 17, 21);">。任务发布是业务层的事，runtime 只管单次执行。</font>

---

## <font style="color:rgb(15, 17, 21);">五、Nacos Prompt：完整替代方案</font>
### <font style="color:rgb(15, 17, 21);">5.1 代码里只保留 fallback</font>
<font style="color:rgb(15, 17, 21);">java</font>

```java
// product/review/ReviewPrompt
public final class ReviewPrompt {
    // fallback：Nacos 不可用、或 key 未配置时使用
    static final String SYSTEM_FALLBACK = """
    你是代码审查助手。分析用户提供的 diff，输出 JSON：
    {"score": 0-100, "summary": "...", "issues": [...]}
    """;

    // 从 Nacos 取，取不到用 fallback
    public static String system(NacosPromptService prompts) {
        return prompts.get("prompt.review.system", Map.of(), SYSTEM_FALLBACK);
    }
}
```

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NacosPromptService</font>`<font style="color:rgb(15, 17, 21);"> 是薄封装，包一层 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NacosPromptListener</font>`<font style="color:rgb(15, 17, 21);">，统一处理 fallback、空值、模板变量：</font>

<font style="color:rgb(15, 17, 21);">java</font>

```java
@Component
public class NacosPromptService {
    private final NacosPromptListener listener;

    public String get(String key, Map<String, String> vars, String fallback) {
        try {
            String template = listener.getPrompt(key, vars);
            return template == null || template.isBlank() ? fallback : template;
        } catch (Exception e) {
            log.warn("nacos prompt fetch failed, key={}, using fallback", key);
            return fallback;
        }
    }
}
```

**<font style="color:rgb(15, 17, 21);">关键</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">catch (Exception)</font>`<font style="color:rgb(15, 17, 21);"> 兜底。Nacos 抖动不能让业务 500，也不能让提示词变成空串——空提示词比 fallback 更危险。</font>

### <font style="color:rgb(15, 17, 21);">5.2 Nacos Key 命名规范</font>
<font style="color:rgb(15, 17, 21);">text</font>

```plain
prompt.{agent}.{purpose}[.{variant}]

示例：
prompt.review.system              审查主提示词
prompt.companion.system           搭子系统提示词
prompt.companion.style-check      风格检查
prompt.knowledge.answer           知识库回答
prompt.content.outline            大纲生成
prompt.content.cover-scene        封面场景描述
```

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">{variant}</font>`<font style="color:rgb(15, 17, 21);"> 用于 A/B 或多租户定制（如 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">prompt.review.system.strict</font>`<font style="color:rgb(15, 17, 21);">）。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NacosPromptListener</font>`<font style="color:rgb(15, 17, 21);"> 支持按 key 拉取，variant 由业务层拼 key。</font>

### <font style="color:rgb(15, 17, 21);">5.3 Nacos 侧的发布流程</font>
<font style="color:rgb(15, 17, 21);">text</font>

```plain
Git 仓库（prompt/*.md）
    ↓ CI 校验（变量占位符完整性、JSON schema 合法性）
    ↓ 发布到 Nacos（namespace=prod，group=agentscope）
    ↓ NacosPromptListener 推送更新
    ↓ 应用下一次 get() 返回新版本（无需重启）
```

**<font style="color:rgb(15, 17, 21);">三条硬规则</font>**<font style="color:rgb(15, 17, 21);">：</font>

1. **<font style="color:rgb(15, 17, 21);">Nacos 上的提示词必须来自 Git</font>**<font style="color:rgb(15, 17, 21);">。不允许在 Nacos 控制台直接改——没有审计、无法回滚、多人协作冲突。CI 负责从 Git 推 Nacos，Nacos 只做分发。</font>
2. **<font style="color:rgb(15, 17, 21);">变量占位符必须完整</font>**<font style="color:rgb(15, 17, 21);">。CI 校验模板里的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">{tenant}</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">{userName}</font>`<font style="color:rgb(15, 17, 21);"> 等变量，在业务代码 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Map</font>`<font style="color:rgb(15, 17, 21);"> 里都能找到对应 key，否则运行时渲染出 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">{tenant}</font>`<font style="color:rgb(15, 17, 21);"> 原文。</font>
3. **<font style="color:rgb(15, 17, 21);">JSON 输出的提示词必须校验 schema</font>**<font style="color:rgb(15, 17, 21);">。审查、结构化输出的提示词，CI 里跑一次 mock 渲染 + JSON 解析，防止运维改错一个引号导致模型输出非法 JSON。</font>

### <font style="color:rgb(15, 17, 21);">5.4 哪些提示词不建议放 Nacos</font>
| <font style="color:rgb(15, 17, 21);">提示词类型</font> | <font style="color:rgb(15, 17, 21);">放 Nacos</font> | <font style="color:rgb(15, 17, 21);">理由</font> |
| --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">系统身份、角色设定</font> | <font style="color:rgb(15, 17, 21);">✅</font> | <font style="color:rgb(15, 17, 21);">高频调整，产品运营可改</font> |
| <font style="color:rgb(15, 17, 21);">输出格式说明（JSON schema 描述）</font> | <font style="color:rgb(15, 17, 21);">⚠️</font><font style="color:rgb(15, 17, 21);"> 谨慎</font> | <font style="color:rgb(15, 17, 21);">与代码里的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ReviewOutput.schema()</font>`<br/><font style="color:rgb(15, 17, 21);"> 强耦合，改错会让结构化输出失败。建议 CI 强校验</font> |
| <font style="color:rgb(15, 17, 21);">工具调用指令</font> | <font style="color:rgb(15, 17, 21);">❌</font> | <font style="color:rgb(15, 17, 21);">与工具注册强耦合，提示词和代码必须一起改</font> |
| <font style="color:rgb(15, 17, 21);">安全护栏提示</font> | <font style="color:rgb(15, 17, 21);">❌</font> | <font style="color:rgb(15, 17, 21);">属于安全契约，不应运维可改</font> |


**<font style="color:rgb(15, 17, 21);">边界</font>**<font style="color:rgb(15, 17, 21);">：Nacos 管“话术”，代码管“契约”。凡是被代码逻辑依赖的格式（JSON schema、工具名、必填字段），留在代码里；凡是模型理解的自然语言描述，可以放 Nacos。</font>

---

## <font style="color:rgb(15, 17, 21);">六、最终依赖清单</font>
<font style="color:rgb(15, 17, 21);">xml</font>

```xml
<!-- AgentScope 核心 -->
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-harness</artifactId>
  <version>2.0.4</version>
</dependency>
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-model-dashscope</artifactId>
  <version>2.0.4</version>
</dependency>

<!-- 分布式存储：全 MySQL -->
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-mysql</artifactId>
  <version>2.0.4</version>
</dependency>

<!-- RAG：Milvus -->
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-rag-simple</artifactId>
  <version>2.0.4</version>
</dependency>

<!-- Nacos Prompt -->
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-nacos-prompt</artifactId>
  <version>2.0.4</version>
</dependency>

<!-- 业务缓存与额度 -->
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- RocketMQ：当前不引入，预留 -->
<!--
<dependency>
<groupId>org.apache.rocketmq</groupId>
<artifactId>rocketmq-spring-boot-starter</artifactId>
<version>2.3.1</version>
</dependency>
-->
```

**<font style="color:rgb(15, 17, 21);">注意</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agentscope-extensions-redis</font>`**<font style="color:rgb(15, 17, 21);">不在清单里</font>**<font style="color:rgb(15, 17, 21);">。Redis 只作为 Spring Data Redis 使用，不引入 AgentScope 的 Redis 扩展——因为不需要它做 StateStore 或 Guard。</font>

---

## <font style="color:rgb(15, 17, 21);">七、最终 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">application.yml</font>`
<font style="color:rgb(15, 17, 21);">yaml</font>

```plain
spring:
  datasource:
    url: jdbc:mysql://mysql:3306/agentscope?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&rewriteBatchedStatements=true
    username: ${MYSQL_USER}
    password: ${MYSQL_PASSWORD}
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
  data:
    redis:
      host: ${REDIS_HOST:redis}
      port: 6379
      password: ${REDIS_PASSWORD:}
      lettuce:
        pool:
          max-active: 16

agentscope:
  model:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
  vector:
    milvus:
      uri: http://milvus:19530
      collection: knowledge
      dimensions: 1024
      metric-type: COSINE
  distributed:
    mysql:
      create-if-not-exist: false
  nacos:
    server-addr: ${NACOS_ADDR}
    namespace: ${NACOS_NAMESPACE:prod}
    group: agentscope
  task:
    mode: sync   # 预留：未来改 mq

rocketmq:
  name-server: ${ROCKETMQ_NAMESRV:}
  # 当前 mode=sync 时不使用
```

---

## <font style="color:rgb(15, 17, 21);">八、最终部署拓扑</font>
<font style="color:rgb(15, 17, 21);">text</font>

```plain
┌─────────────┐
                    │   Nacos     │ 配置 + 提示词（+ 未来 A2A）
                    └──────┬──────┘
                           │ 拉 prompt / config
┌──────────┐    ┌──────────▼──────────┐    ┌─────────────┐
│  Client  │───▶│  Spring Boot App    │───▶│   MySQL     │ StateStore + BaseStore
└──────────┘    │  (多副本，无状态)    │    │             │ Sandbox Snapshot + Guard
                │                     │    └─────────────┘
                │  AgentFactory       │    ┌─────────────┐
                │  DistributedStore   │───▶│   Milvus    │ 向量检索
                │  (全 MySQL)         │    └─────────────┘
                │  NacosPromptService │    ┌─────────────┐
                │  AgentTaskPublisher │───▶│   Redis     │ 额度/缓存/幂等
                │  (sync 实现)        │    └─────────────┘
                └──────────┬──────────┘
                           │ 预留
                    ┌──────▼──────┐
                    │  RocketMQ   │ 异步任务（当前未启用）
                    └─────────────┘
```

**<font style="color:rgb(15, 17, 21);">关键约束</font>**<font style="color:rgb(15, 17, 21);">：</font>

+ <font style="color:rgb(15, 17, 21);">应用 Pod </font>**<font style="color:rgb(15, 17, 21);">本地磁盘不持久化任何 AgentScope 状态</font>**<font style="color:rgb(15, 17, 21);">。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentState</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">BaseStore</font>`<font style="color:rgb(15, 17, 21);">、快照全在 MySQL。</font>
+ <font style="color:rgb(15, 17, 21);">多副本部署时 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RemoteFilesystemSpec</font>`<font style="color:rgb(15, 17, 21);"> 必须用，不能本地文件系统。</font>
+ <font style="color:rgb(15, 17, 21);">Redis 故障不影响 Agent 主链路（额度接口降级，缓存穿透到 MySQL），因为 AgentScope 状态不依赖 Redis。</font>
+ <font style="color:rgb(15, 17, 21);">Nacos 故障时 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NacosPromptService</font>`<font style="color:rgb(15, 17, 21);"> 走 fallback，业务不中断。</font>
+ <font style="color:rgb(15, 17, 21);">RocketMQ 当前不部署，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">mode: sync</font>`<font style="color:rgb(15, 17, 21);"> 时无依赖。</font>

---

## <font style="color:rgb(15, 17, 21);">九、落地检查清单</font>
<font style="color:rgb(15, 17, 21);">按顺序逐项确认，全部通过才能上生产：</font>

**<font style="color:rgb(15, 17, 21);">MySQL</font>**

+ <font style="color:rgb(97, 102, 107);">□ </font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">max_allowed_packet >= 256M</font>`

+ <font style="color:rgb(97, 102, 107);">□ </font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agentscope_sessions</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agentscope_store</font>`<font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agentscope_sandbox_snapshots</font>`<font style="color:rgb(15, 17, 21);"> 三张表由 Flyway 管理，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">create-if-not-exist: false</font>`

+ <font style="color:rgb(97, 102, 107);">□ </font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">session_id</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">namespace_path</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">snapshot_key</font>`<font style="color:rgb(15, 17, 21);"> 均为 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">utf8mb4_bin</font>`

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">快照大小有约束（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">MiddlewareBase.onActing</font>`<font style="color:rgb(15, 17, 21);"> 拒绝 > 5MB 写入）</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">连接池 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">maximum-pool-size</font>`<font style="color:rgb(15, 17, 21);"> 与 MySQL </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">max_connections</font>`<font style="color:rgb(15, 17, 21);"> 匹配</font>

**<font style="color:rgb(15, 17, 21);">Milvus</font>**

+ <font style="color:rgb(97, 102, 107);">□ </font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">collectionName</font>`<font style="color:rgb(15, 17, 21);"> 与 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">dimensions</font>`<font style="color:rgb(15, 17, 21);"> 与嵌入模型一致（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">text-embedding-v4</font>`<font style="color:rgb(15, 17, 21);"> = 1024）</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">metadata 强制带 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">user</font>`<font style="color:rgb(15, 17, 21);"> + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent</font>`

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">检索 filter 强制带 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">user == ... && agent == ...</font>`

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">换嵌入模型时重建集合的流程已文档化</font>

**<font style="color:rgb(15, 17, 21);">Redis</font>**

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">只做额度/缓存/幂等，</font>**<font style="color:rgb(15, 17, 21);">不存 AgentScope 状态</font>**

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">故障降级路径已测（额度 503，缓存穿透到 MySQL）</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">TTL 与业务窗口匹配</font>

**<font style="color:rgb(15, 17, 21);">Nacos</font>**

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">提示词由 CI 从 Git 推送，</font>**<font style="color:rgb(15, 17, 21);">禁止控制台直改</font>**

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">CI 校验：变量占位符完整、JSON 提示词 schema 合法</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NacosPromptService</font>`<font style="color:rgb(15, 17, 21);"> 有 try-catch fallback</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">安全护栏、工具指令类提示词</font>**<font style="color:rgb(15, 17, 21);">不入 Nacos</font>**

**<font style="color:rgb(15, 17, 21);">RocketMQ</font>**

+ <font style="color:rgb(97, 102, 107);">□ </font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentTaskPublisher</font>`<font style="color:rgb(15, 17, 21);"> 接口和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentTask</font>`<font style="color:rgb(15, 17, 21);"> 模型已定义</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">当前 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">mode: sync</font>`<font style="color:rgb(15, 17, 21);">，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SyncTaskPublisher</font>`<font style="color:rgb(15, 17, 21);"> 生效</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RocketMqTaskPublisher</font>`<font style="color:rgb(15, 17, 21);"> 代码预留但未引入依赖</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">切换 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">mode: mq</font>`<font style="color:rgb(15, 17, 21);"> 的步骤已文档化（引依赖、配 name-server、加 Consumer）</font>

**<font style="color:rgb(15, 17, 21);">分布式一致性</font>**

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">应用 Pod 无本地状态</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RemoteFilesystemSpec</font>`<font style="color:rgb(15, 17, 21);"> 已启用（多副本）</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

<font style="color:rgb(15, 17, 21);">Pod 重启后，任意副本能恢复会话（手工验证一次）</font>

+ <font style="color:rgb(97, 102, 107);">□ </font>

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">IsolationScope</font>`<font style="color:rgb(15, 17, 21);"> 上线前定死，不改</font>

---

## <font style="color:rgb(15, 17, 21);">十、一句话总结</font>
<font style="color:rgb(15, 17, 21);">三个决策让架构从“五个组件都重度依赖”收敛为“</font>**<font style="color:rgb(15, 17, 21);">MySQL 单点承担 AgentScope 全量状态，Redis 退回缓存本职，RocketMQ 接口先行，Nacos 管话术不管契约</font>**<font style="color:rgb(15, 17, 21);">”。落地风险集中在两处：</font>**<font style="color:rgb(15, 17, 21);">MySQL 存沙箱快照的大小约束</font>**<font style="color:rgb(15, 17, 21);">（不约束会在生产爆 BLOB）和</font>**<font style="color:rgb(15, 17, 21);">Nacos 提示词与代码契约的边界</font>**<font style="color:rgb(15, 17, 21);">（工具指令入 Nacos 会让工具调用失效）。这两处按检查清单卡住，其余都是配置级工作。</font>

## 流程图
<!-- 这是一个文本绘图，源码为：flowchart LR
    subgraph PD[产品域：产品语义]
        P1[业务意图与规则]
        P2[任务完成标准]
    end
    subgraph SB[Spring Boot：接入与工程化]
        S1[HTTP/消息接入]
        S2[配置与生命周期]
        S3[AgentRun 句柄管理]
    end
    subgraph HA[HarnessAgent：执行内核]
        H1[一次 Agent 调用的执行]
        H2[身份绑定与恢复]
        H3[Hook/Toolkit 扩展点]
    end
    subgraph DS[DistributedStore：状态]
        D1[跨调用状态]
        D2[分布式并发与快照]
    end
    Client[客户端/上游系统] --> S1
    S1 --> P1
    P1 --> S3
    S3 --> H1
    H2 --> D1
    H1 --> D1
    H1 --> D2
    H1 --> S1
    S1 --> Client
    P2 -. 判定完成 .-> H1
    classDef boundary fill:#fff,stroke:#333,stroke-width:1px; -->
![](https://cdn.nlark.com/yuque/__mermaid_v3/8065008ae9f6f2567b303c72c1fcb15c.svg)

<!-- 这是一个文本绘图，源码为：flowchart TB
    C[客户端/上游系统]
    subgraph SB[Spring Boot 接入层]
        A[HTTP/消息接入<br/>配置、部署、生命周期]
    end
    subgraph PD[产品域]
        B[产品语义<br/>业务规则、完成标准]
    end
    subgraph HA[执行内核]
        H[HarnessAgent<br/>执行一次 Agent 调用]
    end
    subgraph DS[状态层]
        D[DistributedStore<br/>跨调用状态外置]
    end
    C --> A
    A --> B
    B --> H
    H <--> D
    H --> A
    A --> C
    A -. 只做接入与工程化 .- SB
    B -. 产品语义归产品域 .- PD
    H -. 执行内核 .- HA
    D -. 状态归 DistributedStore .- DS -->
![](https://cdn.nlark.com/yuque/__mermaid_v3/195090a25fb39cb52df3b7af06cf28fd.svg)

<!-- 这是一个文本绘图，源码为：sequenceDiagram
    autonumber
    participant C as 客户端
    participant SB as Spring Boot 接入层
    participant PD as 产品域
    participant HA as HarnessAgent
    participant DS as DistributedStore
    C->>SB: 发起 Agent 请求
    SB->>PD: 解析产品语义/规则/完成标准
    PD-->>SB: 返回业务语义与约束
    SB->>HA: 创建 AgentRun 并调用执行内核
    HA->>DS: 读取跨调用状态
    DS-->>HA: 返回状态快照
    HA->>HA: 执行 Agent 推理与工具调用
    HA->>DS: 写回状态
    HA-->>SB: 返回事件流/执行结果
    SB-->>C: 响应/流式输出 -->
![](https://cdn.nlark.com/yuque/__mermaid_v3/f41565e887d8d6ba333ee5b259df0cd0.svg)

<!-- 这是一个文本绘图，源码为：sequenceDiagram
    autonumber
    participant C as 客户端
    participant SB as Spring Boot 接入层
    participant PD as 产品域
    participant HA as HarnessAgent
    participant DS as DistributedStore
    C->>SB: 发起 Agent 请求
    SB->>PD: 解析产品语义/规则/完成标准
    PD-->>SB: 返回业务语义与约束
    SB->>HA: 创建 AgentRun 并调用执行内核
    HA->>DS: 读取跨调用状态
    DS-->>HA: 返回状态快照
    HA->>HA: 执行 Agent 推理与工具调用
    HA->>DS: 写回状态
    HA-->>SB: 返回事件流/执行结果
    SB-->>C: 响应/流式输出 -->
![](https://cdn.nlark.com/yuque/__mermaid_v3/f41565e887d8d6ba333ee5b259df0cd0.svg)

## 表格
### <font style="color:rgb(15, 17, 21);">表 1：四层职责边界表（必须）</font>
<font style="color:rgb(15, 17, 21);">这是整份文档核心主张的表格化，用于评审和排障时对齐“谁负责什么、谁不负责什么”。</font>

| <font style="color:rgb(15, 17, 21);">层</font> | <font style="color:rgb(15, 17, 21);">角色</font> | <font style="color:rgb(15, 17, 21);">负责</font> | <font style="color:rgb(15, 17, 21);">明确不负责</font> | <font style="color:rgb(15, 17, 21);">典型产物</font> |
| --- | --- | --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">接入层</font> | <font style="color:rgb(15, 17, 21);">Spring Boot</font> | <font style="color:rgb(15, 17, 21);">HTTP/消息接入、配置、部署、生命周期、流量治理</font> | <font style="color:rgb(15, 17, 21);">Agent 推理、业务语义、状态持久化</font> | <font style="color:rgb(15, 17, 21);">接口、配置中心项、部署单元</font> |
| <font style="color:rgb(15, 17, 21);">产品域</font> | <font style="color:rgb(15, 17, 21);">业务服务/模块</font> | <font style="color:rgb(15, 17, 21);">产品语义、业务规则、任务完成标准</font> | <font style="color:rgb(15, 17, 21);">执行策略（压缩/重试/沙箱选择）</font> | <font style="color:rgb(15, 17, 21);">规则定义、验收标准、Prompt/工具语义</font> |
| <font style="color:rgb(15, 17, 21);">执行内核</font> | <font style="color:rgb(15, 17, 21);">HarnessAgent</font> | <font style="color:rgb(15, 17, 21);">一次 Agent 调用的执行、恢复、上下文压缩重试</font> | <font style="color:rgb(15, 17, 21);">跨调用状态持有、接入协议</font> | <font style="color:rgb(15, 17, 21);">执行结果、事件流</font> |
| <font style="color:rgb(15, 17, 21);">状态层</font> | <font style="color:rgb(15, 17, 21);">DistributedStore</font> | <font style="color:rgb(15, 17, 21);">跨调用状态外置、并发控制、快照</font> | <font style="color:rgb(15, 17, 21);">业务判断、接入协议</font> | <font style="color:rgb(15, 17, 21);">状态快照、锁、文件 KV</font> |


### <font style="color:rgb(15, 17, 21);">表 2：DistributedStore 状态清单表（必须）</font>
<font style="color:rgb(15, 17, 21);">文档明确 DistributedStore 覆盖四类状态，支持混合后端。这张表把“什么东西存在哪、按什么键寻址、生命周期多长”固定下来。</font>

| <font style="color:rgb(15, 17, 21);">状态类别</font> | <font style="color:rgb(15, 17, 21);">对应组件</font> | <font style="color:rgb(15, 17, 21);">逻辑内容</font> | <font style="color:rgb(15, 17, 21);">寻址键</font> | <font style="color:rgb(15, 17, 21);">建议后端</font> | <font style="color:rgb(15, 17, 21);">生命周期</font> |
| --- | --- | --- | --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">Agent 状态</font> | <font style="color:rgb(15, 17, 21);">AgentStateStore</font> | <font style="color:rgb(15, 17, 21);">对话上下文、压缩摘要、权限规则、Plan Mode 状态</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">(userId, sessionId)</font>` | <font style="color:rgb(15, 17, 21);">MySQL</font> | <font style="color:rgb(15, 17, 21);">会话级，可归档</font> |
| <font style="color:rgb(15, 17, 21);">工作区文件</font> | <font style="color:rgb(15, 17, 21);">BaseStore</font> | <font style="color:rgb(15, 17, 21);">工作区文件系统 KV</font> | <font style="color:rgb(15, 17, 21);">工作区/会话标识</font> | <font style="color:rgb(15, 17, 21);">MySQL 或对象存储</font> | <font style="color:rgb(15, 17, 21);">会话级</font> |
| <font style="color:rgb(15, 17, 21);">沙箱快照</font> | <font style="color:rgb(15, 17, 21);">SandboxSnapshotSpec</font> | <font style="color:rgb(15, 17, 21);">沙箱快照</font> | <font style="color:rgb(15, 17, 21);">快照 ID / 会话</font> | <font style="color:rgb(15, 17, 21);">Redis 或对象存储</font> | <font style="color:rgb(15, 17, 21);">短期，可 TTL</font> |
| <font style="color:rgb(15, 17, 21);">并发锁</font> | <font style="color:rgb(15, 17, 21);">SandboxExecutionGuard</font> | <font style="color:rgb(15, 17, 21);">执行互斥锁</font> | <font style="color:rgb(15, 17, 21);">资源/会话标识</font> | <font style="color:rgb(15, 17, 21);">Redis</font> | <font style="color:rgb(15, 17, 21);">秒级 TTL</font> |


### <font style="color:rgb(15, 17, 21);">表 3：故障归因表（必须）</font>
<font style="color:rgb(15, 17, 21);">文档分析里提到四层解耦的最大实践价值是“故障归因边界不重叠”。这张表把它变成可执行的排障路径。</font>

| <font style="color:rgb(15, 17, 21);">现象</font> | <font style="color:rgb(15, 17, 21);">优先排查层</font> | <font style="color:rgb(15, 17, 21);">关键检查点</font> | <font style="color:rgb(15, 17, 21);">常见根因</font> |
| --- | --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">请求超时/限流/连不上</font> | <font style="color:rgb(15, 17, 21);">接入层</font> | <font style="color:rgb(15, 17, 21);">网关、超时配置、线程池</font> | <font style="color:rgb(15, 17, 21);">接入配置错误、容量不足</font> |
| <font style="color:rgb(15, 17, 21);">业务结果不符合预期</font> | <font style="color:rgb(15, 17, 21);">产品域</font> | <font style="color:rgb(15, 17, 21);">规则定义、完成标准、Prompt/工具语义</font> | <font style="color:rgb(15, 17, 21);">语义定义偏差</font> |
| <font style="color:rgb(15, 17, 21);">执行中断、上下文溢出、恢复异常</font> | <font style="color:rgb(15, 17, 21);">执行内核</font> | <font style="color:rgb(15, 17, 21);">压缩重试、Hook 链、Toolkit 配置</font> | <font style="color:rgb(15, 17, 21);">执行策略配置不当</font> |
| <font style="color:rgb(15, 17, 21);">状态丢失、重复执行、并发冲突</font> | <font style="color:rgb(15, 17, 21);">状态层</font> | <font style="color:rgb(15, 17, 21);">键设计、TTL、锁粒度、后端一致性</font> | <font style="color:rgb(15, 17, 21);">状态外置不完整或锁失效</font> |


### <font style="color:rgb(15, 17, 21);">表 4：配置分层表（必须）</font>
<font style="color:rgb(15, 17, 21);">文档强调“按需启用能力”，配置必须按层归属，否则会重新耦合。</font>

| <font style="color:rgb(15, 17, 21);">配置类别</font> | <font style="color:rgb(15, 17, 21);">归属层</font> | <font style="color:rgb(15, 17, 21);">示例项</font> | <font style="color:rgb(15, 17, 21);">变更频率</font> | <font style="color:rgb(15, 17, 21);">管理方式</font> |
| --- | --- | --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">接入配置</font> | <font style="color:rgb(15, 17, 21);">Spring Boot</font> | <font style="color:rgb(15, 17, 21);">端口、超时、限流、鉴权</font> | <font style="color:rgb(15, 17, 21);">低</font> | <font style="color:rgb(15, 17, 21);">配置中心/环境变量</font> |
| <font style="color:rgb(15, 17, 21);">产品语义配置</font> | <font style="color:rgb(15, 17, 21);">产品域</font> | <font style="color:rgb(15, 17, 21);">业务规则、完成标准、工具语义</font> | <font style="color:rgb(15, 17, 21);">中</font> | <font style="color:rgb(15, 17, 21);">业务配置/数据库</font> |
| <font style="color:rgb(15, 17, 21);">执行策略配置</font> | <font style="color:rgb(15, 17, 21);">HarnessAgent</font> | <font style="color:rgb(15, 17, 21);">压缩阈值、重试策略、Hook 开关、沙箱选择</font> | <font style="color:rgb(15, 17, 21);">中</font> | <font style="color:rgb(15, 17, 21);">框架配置</font> |
| <font style="color:rgb(15, 17, 21);">状态后端配置</font> | <font style="color:rgb(15, 17, 21);">DistributedStore</font> | <font style="color:rgb(15, 17, 21);">混合后端路由、TTL、锁参数</font> | <font style="color:rgb(15, 17, 21);">低</font> | <font style="color:rgb(15, 17, 21);">部署配置</font> |


### <font style="color:rgb(15, 17, 21);">表 5：HITL / 权限审批记录表（生产必须）</font>
<font style="color:rgb(15, 17, 21);">文档提到 PermissionEngine 三态决策（允许/审批/拒绝），审批通道属于接入层，规则定义属于产品域。生产环境需要一张审批记录表来闭环。</font>

| <font style="color:rgb(15, 17, 21);">字段</font> | <font style="color:rgb(15, 17, 21);">含义</font> | <font style="color:rgb(15, 17, 21);">归属</font> |
| --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">审批 ID</font> | <font style="color:rgb(15, 17, 21);">唯一标识</font> | <font style="color:rgb(15, 17, 21);">接入层生成</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">(userId, sessionId)</font>` | <font style="color:rgb(15, 17, 21);">会话寻址</font> | <font style="color:rgb(15, 17, 21);">状态层</font> |
| <font style="color:rgb(15, 17, 21);">工具/动作</font> | <font style="color:rgb(15, 17, 21);">待审批的操作</font> | <font style="color:rgb(15, 17, 21);">产品域定义</font> |
| <font style="color:rgb(15, 17, 21);">决策</font> | <font style="color:rgb(15, 17, 21);">允许/审批/拒绝</font> | <font style="color:rgb(15, 17, 21);">PermissionEngine</font> |
| <font style="color:rgb(15, 17, 21);">审批人/通道</font> | <font style="color:rgb(15, 17, 21);">谁批的、经什么通道</font> | <font style="color:rgb(15, 17, 21);">接入层</font> |
| <font style="color:rgb(15, 17, 21);">时间戳</font> | <font style="color:rgb(15, 17, 21);">决策时间</font> | <font style="color:rgb(15, 17, 21);">接入层</font> |
| <font style="color:rgb(15, 17, 21);">结果回写</font> | <font style="color:rgb(15, 17, 21);">批准后是否已执行</font> | <font style="color:rgb(15, 17, 21);">执行内核回写</font> |


### <font style="color:rgb(15, 17, 21);">表 6：可观测性事件分类表（生产必须）</font>


| <font style="color:rgb(15, 17, 21);">事件类别</font> | <font style="color:rgb(15, 17, 21);">产生方</font> | <font style="color:rgb(15, 17, 21);">采集方</font> | <font style="color:rgb(15, 17, 21);">去向</font> | <font style="color:rgb(15, 17, 21);">用途</font> |
| --- | --- | --- | --- | --- |
| <font style="color:rgb(15, 17, 21);">接入事件</font> | <font style="color:rgb(15, 17, 21);">Spring Boot</font> | <font style="color:rgb(15, 17, 21);">接入层</font> | <font style="color:rgb(15, 17, 21);">日志/APM</font> | <font style="color:rgb(15, 17, 21);">流量、延迟、错误率</font> |
| <font style="color:rgb(15, 17, 21);">执行事件</font> | <font style="color:rgb(15, 17, 21);">HarnessAgent</font> | <font style="color:rgb(15, 17, 21);">接入层转发或框架侧导出</font> | <font style="color:rgb(15, 17, 21);">Trace/事件流</font> | <font style="color:rgb(15, 17, 21);">执行链路、压缩重试</font> |
| <font style="color:rgb(15, 17, 21);">状态事件</font> | <font style="color:rgb(15, 17, 21);">DistributedStore</font> | <font style="color:rgb(15, 17, 21);">状态层</font> | <font style="color:rgb(15, 17, 21);">日志/指标</font> | <font style="color:rgb(15, 17, 21);">读写延迟、锁竞争</font> |
| <font style="color:rgb(15, 17, 21);">审批事件</font> | <font style="color:rgb(15, 17, 21);">PermissionEngine</font> | <font style="color:rgb(15, 17, 21);">接入层</font> | <font style="color:rgb(15, 17, 21);">审计库</font> | <font style="color:rgb(15, 17, 21);">合规审计</font> |


## <font style="color:rgb(15, 17, 21);">数据表</font>
### <font style="color:rgb(15, 17, 21);">通用约定</font>
+ <font style="color:rgb(15, 17, 21);">字符集 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">utf8mb4</font>`<font style="color:rgb(15, 17, 21);">，引擎 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">InnoDB</font>`<font style="color:rgb(15, 17, 21);">，时间统一存 UTC 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DATETIME(3)</font>`<font style="color:rgb(15, 17, 21);">。</font>
+ <font style="color:rgb(15, 17, 21);">不建外键，跨表一致性由应用层保证。</font>
+ <font style="color:rgb(15, 17, 21);">主键用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">BIGINT UNSIGNED AUTO_INCREMENT</font>`<font style="color:rgb(15, 17, 21);">；分布式环境可替换为雪花 ID 或 UUID。</font>
+ <font style="color:rgb(15, 17, 21);">大文本用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">LONGTEXT</font>`<font style="color:rgb(15, 17, 21);">，结构化扩展用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">JSON</font>`<font style="color:rgb(15, 17, 21);">；注意调大 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">max_allowed_packet</font>`<font style="color:rgb(15, 17, 21);">。</font>
+ <font style="color:rgb(15, 17, 21);">MySQL 无原生 TTL，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">expires_at</font>`<font style="color:rgb(15, 17, 21);"> 需配定时清理任务，或对审计表按时间分区。</font>

```sql
-- ============================================================
-- AgentScope Java 2.0.4 工程落地参考 DDL（MySQL 8.0+）
-- 说明：非官方 schema，按文档核心主张推导，字段可按实现调整
-- ============================================================

CREATE DATABASE IF NOT EXISTS agentscope
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE agentscope;

-- ------------------------------------------------------------
-- 1. AgentStateStore 主表：按 (user_id, session_id) 寻址
--    承载 Plan Mode 状态、权限规则、压缩摘要引用等
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_session_state (
  id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id             VARCHAR(128)    NOT NULL COMMENT '用户标识',
  session_id          VARCHAR(128)    NOT NULL COMMENT '会话标识',
  status              VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE'
  COMMENT 'ACTIVE / COMPRESSED / ARCHIVED / EXPIRED',
  plan_mode_state     JSON            NULL COMMENT 'Plan Mode 状态快照',
  permission_rules    JSON            NULL COMMENT '权限规则快照',
  compression_summary TEXT            NULL COMMENT '最近一次压缩摘要（冗余，便于快速恢复）',
  metadata            JSON            NULL COMMENT '扩展元数据',
  version             BIGINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '乐观锁版本号',
  expires_at          DATETIME(3)     NULL COMMENT '过期时间，NULL 表示不过期',
  created_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at          DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
  ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_session (user_id, session_id),
  KEY idx_status_expires (status, expires_at),
  KEY idx_updated_at (updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='AgentStateStore：会话级 Agent 状态';

-- ------------------------------------------------------------
-- 2. 对话上下文：按 (user_id, session_id, seq) 顺序存放
--    对应文档中 AgentStateStore 的“对话上下文”
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_message (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       VARCHAR(128)    NOT NULL,
  session_id    VARCHAR(128)    NOT NULL,
  seq           BIGINT UNSIGNED NOT NULL COMMENT '会话内单调递增序号',
  role          VARCHAR(32)     NOT NULL COMMENT 'system/user/assistant/tool',
  content       LONGTEXT        NULL COMMENT '消息正文',
  tool_calls    JSON            NULL COMMENT '工具调用列表',
  tool_call_id  VARCHAR(128)    NULL COMMENT '工具调用回执关联 ID',
  token_count   INT UNSIGNED    NULL COMMENT '该消息 token 估算',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_session_seq (user_id, session_id, seq),
  KEY idx_session_created (user_id, session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='AgentStateStore：对话上下文消息';

-- ------------------------------------------------------------
-- 3. 压缩摘要历史：记录压缩覆盖的消息序号范围
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_compression_summary (
  id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id           VARCHAR(128)    NOT NULL,
  session_id        VARCHAR(128)    NOT NULL,
  seq_start         BIGINT UNSIGNED NOT NULL COMMENT '被压缩消息起始 seq',
  seq_end           BIGINT UNSIGNED NOT NULL COMMENT '被压缩消息结束 seq',
  summary           LONGTEXT        NOT NULL COMMENT '压缩摘要正文',
  token_count       INT UNSIGNED    NULL,
  strategy          VARCHAR(64)     NULL COMMENT '压缩策略标识',
  created_at        DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_session_range (user_id, session_id, seq_start, seq_end)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='AgentStateStore：上下文压缩摘要历史';

-- ------------------------------------------------------------
-- 4. BaseStore：工作区文件 KV
--    对应文档中 DistributedStore 的“工作区文件系统 KV”
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS workspace_file (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  workspace_id  VARCHAR(128)    NOT NULL COMMENT '工作区标识',
  file_path     VARCHAR(1024)   NOT NULL COMMENT '工作区内相对路径',
  content       LONGBLOB        NULL COMMENT '文件内容（文本/二进制）',
  content_type  VARCHAR(128)    NULL,
  size_bytes    BIGINT UNSIGNED NOT NULL DEFAULT 0,
  checksum      CHAR(64)        NULL COMMENT 'SHA-256',
  version       BIGINT UNSIGNED NOT NULL DEFAULT 1,
  expires_at    DATETIME(3)     NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_workspace_path (workspace_id, file_path(255)),
  KEY idx_workspace_updated (workspace_id, updated_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='BaseStore：工作区文件 KV';

-- ------------------------------------------------------------
-- 5. SandboxExecutionGuard 的 MySQL 兜底实现
--    生产建议按文档用 Redis；此表仅用于单机/测试或 Redis 不可用时兜底
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_execution_lock (
  lock_key      VARCHAR(255)    NOT NULL COMMENT '锁资源标识',
  owner_id      VARCHAR(128)    NOT NULL COMMENT '持有者实例标识',
  acquired_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  expires_at    DATETIME(3)     NOT NULL COMMENT '锁过期时间',
  PRIMARY KEY (lock_key),
  KEY idx_expires (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='SandboxExecutionGuard：执行互斥锁（MySQL 兜底）';

-- ------------------------------------------------------------
-- 6. HITL / 权限审批记录
--    对应前面表 5；审批通道在接入层，规则在产品域
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_record (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  approval_id   VARCHAR(64)     NOT NULL COMMENT '审批唯一标识',
  user_id       VARCHAR(128)    NOT NULL,
  session_id    VARCHAR(128)    NOT NULL,
  run_id        VARCHAR(128)    NULL COMMENT 'AgentRun 标识',
  tool_name     VARCHAR(128)    NULL COMMENT '待审批工具/动作',
  action        VARCHAR(256)    NULL COMMENT '动作描述或参数摘要',
  decision      VARCHAR(32)     NOT NULL DEFAULT 'PENDING'
                COMMENT 'PENDING / APPROVED / REJECTED / EXPIRED',
  approver      VARCHAR(128)    NULL COMMENT '审批人',
  channel       VARCHAR(64)     NULL COMMENT '审批通道：API/IM/控制台',
  reason        VARCHAR(1024)   NULL COMMENT '审批意见',
  requested_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  decided_at    DATETIME(3)     NULL,
  executed_at   DATETIME(3)     NULL COMMENT '批准后实际执行时间',
  result        JSON            NULL COMMENT '执行结果回写',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_approval_id (approval_id),
  KEY idx_session (user_id, session_id),
  KEY idx_decision_requested (decision, requested_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='HITL：权限审批记录';

-- ------------------------------------------------------------
-- 7. 可观测性审计事件（可选，生产建议）
--    对应前面表 6；大表建议按 created_at 做 RANGE 分区
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_event_audit (
  id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  event_id    VARCHAR(64)     NOT NULL COMMENT '事件唯一标识',
  event_type  VARCHAR(64)     NOT NULL COMMENT 'AgentEvent 类型',
  user_id     VARCHAR(128)    NULL,
  session_id  VARCHAR(128)    NULL,
  run_id      VARCHAR(128)    NULL,
  source      VARCHAR(32)     NOT NULL COMMENT 'ACCESS/EXECUTOR/STORE/PERMISSION',
  payload     JSON            NULL COMMENT '事件载荷',
  created_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_event_id (event_id),
  KEY idx_session_created (user_id, session_id, created_at),
  KEY idx_type_created (event_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='可观测性：Agent 事件审计（可按时间分区）';

-- ------------------------------------------------------------
-- 8. 产品域语义配置（可选）
--    对应前面表 4 中“产品语义配置”；接入/执行配置不建议入库
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product_semantic_config (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  config_key    VARCHAR(128)    NOT NULL COMMENT '配置键',
  config_value  JSON            NOT NULL COMMENT '配置值',
  version       BIGINT UNSIGNED NOT NULL DEFAULT 1,
  enabled       TINYINT(1)      NOT NULL DEFAULT 1,
  updated_by    VARCHAR(128)    NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
  COMMENT='产品域：产品语义配置';
```

## 业务表
### <font style="color:rgb(15, 17, 21);">A. 租户与配额（产品域 + 接入层）</font>
```sql
-- ------------------------------------------------------------
-- 1. 租户：多租户隔离的根
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenant (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id    VARCHAR(64)     NOT NULL COMMENT '租户业务标识',
  name         VARCHAR(128)    NOT NULL COMMENT '租户名称',
  plan         VARCHAR(64)     NOT NULL DEFAULT 'standard' COMMENT '套餐/版本',
  status       VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE'
               COMMENT 'ACTIVE / SUSPENDED / CLOSED',
  settings     JSON            NULL COMMENT '租户级扩展配置',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
               ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_id (tenant_id),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：租户';

-- ------------------------------------------------------------
-- 2. 租户用户： (userId, sessionId) 寻址前的身份与角色
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenant_user (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id    VARCHAR(64)     NOT NULL,
  user_id      VARCHAR(128)    NOT NULL COMMENT '用户业务标识，与 AgentStateStore 的 userId 一致',
  display_name VARCHAR(128)    NULL,
  roles        JSON            NULL COMMENT '角色列表',
  status       VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE'
               COMMENT 'ACTIVE / DISABLED',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
               ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_user (tenant_id, user_id),
  KEY idx_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：租户用户与角色';

-- ------------------------------------------------------------
-- 3. 租户配额：限流与计费的依据
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tenant_quota (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id    VARCHAR(64)     NOT NULL,
  quota_type   VARCHAR(64)     NOT NULL COMMENT 'REQUESTS / TOKENS / COST / CONCURRENT_RUNS',
  period       VARCHAR(32)     NOT NULL DEFAULT 'MONTHLY'
               COMMENT 'DAILY / MONTHLY / TOTAL',
  limit_value  BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '上限',
  used_value   BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '当前周期已用量',
  reset_at     DATETIME(3)     NULL COMMENT '下次重置时间',
  enabled      TINYINT(1)      NOT NULL DEFAULT 1,
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
               ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_quota (tenant_id, quota_type, period),
  KEY idx_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入层：租户配额';

-- ------------------------------------------------------------
-- 4. 配额消耗流水：扣减审计与对账
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS quota_usage_log (
  id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id    VARCHAR(64)     NOT NULL,
  user_id      VARCHAR(128)    NULL,
  quota_type   VARCHAR(64)     NOT NULL,
  amount       BIGINT          NOT NULL COMMENT '本次消耗量，可为负表示回补',
  run_id       VARCHAR(128)    NULL COMMENT '关联的 AgentRun',
  reason       VARCHAR(128)    NULL COMMENT '扣减原因',
  created_at   DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_tenant_time (tenant_id, created_at),
  KEY idx_run (run_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入层：配额消耗流水';
```

### <font style="color:rgb(15, 17, 21);">B. 工具、技能目录与权限规则（产品域）</font>
```sql
-- ------------------------------------------------------------
-- 5. 工具目录：Toolkit 注册清单的持久化来源
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tool_registry (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tool_id       VARCHAR(128)    NOT NULL COMMENT '工具业务标识',
  name          VARCHAR(128)    NOT NULL,
  version       VARCHAR(32)     NOT NULL DEFAULT '1.0.0',
  module        VARCHAR(64)     NULL COMMENT '所属模块/领域',
  param_schema  JSON            NULL COMMENT '参数 JSON Schema',
  description   TEXT            NULL,
  enabled       TINYINT(1)      NOT NULL DEFAULT 1,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tool_id (tool_id),
  KEY idx_module_enabled (module, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：工具目录';

-- ------------------------------------------------------------
-- 6. 工具权限规则：PermissionEngine 的规则来源（不是决策记录）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tool_permission (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tool_id       VARCHAR(128)    NOT NULL,
  tenant_id     VARCHAR(64)     NOT NULL DEFAULT '*' COMMENT '*' 表示全局',
  role_id       VARCHAR(64)     NOT NULL DEFAULT '*' COMMENT '角色，* 表示所有角色',
  decision      VARCHAR(32)     NOT NULL DEFAULT 'ASK'
                COMMENT 'ALLOW / ASK / DENY',
  conditions    JSON            NULL COMMENT '条件表达式，如参数范围、时间窗',
  priority      INT             NOT NULL DEFAULT 100 COMMENT '数值越小优先级越高',
  enabled       TINYINT(1)      NOT NULL DEFAULT 1,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_rule (tool_id, tenant_id, role_id),
  KEY idx_tool (tool_id),
  KEY idx_tenant_role (tenant_id, role_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：工具权限规则';

-- ------------------------------------------------------------
-- 7. 技能目录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS skill_registry (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  skill_id      VARCHAR(128)    NOT NULL,
  name          VARCHAR(128)    NOT NULL,
  version       VARCHAR(32)     NOT NULL DEFAULT '1.0.0',
  description   TEXT            NULL,
  entry_point   VARCHAR(512)    NULL COMMENT '技能入口/资源路径',
  enabled       TINYINT(1)      NOT NULL DEFAULT 1,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_skill_id (skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：技能目录';

-- ------------------------------------------------------------
-- 8. 工具版本历史：审计回答“当时用的是哪版”
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS tool_version_history (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tool_id       VARCHAR(128)    NOT NULL,
  version       VARCHAR(32)     NOT NULL,
  change_note   TEXT            NULL,
  param_schema  JSON            NULL COMMENT '该版本参数快照',
  effective_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tool_version (tool_id, version),
  KEY idx_effective (effective_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：工具版本历史';
```

### <font style="color:rgb(15, 17, 21);">C. 运行记录与会话元数据（接入层 + 产品域）</font>
```sql
-- ------------------------------------------------------------
-- 9. AgentRun：2.0.4 显式运行时句柄的持久化
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_run (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id        VARCHAR(128)    NOT NULL COMMENT '2.0.4 AgentRun 标识',
  tenant_id     VARCHAR(64)     NOT NULL DEFAULT '',
  user_id       VARCHAR(128)    NOT NULL,
  session_id    VARCHAR(128)    NOT NULL,
  trigger_type  VARCHAR(32)     NOT NULL DEFAULT 'USER'
                COMMENT 'USER / SCHEDULE / API / RETRY',
  status        VARCHAR(32)     NOT NULL DEFAULT 'PENDING'
                COMMENT 'PENDING / RUNNING / SUCCEEDED / FAILED / CANCELLED / TIMEOUT',
  model         VARCHAR(128)    NULL COMMENT '本次使用的模型',
  input_summary TEXT            NULL COMMENT '输入摘要，不存全文',
  output_summary TEXT           NULL,
  error_code    VARCHAR(64)     NULL,
  error_message VARCHAR(1024)   NULL,
  started_at    DATETIME(3)     NULL,
  finished_at   DATETIME(3)     NULL,
  duration_ms   BIGINT UNSIGNED NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_run_id (run_id),
  KEY idx_user_session (user_id, session_id, created_at),
  KEY idx_status_created (status, created_at),
  KEY idx_tenant_created (tenant_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入层：Agent 运行记录';

-- ------------------------------------------------------------
-- 10. 会话元数据：会话列表、标题、渠道（区别于 agent_session_state）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_session_meta (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       VARCHAR(128)    NOT NULL,
  session_id    VARCHAR(128)    NOT NULL,
  tenant_id     VARCHAR(64)     NOT NULL DEFAULT '',
  title         VARCHAR(256)    NULL COMMENT '会话标题',
  channel       VARCHAR(64)     NULL COMMENT 'WEB / API / IM / WECHAT',
  status        VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE'
                COMMENT 'ACTIVE / ARCHIVED / DELETED',
  last_run_id   VARCHAR(128)    NULL,
  last_active_at DATETIME(3)    NULL,
  message_count BIGINT UNSIGNED NOT NULL DEFAULT 0,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_session (user_id, session_id),
  KEY idx_user_active (user_id, last_active_at),
  KEY idx_tenant_status (tenant_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：会话元数据';

-- ------------------------------------------------------------
-- 11. 运行步骤：细粒度排障与性能分析
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_run_step (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id        VARCHAR(128)    NOT NULL,
  step_no       INT UNSIGNED    NOT NULL COMMENT 'run 内单调递增',
  step_type     VARCHAR(32)     NOT NULL
                COMMENT 'REASONING / TOOL / COMPACTION / APPROVAL / RETRY',
  tool_id       VARCHAR(128)    NULL,
  status        VARCHAR(32)     NOT NULL DEFAULT 'SUCCEEDED'
                COMMENT 'SUCCEEDED / FAILED / SKIPPED',
  input_summary JSON            NULL,
  output_summary JSON           NULL,
  token_input   INT UNSIGNED    NULL,
  token_output  INT UNSIGNED    NULL,
  duration_ms   BIGINT UNSIGNED NULL,
  error_message VARCHAR(1024)   NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_run_step (run_id, step_no),
  KEY idx_run (run_id),
  KEY idx_type_created (step_type, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入层：Agent 运行步骤';
```

### <font style="color:rgb(15, 17, 21);">D. 用量与计费（接入层 + 产品域）</font>
```sql
-- ------------------------------------------------------------
-- 12. Token 用量：按 run 的计费原始凭证
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS token_usage (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id        VARCHAR(128)    NOT NULL,
  tenant_id     VARCHAR(64)     NOT NULL DEFAULT '',
  user_id       VARCHAR(128)    NOT NULL,
  session_id    VARCHAR(128)    NULL,
  model         VARCHAR(128)    NOT NULL,
  input_tokens  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  output_tokens BIGINT UNSIGNED NOT NULL DEFAULT 0,
  total_tokens  BIGINT UNSIGNED NOT NULL DEFAULT 0,
  cost_amount   DECIMAL(14,6)   NOT NULL DEFAULT 0 COMMENT '按 model_price 计算',
  currency      CHAR(3)         NOT NULL DEFAULT 'CNY',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_run (run_id),
  KEY idx_tenant_time (tenant_id, created_at),
  KEY idx_model_time (model, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入层：Token 用量';

-- ------------------------------------------------------------
-- 13. 模型价格表：成本计算按生效时间取价
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS model_price (
  id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  model           VARCHAR(128)    NOT NULL,
  currency        CHAR(3)         NOT NULL DEFAULT 'CNY',
  input_per_1k    DECIMAL(12,6)   NOT NULL DEFAULT 0 COMMENT '每千输入 token 单价',
  output_per_1k   DECIMAL(12,6)   NOT NULL DEFAULT 0 COMMENT '每千输出 token 单价',
  effective_from  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  effective_to    DATETIME(3)     NULL COMMENT 'NULL 表示当前有效',
  created_at      DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_model_effective (model, effective_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：模型价格';

-- ------------------------------------------------------------
-- 14. 计费汇总：对账与开票
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS billing_record (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  tenant_id     VARCHAR(64)     NOT NULL,
  period_start  DATETIME(3)     NOT NULL,
  period_end    DATETIME(3)     NOT NULL,
  token_total   BIGINT UNSIGNED NOT NULL DEFAULT 0,
  cost_amount   DECIMAL(14,2)   NOT NULL DEFAULT 0,
  currency      CHAR(3)         NOT NULL DEFAULT 'CNY',
  status        VARCHAR(32)     NOT NULL DEFAULT 'DRAFT'
                COMMENT 'DRAFT / CONFIRMED / INVOICED / PAID',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_tenant_period (tenant_id, period_start, period_end),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：计费汇总';
```

### <font style="color:rgb(15, 17, 21);">E. 长时任务与调度（接入层）</font>
```sql
-- ------------------------------------------------------------
-- 15. Agent 任务：异步/后台执行的状态持久化
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_task (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  task_id       VARCHAR(128)    NOT NULL,
  run_id        VARCHAR(128)    NULL COMMENT '关联的 AgentRun',
  tenant_id     VARCHAR(64)     NOT NULL DEFAULT '',
  user_id       VARCHAR(128)    NOT NULL,
  session_id    VARCHAR(128)    NULL,
  task_type     VARCHAR(64)     NOT NULL COMMENT 'BACKFILL / REPORT / BATCH 等',
  status        VARCHAR(32)     NOT NULL DEFAULT 'PENDING'
                COMMENT 'PENDING / SCHEDULED / RUNNING / SUCCEEDED / FAILED / CANCELLED',
  payload       JSON            NULL COMMENT '任务输入',
  result_ref    VARCHAR(512)    NULL COMMENT '结果存储引用',
  retry_count   INT UNSIGNED    NOT NULL DEFAULT 0,
  max_retries   INT UNSIGNED    NOT NULL DEFAULT 3,
  next_run_at   DATETIME(3)     NULL,
  error_message VARCHAR(1024)   NULL,
  started_at    DATETIME(3)     NULL,
  finished_at   DATETIME(3)     NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_task_id (task_id),
  KEY idx_status_next (status, next_run_at),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入层：Agent 任务';

-- ------------------------------------------------------------
-- 16. 任务调度：cron/延迟任务的调度来源
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS agent_task_schedule (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  schedule_id   VARCHAR(128)    NOT NULL,
  task_type     VARCHAR(64)     NOT NULL,
  cron_expr     VARCHAR(128)    NULL COMMENT 'cron 表达式，延迟任务为空',
  delay_seconds INT UNSIGNED    NULL COMMENT '延迟秒数，cron 任务为空',
  payload       JSON            NULL COMMENT '任务模板输入',
  enabled       TINYINT(1)      NOT NULL DEFAULT 1,
  next_fire_at  DATETIME(3)     NULL,
  last_fire_at  DATETIME(3)     NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_schedule_id (schedule_id),
  KEY idx_enabled_next (enabled, next_fire_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入层：任务调度';
```

### <font style="color:rgb(15, 17, 21);">F. HITL 通知与待办（接入层 + 产品域）</font>
```sql
-- ------------------------------------------------------------
-- 17. 审批通知：approval_record 只记结果，这里记“推没推出去”
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS approval_notification (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  approval_id   VARCHAR(64)     NOT NULL COMMENT '关联 approval_record.approval_id',
  receiver_id   VARCHAR(128)    NOT NULL COMMENT '接收人 user_id',
  channel       VARCHAR(64)     NOT NULL COMMENT 'IM / EMAIL / WEB / SMS',
  send_status   VARCHAR(32)     NOT NULL DEFAULT 'PENDING'
                COMMENT 'PENDING / SENT / FAILED',
  retry_count   INT UNSIGNED    NOT NULL DEFAULT 0,
  last_error    VARCHAR(1024)   NULL,
  sent_at       DATETIME(3)     NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_approval (approval_id),
  KEY idx_receiver_status (receiver_id, send_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入层：审批通知发送记录';

-- ------------------------------------------------------------
-- 18. 用户待办：产品侧待办中心
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_todo (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  user_id       VARCHAR(128)    NOT NULL,
  todo_type     VARCHAR(32)     NOT NULL COMMENT 'APPROVAL / CONFIRM / TASK',
  ref_id        VARCHAR(128)    NOT NULL COMMENT '关联 approval_id / run_id / task_id',
  title         VARCHAR(256)    NULL,
  status        VARCHAR(32)     NOT NULL DEFAULT 'OPEN'
                COMMENT 'OPEN / DONE / EXPIRED / CANCELLED',
  due_at        DATETIME(3)     NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_user_ref (user_id, todo_type, ref_id),
  KEY idx_user_status (user_id, status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：用户待办';
```

### <font style="color:rgb(15, 17, 21);">G. 附件与输入素材（产品域）</font>
```sql
-- ------------------------------------------------------------
-- 19. 附件：用户资产，生命周期跟用户（区别于 workspace_file）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attachment (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  attachment_id VARCHAR(64)     NOT NULL,
  tenant_id     VARCHAR(64)     NOT NULL DEFAULT '',
  user_id       VARCHAR(128)    NOT NULL,
  session_id    VARCHAR(128)    NULL,
  file_name     VARCHAR(512)    NOT NULL,
  storage_type  VARCHAR(32)     NOT NULL DEFAULT 'LOCAL'
                COMMENT 'LOCAL / S3 / OSS / MINIO',
  storage_path  VARCHAR(1024)   NOT NULL,
  mime_type     VARCHAR(128)    NULL,
  size_bytes    BIGINT UNSIGNED NOT NULL DEFAULT 0,
  checksum      CHAR(64)        NULL COMMENT 'SHA-256',
  status        VARCHAR(32)     NOT NULL DEFAULT 'ACTIVE'
                COMMENT 'ACTIVE / DELETED',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_attachment_id (attachment_id),
  KEY idx_user_created (user_id, created_at),
  KEY idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：用户附件';

-- ------------------------------------------------------------
-- 20. 附件引用：附件与消息/运行的关联
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS attachment_ref (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  attachment_id VARCHAR(64)     NOT NULL,
  ref_type      VARCHAR(32)     NOT NULL COMMENT 'MESSAGE / RUN',
  ref_id        VARCHAR(128)    NOT NULL COMMENT 'message_id 或 run_id',
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_attachment_ref (attachment_id, ref_type, ref_id),
  KEY idx_ref (ref_type, ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：附件引用';
```

### <font style="color:rgb(15, 17, 21);">H. 反馈与评测（产品域）</font>
```sql
-- ------------------------------------------------------------
-- 21. 用户反馈
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS user_feedback (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  run_id        VARCHAR(128)    NULL,
  message_id    BIGINT UNSIGNED NULL COMMENT '关联 agent_message.id',
  user_id       VARCHAR(128)    NOT NULL,
  rating        TINYINT         NULL COMMENT '1-5 或 -1/1',
  labels        JSON            NULL COMMENT '反馈标签',
  comment       TEXT            NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_run (run_id),
  KEY idx_user_created (user_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：用户反馈';

-- ------------------------------------------------------------
-- 22. 评测数据集
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS eval_dataset (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dataset_id    VARCHAR(128)    NOT NULL,
  name          VARCHAR(256)    NOT NULL,
  version       VARCHAR(32)     NOT NULL DEFAULT '1.0.0',
  description   TEXT            NULL,
  enabled       TINYINT(1)      NOT NULL DEFAULT 1,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dataset_version (dataset_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：评测数据集';

-- ------------------------------------------------------------
-- 23. 评测用例
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS eval_case (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  dataset_id    VARCHAR(128)    NOT NULL,
  version       VARCHAR(32)     NOT NULL,
  case_no       INT UNSIGNED    NOT NULL,
  input_text    LONGTEXT        NOT NULL,
  expected_text LONGTEXT        NULL,
  labels        JSON            NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_dataset_case (dataset_id, version, case_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：评测用例';

-- ------------------------------------------------------------
-- 24. 评测执行记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS eval_run (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  eval_run_id   VARCHAR(128)    NOT NULL,
  dataset_id    VARCHAR(128)    NOT NULL,
  dataset_version VARCHAR(32)   NOT NULL,
  model         VARCHAR(128)    NULL,
  prompt_version VARCHAR(32)    NULL,
  total_cases   INT UNSIGNED    NOT NULL DEFAULT 0,
  passed_cases  INT UNSIGNED    NOT NULL DEFAULT 0,
  pass_rate     DECIMAL(6,4)    NULL,
  status        VARCHAR(32)     NOT NULL DEFAULT 'RUNNING'
                COMMENT 'RUNNING / SUCCEEDED / FAILED',
  detail_ref    VARCHAR(512)    NULL COMMENT '详细结果存储引用',
  started_at    DATETIME(3)     NULL,
  finished_at   DATETIME(3)     NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_eval_run_id (eval_run_id),
  KEY idx_dataset (dataset_id, dataset_version),
  KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：评测执行记录';
```

### <font style="color:rgb(15, 17, 21);">I. Prompt 与模型配置版本（产品域 + 执行内核配置）</font>
```sql
-- ------------------------------------------------------------
-- 25. Prompt 模板：产品语义的载体，独立版本管理
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS prompt_template (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  template_id   VARCHAR(128)    NOT NULL,
  scene         VARCHAR(64)     NOT NULL COMMENT '场景标识',
  version       VARCHAR(32)     NOT NULL DEFAULT '1.0.0',
  content       LONGTEXT        NOT NULL,
  variables     JSON            NULL COMMENT '变量声明',
  enabled       TINYINT(1)      NOT NULL DEFAULT 1,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_template_version (template_id, version),
  KEY idx_scene_enabled (scene, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：Prompt 模板';

-- ------------------------------------------------------------
-- 26. 模型配置：执行策略的持久化来源，支持灰度
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS model_config (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  config_id     VARCHAR(128)    NOT NULL,
  model         VARCHAR(128)    NOT NULL,
  provider      VARCHAR(64)     NOT NULL,
  params        JSON            NULL COMMENT 'temperature、max_tokens 等',
  version       VARCHAR(32)     NOT NULL DEFAULT '1.0.0',
  tenant_scope  VARCHAR(64)     NOT NULL DEFAULT '*' COMMENT '*' 表示全局',
  enabled       TINYINT(1)      NOT NULL DEFAULT 1,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_config_version (config_id, version),
  KEY idx_tenant_enabled (tenant_scope, enabled)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='执行内核配置：模型配置';
```

### <font style="color:rgb(15, 17, 21);">J. 数据保留、删除与操作审计（产品域 + 接入层）</font>
```sql
-- ------------------------------------------------------------
-- 27. 数据保留策略
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS data_retention_policy (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  data_type     VARCHAR(64)     NOT NULL COMMENT 'SESSION / MESSAGE / EVENT / ATTACHMENT',
  scope         VARCHAR(64)     NOT NULL DEFAULT '*' COMMENT '租户或 *',
  retain_days   INT UNSIGNED    NOT NULL,
  action        VARCHAR(32)     NOT NULL DEFAULT 'ARCHIVE'
                COMMENT 'ARCHIVE / DELETE / ANONYMIZE',
  enabled       TINYINT(1)      NOT NULL DEFAULT 1,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  updated_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  UNIQUE KEY uk_type_scope (data_type, scope)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：数据保留策略';

-- ------------------------------------------------------------
-- 28. 数据删除请求：用户删除权的可追溯记录
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS data_deletion_request (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  request_id    VARCHAR(64)     NOT NULL,
  user_id       VARCHAR(128)    NOT NULL,
  tenant_id     VARCHAR(64)     NOT NULL DEFAULT '',
  data_type     VARCHAR(64)     NOT NULL COMMENT 'SESSION / ATTACHMENT / ALL',
  status        VARCHAR(32)     NOT NULL DEFAULT 'PENDING'
                COMMENT 'PENDING / APPROVED / EXECUTING / DONE / REJECTED',
  reason        VARCHAR(1024)   NULL,
  requested_at  DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  executed_at   DATETIME(3)     NULL,
  result        JSON            NULL COMMENT '删除结果统计',
  PRIMARY KEY (id),
  UNIQUE KEY uk_request_id (request_id),
  KEY idx_user_status (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品域：数据删除请求';

-- ------------------------------------------------------------
-- 29. 操作审计：人在后台的操作，区别于 agent_event_audit
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS operation_audit (
  id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
  operator_id   VARCHAR(128)    NOT NULL,
  tenant_id     VARCHAR(64)     NOT NULL DEFAULT '',
  operation     VARCHAR(128)    NOT NULL COMMENT 'CREATE/UPDATE/DELETE/LOGIN 等',
  object_type   VARCHAR(64)     NOT NULL,
  object_id     VARCHAR(128)    NULL,
  before_value  JSON            NULL,
  after_value   JSON            NULL,
  ip            VARCHAR(64)     NULL,
  user_agent    VARCHAR(512)    NULL,
  created_at    DATETIME(3)     NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (id),
  KEY idx_operator_time (operator_id, created_at),
  KEY idx_object (object_type, object_id),
  KEY idx_created (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='接入层：后台操作审计';
```

## 属性配置
### <font style="color:rgb(15, 17, 21);">配置归属总表：什么进 Nacos，什么不进</font>
| <font style="color:rgb(15, 17, 21);">层</font> | <font style="color:rgb(15, 17, 21);">配置项</font> | <font style="color:rgb(15, 17, 21);">推荐存放位置</font> | <font style="color:rgb(15, 17, 21);">理由</font> |
| --- | --- | --- | --- |
| **<font style="color:rgb(15, 17, 21);">接入层</font>** | <font style="color:rgb(15, 17, 21);">端口、超时、限流、AG-UI 路径、CORS</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">application.yml</font>`<br/><font style="color:rgb(15, 17, 21);"> + Nacos </font>**<font style="color:rgb(15, 17, 21);">共享配置</font>**<font style="color:rgb(15, 17, 21);">（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">shared-configs</font>`<br/><font style="color:rgb(15, 17, 21);">）</font> | <font style="color:rgb(15, 17, 21);">部署级配置，变更频率低，但多副本需要一致</font> |
| **<font style="color:rgb(15, 17, 21);">产品域</font>** | <font style="color:rgb(15, 17, 21);">Prompt 模板、Skill 版本/标签、工具权限规则</font> | **<font style="color:rgb(15, 17, 21);">Nacos 专属配置</font>**<font style="color:rgb(15, 17, 21);">（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">extension-configs</font>`<br/><font style="color:rgb(15, 17, 21);">）</font> | <font style="color:rgb(15, 17, 21);">需要热更新，产品运营独立维护，不跟代码发布绑定</font> |
| **<font style="color:rgb(15, 17, 21);">执行内核</font>** | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">compaction</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">maxIters</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">model</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">fallbackModel</font>`<br/><font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">toolExecutionConfig</font>` | `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">application.yml</font>**`<br/>**<font style="color:rgb(15, 17, 21);"> 为主，Builder 兜底</font>** | <font style="color:rgb(15, 17, 21);">属于代码级策略，需要类型安全，不应在 Nacos 上裸改</font> |
| **<font style="color:rgb(15, 17, 21);">状态层</font>** | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<br/><font style="color:rgb(15, 17, 21);"> 混合后端装配</font> | `**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">application.yml</font>**`<br/>**<font style="color:rgb(15, 17, 21);"> + Java </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@Bean</font>**`<br/>**<font style="color:rgb(15, 17, 21);"> 装配</font>** | <font style="color:rgb(15, 17, 21);">涉及 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DataSource</font>`<br/><font style="color:rgb(15, 17, 21);">/</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Jedis</font>`<br/><font style="color:rgb(15, 17, 21);"> 注入，必须走 Spring 容器</font> |


**<font style="color:rgb(15, 17, 21);">核心原则</font>**<font style="color:rgb(15, 17, 21);">：Nacos 管“产品可运营的语义”，Spring Boot 管“工程可部署的装配”，Builder 管“执行内核的类型化策略”。三者不要混用。</font>

### <font style="color:rgb(15, 17, 21);">接入层：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">application.yml</font>`<font style="color:rgb(15, 17, 21);"> 与 Nacos 共享配置</font>
```yaml
server:
  port: 8080

spring:
  application:
    name: agent-access
  datasource:
    url: jdbc:mysql://${DB_HOST:localhost}:3306/agentscope?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: ${DB_USER}
    password: ${DB_PASSWORD}
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: 6379

agentscope:
  # 接入层：AG-UI 暴露
  agui:
    path-prefix: /agui
    cors-enabled: true
    server-side-memory: true   # 启用服务端会话保持[reference:1]

  # 执行策略（Builder 可读取，但优先在此统一管理）
  model:
    default: dashscope:qwen-plus
    fallback: openai:gpt-5.5
    timeout-seconds: 90
    max-attempts: 3

  compaction:
    trigger-messages: 30
    keep-messages: 10
    flush-before-compact: true   # 压缩前把事实提取到日流水账

  # 状态层：混合后端选择
  distributed:
    agent-state-store: mysql    # mysql / redis
    base-store: mysql
    sandbox-snapshot-spec: redis
    sandbox-execution-guard: redis

  # 接入层超时
  access:
    request-timeout-ms: 120000

# Nacos 接入（共享配置用）
nacos:
  server-addr: ${NACOS_ADDR:127.0.0.1:8848}
  namespace: ${NACOS_NAMESPACE:prod}
  group: AGENT_PLATFORM
  username: ${NACOS_USER}
  password: ${NACOS_PASSWORD}
```

### <font style="color:rgb(15, 17, 21);">通过 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">spring.config.import</font>`<font style="color:rgb(15, 17, 21);"> 引入 Nacos 共享配置</font>
<font style="color:rgb(15, 17, 21);">接入层的部署级配置（超时、限流阈值、AG-UI 开关）用 Nacos 共享配置，保证多副本一致，但</font>**<font style="color:rgb(15, 17, 21);">不在运行时热改</font>**<font style="color:rgb(15, 17, 21);">：</font>

```yaml
spring:
  config:
    import:
      - optional:nacos:agent-access-shared.yaml?group=AGENT_PLATFORM&refreshEnabled=false
```

### <font style="color:rgb(15, 17, 21);">产品域：Nacos 的核心战场</font>
### <font style="color:rgb(15, 17, 21);">Prompt 配置中心（热更新）</font>
`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agentscope-extensions-nacos-prompt</font>`<font style="color:rgb(15, 17, 21);"> 提供 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NacosPromptListener</font>`<font style="color:rgb(15, 17, 21);">，监听器内部维护本地缓存，Nacos 上 Prompt 更新时自动推送，下一次 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">getPrompt(...)</font>`<font style="color:rgb(15, 17, 21);"> 立即拿到新版本，无需重启。</font>

```xml
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-nacos-prompt</artifactId>
  <version>${agentscope.version}</version>
</dependency>
```

**<font style="color:rgb(15, 17, 21);">Nacos 上的配置规划</font>**<font style="color:rgb(15, 17, 21);">：</font>

| <font style="color:rgb(15, 17, 21);">dataId</font> | <font style="color:rgb(15, 17, 21);">group</font> | <font style="color:rgb(15, 17, 21);">内容</font> | <font style="color:rgb(15, 17, 21);">格式</font> |
| --- | --- | --- | --- |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent.prompt.system</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AGENT_PROMPT</font>` | <font style="color:rgb(15, 17, 21);">系统提示词模板</font> | <font style="color:rgb(15, 17, 21);">纯文本，支持 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">{{variable}}</font>` |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent.prompt.task.order</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AGENT_PROMPT</font>` | <font style="color:rgb(15, 17, 21);">订单场景任务模板</font> | <font style="color:rgb(15, 17, 21);">纯文本</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent.prompt.task.support</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AGENT_PROMPT</font>` | <font style="color:rgb(15, 17, 21);">客服场景任务模板</font> | <font style="color:rgb(15, 17, 21);">纯文本</font> |


**<font style="color:rgb(15, 17, 21);">Java 侧消费</font>**<font style="color:rgb(15, 17, 21);">（产品域，不放进 Builder）：</font>

```java
@Service
public class ProductPromptService {

    private final NacosPromptListener prompts;

    public ProductPromptService(AiService aiService) {
        this.prompts = new NacosPromptListener(aiService);
    }

    public String systemPrompt(String tenantId) {
        return prompts.getPrompt("agent.prompt.system", Map.of(
            "tenantName", resolveTenantName(tenantId),
            "today", LocalDate.now().toString()
        ));
    }
}
```

`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NacosPromptListener</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">getPrompt</font>`<font style="color:rgb(15, 17, 21);"> 每次调用都会拿最新版本，所以</font>**<font style="color:rgb(15, 17, 21);">不要在启动时把结果缓存到字段里</font>**<font style="color:rgb(15, 17, 21);">，否则热更新失效。</font>

### <font style="color:rgb(15, 17, 21);">Skill 仓库（版本/标签控制）</font>
`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agentscope-extensions-nacos-skill</font>`<font style="color:rgb(15, 17, 21);"> 提供 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">NacosSkillRepository</font>`<font style="color:rgb(15, 17, 21);">，从 Nacos AI 模块加载 Skill ZIP 包。版本解析顺序是：构造时传入的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Properties</font>`<font style="color:rgb(15, 17, 21);"> → JVM </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">-D</font>`<font style="color:rgb(15, 17, 21);"> 系统属性 → 默认值。</font>

```xml
<dependency>
  <groupId>io.agentscope</groupId>
  <artifactId>agentscope-extensions-nacos-skill</artifactId>
  <version>${agentscope.version}</version>
</dependency>
```

<font style="color:rgb(15, 17, 21);background-color:rgb(249, 250, 251);">java</font>

```java
@Bean
public AgentSkillRepository skillRepository(AiService aiService,
                                            @Value("${agent.skill.label:stable}") String label) {
    Properties props = new Properties();
    props.setProperty(NacosSkillRepository.SKILL_LABEL_PATH, label);
    return new NacosSkillRepository(aiService, "prod-namespace", props);
}
```

**<font style="color:rgb(15, 17, 21);">建议用 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SKILL_LABEL_PATH</font>**`**<font style="color:rgb(15, 17, 21);">（如 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">stable</font>**`**<font style="color:rgb(15, 17, 21);">）而不是硬编码 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">SKILL_VERSION_PATH</font>**`<font style="color:rgb(15, 17, 21);">，这样灰度切换 Skill 版本只需在 Nacos 控制台改标签，不用改代码或重启。</font>

### <font style="color:rgb(15, 17, 21);">工具权限规则（可选，按需）</font>
`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">tool_permission</font>`<font style="color:rgb(15, 17, 21);"> 表已经承载了权限规则的持久化，但如果希望</font>**<font style="color:rgb(15, 17, 21);">规则变更不经过数据库 DDL 发布</font>**<font style="color:rgb(15, 17, 21);">，可以把规则的“策略片段”放到 Nacos：</font>

| <font style="color:rgb(15, 17, 21);">dataId</font> | <font style="color:rgb(15, 17, 21);">内容</font> |
| --- | --- |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent.permission.rules</font>` | <font style="color:rgb(15, 17, 21);">JSON 数组，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">[{tool_id, tenant_id, role_id, decision, conditions}]</font>` |


<font style="color:rgb(15, 17, 21);">Spring Boot 侧用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@NacosValue</font>`<font style="color:rgb(15, 17, 21);"> 或 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ConfigService.addListener</font>`<font style="color:rgb(15, 17, 21);"> 监听变更，刷新内存中的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">PermissionContextState</font>`<font style="color:rgb(15, 17, 21);">。</font>**<font style="color:rgb(15, 17, 21);">注意</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">PermissionContextState</font>`<font style="color:rgb(15, 17, 21);"> 在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent.builder().permissionContext(perms)</font>`<font style="color:rgb(15, 17, 21);"> 时注入后不可变，所以规则热更新必须配合</font>**<font style="color:rgb(15, 17, 21);">每次请求 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">build()</font>**`**<font style="color:rgb(15, 17, 21);"> 新实例</font>**<font style="color:rgb(15, 17, 21);">，而不是复用单例。</font>

### <font style="color:rgb(15, 17, 21);">执行内核：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">application.yml</font>`<font style="color:rgb(15, 17, 21);"> 承载，Builder 兜底</font>
`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent.builder()</font>`<font style="color:rgb(15, 17, 21);"> 有完整的配置面，但</font>**<font style="color:rgb(15, 17, 21);">只有 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">name</font>**`**<font style="color:rgb(15, 17, 21);">、</font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">sysPrompt</font>**`**<font style="color:rgb(15, 17, 21);">、</font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">model</font>**`**<font style="color:rgb(15, 17, 21);"> 是必填的</font>**<font style="color:rgb(15, 17, 21);">，其余都有可用默认值。生产环境的执行策略应该用 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@ConfigurationProperties</font>`<font style="color:rgb(15, 17, 21);"> 绑定 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">application.yml</font>`<font style="color:rgb(15, 17, 21);">，在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@Bean</font>`<font style="color:rgb(15, 17, 21);"> 中构建 Builder，而不是把参数散落在 Nacos：</font>

```java
@ConfigurationProperties(prefix = "agentscope")
@Data
public class AgentScopeProperties {
    private Model model = new Model();
    private Compaction compaction = new Compaction();
    private Distributed distributed = new Distributed();

    @Data
    public static class Model {
        private String defaultModel = "dashscope:qwen-plus";
        private String fallback;
        private int timeoutSeconds = 90;
        private int maxAttempts = 3;
    }

    @Data
    public static class Compaction {
        private int triggerMessages = 30;
        private int keepMessages = 10;
        private boolean flushBeforeCompact = true;
    }

    @Data
    public static class Distributed {
        private String agentStateStore = "mysql";
        private String baseStore = "mysql";
        private String sandboxSnapshotSpec = "redis";
        private String sandboxExecutionGuard = "redis";
    }
}
```

```java
@Bean
public HarnessAgent.Builder harnessAgentBuilder(
        AgentScopeProperties props,
        Model model,
        Toolkit toolkit,
        DistributedStore distributedStore) {

    return HarnessAgent.builder()
            .name("product-agent")
            .agentId("product-agent-v1")          // 稳定命名空间键，避免改名导致状态失联
            .sysPrompt("placeholder")             // 实际 sysPrompt 由请求级传入或 Prompt 服务注入
            .model(model)
            .fallbackModel(props.getModel().getFallback())
            .modelExecutionConfig(ExecutionConfig.builder()
                    .timeout(Duration.ofSeconds(props.getModel().getTimeoutSeconds()))
                    .maxAttempts(props.getModel().getMaxAttempts())
                    .build())
            .toolkit(toolkit)
            .maxIters(20)
            .compaction(CompactionConfig.builder()
                    .triggerMessages(props.getCompaction().getTriggerMessages())
                    .keepMessages(props.getCompaction().getKeepMessages())
                    .flushBeforeCompact(props.getCompaction().isFlushBeforeCompact())
                    .build())
            .distributedStore(distributedStore)
            .build();
}
```

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agentId</font>**`**<font style="color:rgb(15, 17, 21);"> 必须显式设置</font>**<font style="color:rgb(15, 17, 21);">：不设置时 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">name</font>`<font style="color:rgb(15, 17, 21);"> 会兼作命名空间键，改展示名会把 Agent 指向全新的空状态命名空间。这是一个很容易在生产环境踩到的坑。</font>

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">sysPrompt</font>**`**<font style="color:rgb(15, 17, 21);"> 在 Builder 里放占位符</font>**<font style="color:rgb(15, 17, 21);">，实际内容由产品域的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ProductPromptService</font>`<font style="color:rgb(15, 17, 21);"> 在请求级注入，或者通过 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">RuntimeContext</font>`<font style="color:rgb(15, 17, 21);"> 传递。这样 Prompt 热更新和 Builder 生命周期解耦。</font>

### <font style="color:rgb(15, 17, 21);">状态层：DistributedStore 装配，不走 Nacos</font>
`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<font style="color:rgb(15, 17, 21);"> 的混合后端必须在 Java 侧装配，因为需要 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DataSource</font>`<font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Jedis</font>`<font style="color:rgb(15, 17, 21);"> 的注入，</font>**<font style="color:rgb(15, 17, 21);">不应该把连接串或后端选择放到 Nacos 热更新</font>**<font style="color:rgb(15, 17, 21);">——连接变更应该走部署流程：</font>

```java
@Bean
public DistributedStore distributedStore(DataSource dataSource, JedisPooled jedis) {
    DistributedStore mysql = MysqlDistributedStore.create(dataSource);
    DistributedStore redis = RedisDistributedStore.fromJedis(jedis);

    return DistributedStore.builder()
            .agentStateStore(mysql.agentStateStore())       // 对话上下文、压缩摘要、权限规则
            .baseStore(mysql.baseStore())                   // 工作区文件 KV
            .sandboxSnapshotSpec(redis.sandboxSnapshotSpec())
            .sandboxExecutionGuard(redis.sandboxExecutionGuard())
            .build();
}
```

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">sandboxExecutionGuard</font>**`**<font style="color:rgb(15, 17, 21);"> 不要用 OSS</font>**<font style="color:rgb(15, 17, 21);">：对象存储不适合做分布式锁，需要并发控制的场景必须混入 Redis 的 guard。</font>

### <font style="color:rgb(15, 17, 21);">Nacos 配置的完整 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">bootstrap.yml</font>`<font style="color:rgb(15, 17, 21);"> 示例</font>
```yaml
spring:
  application:
    name: agent-access
  cloud:
    nacos:
      config:
        server-addr: ${NACOS_ADDR}
        namespace: ${NACOS_NAMESPACE:prod}
        group: AGENT_PLATFORM
        username: ${NACOS_USER}
        password: ${NACOS_PASSWORD}
        file-extension: yaml
        # 产品域专属配置：热更新开启
        extension-configs:
          - data-id: agent-prompt.yaml
            group: AGENT_PROMPT
            refresh: true
          - data-id: agent-skill.yaml
            group: AGENT_SKILL
            refresh: true
          - data-id: agent-permission.yaml
            group: AGENT_PERMISSION
            refresh: true
        # 接入层共享配置：热更新关闭
        shared-configs:
          - data-id: agent-access-shared.yaml
            group: AGENT_PLATFORM
            refresh: false
```

<font style="color:rgb(15, 17, 21);">对应的 Nacos 配置内容示例：</font>

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-prompt.yaml</font>**`<font style="color:rgb(15, 17, 21);">（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AGENT_PROMPT</font>`<font style="color:rgb(15, 17, 21);"> 组）：</font>

```yaml
agent:
  prompt:
    system-key: agent.prompt.system
    task-keys:
      order: agent.prompt.task.order
      support: agent.prompt.task.support
```

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-skill.yaml</font>**`<font style="color:rgb(15, 17, 21);">（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AGENT_SKILL</font>`<font style="color:rgb(15, 17, 21);"> 组）：</font>

```yaml
agent:
  skill:
    label: stable          # 灰度时改为 canary
    namespace: prod-namespace
```

`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-access-shared.yaml</font>**`<font style="color:rgb(15, 17, 21);">（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AGENT_PLATFORM</font>`<font style="color:rgb(15, 17, 21);"> 组，不热更新）：</font>

```yaml
agentscope:
  access:
    request-timeout-ms: 120000
    rate-limit:
      qps: 50
```

### <font style="color:rgb(15, 17, 21);">一个容易踩的坑：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@RefreshScope</font>`<font style="color:rgb(15, 17, 21);"> 与 Agent 实例化</font>
<font style="color:rgb(15, 17, 21);">如果产品域确实需要让权限规则或部分执行策略热更新，</font>**<font style="color:rgb(15, 17, 21);">不要给 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent</font>**`**<font style="color:rgb(15, 17, 21);"> 单例加 </font>**`**<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@RefreshScope</font>**`<font style="color:rgb(15, 17, 21);">。正确做法是：</font>

+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent.Builder</font>`<font style="color:rgb(15, 17, 21);"> 保持单例，承载稳定的执行策略；</font>
+ <font style="color:rgb(15, 17, 21);">每次请求 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">builder.build()</font>`<font style="color:rgb(15, 17, 21);"> 出短生命周期实例，产品域的最新 Prompt、权限规则、Skill 标签在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">build()</font>`<font style="color:rgb(15, 17, 21);"> 前注入；</font>
+ <font style="color:rgb(15, 17, 21);">Nacos 监听器只更新产品域服务的内存状态，不直接操作 Builder。</font>

<font style="color:rgb(15, 17, 21);">这样既保留了热更新能力，又不会因为刷新导致 Agent 内部状态（如压缩摘要、Plan Mode）丢失。</font>

**<font style="color:rgb(15, 17, 21);">一句话总结</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">application.yml</font>`<font style="color:rgb(15, 17, 21);"> 管接入层和状态层的工程装配，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent.builder()</font>`<font style="color:rgb(15, 17, 21);"> 管执行内核的类型化策略，</font>**<font style="color:rgb(15, 17, 21);">Nacos 只管产品域的 Prompt、Skill 标签和权限规则片段</font>**<font style="color:rgb(15, 17, 21);">——三者各司其职，热更新范围严格限定在产品语义层，执行策略和连接配置不放进 Nacos 裸改。</font>

## 包
<font style="color:rgb(15, 17, 21);">下面按前面确定的多模块划分（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-access</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-semantic</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-runtime</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-state</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-audit</font>`<font style="color:rgb(15, 17, 21);">）加两个基础模块（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-common</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-bootstrap</font>`<font style="color:rgb(15, 17, 21);">），给出</font>**<font style="color:rgb(15, 17, 21);">项目包结构规划</font>**<font style="color:rgb(15, 17, 21);">。核心约束是：</font>**<font style="color:rgb(15, 17, 21);">模块间依赖严格单向，模块内按 DDD 分层，包名即边界</font>**<font style="color:rgb(15, 17, 21);">。</font>

### <font style="color:rgb(15, 17, 21);">1. Maven 多模块总览</font>
```yaml
agentscope-platform/                      (parent pom)
├── agent-common/        公共基础：异常、常量、通用 DTO、工具
├── agent-state/         状态层：DDL Repository、状态读写、锁
├── agent-semantic/      产品域：语义契约、工具目录、权限规则、Prompt
├── agent-runtime/       执行内核：HarnessAgent 装配、Hook、Toolkit 注册
├── agent-audit/         审计横切：事件审计、审批落库、操作审计
├── agent-access/        接入层：Controller、Channel、AG-UI、鉴权限流
└── agent-bootstrap/     启动装配：Spring Boot 主类、配置聚合
```

**<font style="color:rgb(15, 17, 21);">依赖方向（严格单向，禁止反向）</font>**<font style="color:rgb(15, 17, 21);">：</font>

```yaml
agent-bootstrap
   ├──> agent-access
   │        ├──> agent-semantic
   │        ├──> agent-runtime
   │        │        ├──> agent-semantic (契约接口)
   │        │        ├──> agent-state
   │        │        └──> agent-audit
   │        ├──> agent-audit
   │        └──> agent-state
   └──> agent-common (所有模块)
```

**<font style="color:rgb(15, 17, 21);">关键约束</font>**<font style="color:rgb(15, 17, 21);">：</font>

| <font style="color:rgb(15, 17, 21);">约束</font> | <font style="color:rgb(15, 17, 21);">原因</font> |
| --- | --- |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-semantic</font>`<br/>**<font style="color:rgb(15, 17, 21);">不依赖</font>**`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-runtime</font>`<br/><font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-access</font>` | <font style="color:rgb(15, 17, 21);">产品域不能反向依赖执行和接入，否则语义会被接入逻辑污染</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-runtime</font>`<br/>**<font style="color:rgb(15, 17, 21);">只依赖</font>**`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">semantic</font>`<br/><font style="color:rgb(15, 17, 21);"> 的契约包（</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">contract</font>`<br/><font style="color:rgb(15, 17, 21);">），不依赖其实现</font> | <font style="color:rgb(15, 17, 21);">执行内核面向接口，产品语义实现可替换</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-access</font>`<br/>**<font style="color:rgb(15, 17, 21);">不直接</font>**`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">new HarnessAgent</font>` | <font style="color:rgb(15, 17, 21);">接入层只调 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<br/><font style="color:rgb(15, 17, 21);"> 暴露的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentExecutor</font>`<br/><font style="color:rgb(15, 17, 21);"> 门面，防止接入层膨胀</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-state</font>`<br/>**<font style="color:rgb(15, 17, 21);">不依赖</font>**`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">semantic</font>`<br/><font style="color:rgb(15, 17, 21);"> / </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>` | <font style="color:rgb(15, 17, 21);">状态层只做持久化，不认识业务语义和执行逻辑</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-audit</font>`<br/><font style="color:rgb(15, 17, 21);"> 被 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<br/><font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">access</font>`<br/><font style="color:rgb(15, 17, 21);"> 依赖，</font>**<font style="color:rgb(15, 17, 21);">不反向依赖</font>** | <font style="color:rgb(15, 17, 21);">审计是横切消费方，被审计方不应知道审计实现</font> |


### <font style="color:rgb(15, 17, 21);">2. 各模块包结构</font>
<font style="color:rgb(15, 17, 21);">统一包名前缀 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">com.example.agentscope</font>`<font style="color:rgb(15, 17, 21);">。模块内按 DDD 分层：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">api</font>`<font style="color:rgb(15, 17, 21);">（对外）→ </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">application</font>`<font style="color:rgb(15, 17, 21);">（用例编排）→ </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">domain</font>`<font style="color:rgb(15, 17, 21);">（领域）→ </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">infrastructure</font>`<font style="color:rgb(15, 17, 21);">（实现）→ </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">config</font>`<font style="color:rgb(15, 17, 21);">（装配）。</font>

#### <font style="color:rgb(15, 17, 21);">2.1 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-common</font>`
```yaml
com.example.agentscope.common
├── constant/          全局常量：Header、事件类型名、默认值
├── exception/         业务异常基类、错误码枚举
├── dto/               跨模块通用 DTO（分页、结果包装）
├── util/              JSON、ID 生成、时间工具
└── context/           TenantContext、UserContext（ThreadLocal 封装）
```

**<font style="color:rgb(15, 17, 21);">规则</font>**<font style="color:rgb(15, 17, 21);">：不依赖任何其他业务模块；不出现 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@Service</font>`<font style="color:rgb(15, 17, 21);">，只有纯工具和值对象。</font>

#### <font style="color:rgb(15, 17, 21);">2.2 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-state</font>`
```yaml
com.example.agentscope.state
├── api/                              状态层对外接口（供 runtime 调用）
│   ├── AgentStateRepository.java
│   ├── WorkspaceFileRepository.java
│   └── ExecutionLockService.java
├── application/
│   ├── AgentStateAppService.java     会话状态读写编排
│   └── ExecutionLockAppService.java
├── domain/
│   ├── model/                        SessionState、Message、CompressionSummary
│   └── repository/                   仓储接口（领域侧定义）
├── infrastructure/
│   ├── persistence/
│   │   ├── mysql/                    agent_session_state / agent_message 等 Mapper
│   │   ├── redis/                    沙箱快照、执行锁
│   │   └── entity/                   与 DDL 一一对应的 Entity
│   └── store/                        DistributedStore 装配的底层适配
└── config/
    └── DistributedStoreConfig.java   混合后端装配（MySQL + Redis）
```

**<font style="color:rgb(15, 17, 21);">对应 DDL</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent_session_state</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent_message</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent_compression_summary</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">workspace_file</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent_execution_lock</font>`<font style="color:rgb(15, 17, 21);"> 的 Repository 全部落在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">infrastructure/persistence</font>`<font style="color:rgb(15, 17, 21);">。</font>

#### <font style="color:rgb(15, 17, 21);">2.3 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-semantic</font>`
```yaml
com.example.agentscope.semantic
├── contract/                         对外契约（runtime 只依赖这个包）
│   ├── ProductSemanticProvider.java
│   ├── ToolDescriptor.java
│   └── CompletionCriteria.java
├── api/                              供 access 调用的产品服务
│   ├── PromptService.java
│   ├── ToolCatalogService.java
│   └── PermissionRuleService.java
├── application/
│   ├── PromptAppService.java
│   ├── ToolRegistryAppService.java
│   └── PermissionRuleAppService.java
├── domain/
│   ├── model/                        ToolDefinition、PermissionRule、PromptTemplate
│   └── service/                      PermissionRuleResolver（最具体优先匹配）
├── infrastructure/
│   ├── persistence/                  tool_registry / tool_permission / prompt_template 等 Mapper
│   ├── nacos/                        NacosPromptListener、NacosSkillRepository 适配
│   └── tool/                         具体 @Tool 实现类（业务工具）
└── config/
    └── SemanticConfig.java           ProductSemanticProvider 的默认实现装配
```

**<font style="color:rgb(15, 17, 21);">两个关键点</font>**<font style="color:rgb(15, 17, 21);">：</font>

+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">contract/</font>`<font style="color:rgb(15, 17, 21);"> 是唯一对 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<font style="color:rgb(15, 17, 21);"> 暴露的包。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<font style="color:rgb(15, 17, 21);"> 的 pom </font>**<font style="color:rgb(15, 17, 21);">只允许</font>**<font style="color:rgb(15, 17, 21);"> import 这个包，不允许 import </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">semantic.infrastructure</font>`<font style="color:rgb(15, 17, 21);"> 或 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">semantic.application</font>`<font style="color:rgb(15, 17, 21);">。</font>
+ `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">infrastructure/tool/</font>`<font style="color:rgb(15, 17, 21);"> 放 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@Tool</font>`<font style="color:rgb(15, 17, 21);"> 注解的业务工具类。</font>**<font style="color:rgb(15, 17, 21);">工具的业务实现属于产品域</font>**<font style="color:rgb(15, 17, 21);">，但注册进 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Toolkit</font>`<font style="color:rgb(15, 17, 21);"> 的动作在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<font style="color:rgb(15, 17, 21);">，这样工具变更不需要动执行内核。</font>

#### <font style="color:rgb(15, 17, 21);">2.4 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-runtime</font>`
```yaml
com.example.agentscope.runtime
├── api/                              接入层唯一调用入口（门面）
│   ├── AgentExecutor.java            封装 HarnessAgent.call 的薄门面
│   ├── AgentRequest.java
│   └── AgentResponse.java
├── application/
│   ├── AgentExecutionAppService.java 创建 RuntimeContext + AgentRun，调 HarnessAgent
│   └── ToolkitAssemblyService.java   按租户/请求组装 Toolkit
├── domain/
│   └── hook/                         Hook 实现（不属于产品域，属于执行策略）
│       ├── AuditHook.java
│       └── ApprovalHook.java
├── infrastructure/
│   ├── agent/                        HarnessAgent.builder() 装配、短生命周期 build
│   └── store/                        DistributedStore 引用注入
└── config/
    ├── HarnessAgentConfig.java       Builder Bean
    └── AgentScopeProperties.java     @ConfigurationProperties
```

**<font style="color:rgb(15, 17, 21);">关键点</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">api/AgentExecutor</font>`<font style="color:rgb(15, 17, 21);"> 是接入层与执行内核之间</font>**<font style="color:rgb(15, 17, 21);">唯一的缝</font>**<font style="color:rgb(15, 17, 21);">。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">access</font>`<font style="color:rgb(15, 17, 21);"> 调 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentExecutor.execute(request)</font>`<font style="color:rgb(15, 17, 21);">，不直接触碰 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent</font>`<font style="color:rgb(15, 17, 21);">。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ApprovalHook</font>`<font style="color:rgb(15, 17, 21);"> 里处理 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ASK</font>`<font style="color:rgb(15, 17, 21);"> 时，调 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">audit</font>`<font style="color:rgb(15, 17, 21);"> 模块落 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">approval_record</font>`<font style="color:rgb(15, 17, 21);">，但不直接调 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">access</font>`<font style="color:rgb(15, 17, 21);"> 的推送——推送通过事件发布（Spring </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ApplicationEventPublisher</font>`<font style="color:rgb(15, 17, 21);">）由 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">access</font>`<font style="color:rgb(15, 17, 21);"> 订阅，避免 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime → access</font>`<font style="color:rgb(15, 17, 21);"> 的反向依赖。</font>

#### <font style="color:rgb(15, 17, 21);">2.5 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-audit</font>`
```yaml
com.example.agentscope.audit
├── api/
│   ├── AuditRecorder.java            审计写入门面
│   └── ApprovalRecorder.java
├── application/
│   ├── EventAuditAppService.java
│   └── ApprovalAuditAppService.java
├── domain/
│   ├── model/                        AuditEvent、ApprovalRecord
│   └── event/                        ApprovalRequestedEvent（跨模块事件）
├── infrastructure/
│   └── persistence/                  agent_event_audit / approval_record / operation_audit Mapper
└── config/
    └── AuditConfig.java
```

**<font style="color:rgb(15, 17, 21);">关键点</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ApprovalRequestedEvent</font>`<font style="color:rgb(15, 17, 21);"> 由 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ApprovalHook</font>`<font style="color:rgb(15, 17, 21);"> 发布，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">access</font>`<font style="color:rgb(15, 17, 21);"> 订阅后负责推送审批通知。这是打破 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime → access</font>`<font style="color:rgb(15, 17, 21);"> 循环依赖的标准做法。</font>

#### <font style="color:rgb(15, 17, 21);">2.6 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-access</font>`
```yaml
com.example.agentscope.access
├── api/
│   ├── controller/
│   │   ├── AgentChatController.java
│   │   ├── SessionController.java
│   │   └── ApprovalController.java
│   └── dto/                          请求/响应 DTO（与 runtime 的 AgentRequest 隔离）
├── application/
│   ├── ChatAppService.java           接收请求 → 调 AgentExecutor
│   ├── ApprovalNotifyAppService.java 订阅 ApprovalRequestedEvent，推送通知
│   └── QuotaCheckService.java        调 state/quota 做限流
├── domain/
│   └── channel/                      Channel 抽象（WEB / IM / API）
├── infrastructure/
│   ├── agui/                         AG-UI 端点、SSE 推送
│   ├── notify/                       IM / 邮件 / Webhook 推送实现
│   └── security/                     鉴权、租户解析、限流拦截器
└── config/
    ├── WebConfig.java
    └── SecurityConfig.java
```

**<font style="color:rgb(15, 17, 21);">关键点</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">controller</font>`<font style="color:rgb(15, 17, 21);"> 的 DTO 与 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentRequest</font>`<font style="color:rgb(15, 17, 21);"> 分离，Controller 只做参数校验和上下文组装，业务编排在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ChatAppService</font>`<font style="color:rgb(15, 17, 21);">。</font>

#### <font style="color:rgb(15, 17, 21);">2.7 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-bootstrap</font>`
```yaml
com.example.agentscope.bootstrap
├── AgentPlatformApplication.java     Spring Boot 主类
├── config/                           跨模块聚合配置（@Import、@ConfigurationPropertiesScan）
└── resources/
    ├── application.yml
    └── bootstrap.yml                 Nacos 配置
```

**<font style="color:rgb(15, 17, 21);">规则</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">bootstrap</font>`<font style="color:rgb(15, 17, 21);"> 只做装配和启动，</font>**<font style="color:rgb(15, 17, 21);">不写业务代码</font>**<font style="color:rgb(15, 17, 21);">。所有 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@Bean</font>`<font style="color:rgb(15, 17, 21);"> 装配在各模块自己的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">config</font>`<font style="color:rgb(15, 17, 21);"> 包，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">bootstrap</font>`<font style="color:rgb(15, 17, 21);"> 通过组件扫描聚合。</font>

### <font style="color:rgb(15, 17, 21);">3. 包依赖规则（可用 ArchUnit 校验）</font>
<font style="color:rgb(15, 17, 21);">建议在 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">agent-bootstrap</font>`<font style="color:rgb(15, 17, 21);"> 加 ArchUnit 测试，把边界固化成可执行断言：</font>

```java
@AnalyzeClasses(packages = "com.example.agentscope")
class ArchitectureTest {

    @ArchTest
    static final ArchRule semantic_must_not_depend_on_runtime_or_access =
    noClasses().that().resideInAPackage("..semantic..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..runtime..", "..access..");

    @ArchTest
    static final ArchRule runtime_only_depends_on_semantic_contract =
    noClasses().that().resideInAPackage("..runtime..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..semantic.infrastructure..", "..semantic.application..");

    @ArchTest
    static final ArchRule state_must_not_depend_on_business =
    noClasses().that().resideInAPackage("..state..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..semantic..", "..runtime..", "..access..");

    @ArchTest
    static final ArchRule access_must_not_touch_harness_agent =
    noClasses().that().resideInAPackage("..access..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..harness..");

    @ArchTest
    static final ArchRule controllers_must_not_access_repositories =
    noClasses().that().resideInAPackage("..access.api.controller..")
    .should().dependOnClassesThat()
    .resideInAnyPackage("..infrastructure.persistence..");
}
```

### <font style="color:rgb(15, 17, 21);">4. 几个容易放错位置的包</font>
| <font style="color:rgb(15, 17, 21);">容易放错</font> | <font style="color:rgb(15, 17, 21);">正确位置</font> | <font style="color:rgb(15, 17, 21);">原因</font> |
| --- | --- | --- |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">@Tool</font>`<br/><font style="color:rgb(15, 17, 21);"> 业务工具类放 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">semantic/infrastructure/tool</font>` | <font style="color:rgb(15, 17, 21);">工具的业务实现属产品域，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<br/><font style="color:rgb(15, 17, 21);"> 只负责注册进 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">Toolkit</font>` |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ApprovalHook</font>`<br/><font style="color:rgb(15, 17, 21);"> 放 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">access</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime/domain/hook</font>` | <font style="color:rgb(15, 17, 21);">Hook 是执行策略，属执行内核；推送动作才属接入层</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ProductSemanticProvider</font>`<br/><font style="color:rgb(15, 17, 21);"> 实现放 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">semantic/config</font>`<br/><font style="color:rgb(15, 17, 21);"> 装配</font> | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<br/><font style="color:rgb(15, 17, 21);"> 只依赖 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">contract</font>`<br/><font style="color:rgb(15, 17, 21);"> 接口，实现由产品域提供</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">QuotaCheckService</font>`<br/><font style="color:rgb(15, 17, 21);"> 放 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">semantic</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">access/application</font>` | <font style="color:rgb(15, 17, 21);">配额是接入层限流依据，属接入治理</font> |
| `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<br/><font style="color:rgb(15, 17, 21);"> 装配放 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">bootstrap</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">state/config</font>` | <font style="color:rgb(15, 17, 21);">状态层自己管自己的后端装配，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">bootstrap</font>`<br/><font style="color:rgb(15, 17, 21);"> 不写业务配置</font> |
| <font style="color:rgb(15, 17, 21);">跨模块事件放 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">common</font>` | <font style="color:rgb(15, 17, 21);">发布方模块的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">domain/event</font>` | `<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">common</font>`<br/><font style="color:rgb(15, 17, 21);"> 不应承载业务语义事件</font> |


### <font style="color:rgb(15, 17, 21);">5. 包结构落地顺序建议</font>
1. **<font style="color:rgb(15, 17, 21);">先建骨架</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">common</font>`<font style="color:rgb(15, 17, 21);"> + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">state</font>`<font style="color:rgb(15, 17, 21);"> + </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">bootstrap</font>`<font style="color:rgb(15, 17, 21);"> 三模块，跑通 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">DistributedStore</font>`<font style="color:rgb(15, 17, 21);"> 装配和 DDL Repository。</font>
2. **<font style="color:rgb(15, 17, 21);">再建契约</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">semantic/contract</font>`<font style="color:rgb(15, 17, 21);"> 定义 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ProductSemanticProvider</font>`<font style="color:rgb(15, 17, 21);">，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime/api</font>`<font style="color:rgb(15, 17, 21);"> 定义 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentExecutor</font>`<font style="color:rgb(15, 17, 21);"> 门面，两边都只依赖契约。</font>
3. **<font style="color:rgb(15, 17, 21);">接入打通</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">access</font>`<font style="color:rgb(15, 17, 21);"> 调 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AgentExecutor</font>`<font style="color:rgb(15, 17, 21);">，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<font style="color:rgb(15, 17, 21);"> 内部 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">builder.build()</font>`<font style="color:rgb(15, 17, 21);"> 调 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">HarnessAgent</font>`<font style="color:rgb(15, 17, 21);">，跑通端到端。</font>
4. **<font style="color:rgb(15, 17, 21);">最后补横切</font>**<font style="color:rgb(15, 17, 21);">：</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">audit</font>`<font style="color:rgb(15, 17, 21);"> 的 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">AuditHook</font>`<font style="color:rgb(15, 17, 21);">、</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ApprovalHook</font>`<font style="color:rgb(15, 17, 21);"> 接进 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime</font>`<font style="color:rgb(15, 17, 21);">，</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">access</font>`<font style="color:rgb(15, 17, 21);"> 订阅 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">ApprovalRequestedEvent</font>`<font style="color:rgb(15, 17, 21);"> 做推送。</font>
5. **<font style="color:rgb(15, 17, 21);">加 ArchUnit</font>**<font style="color:rgb(15, 17, 21);">：边界测试随第一个业务用例一起写，不要等模块多了再补。</font>

**<font style="color:rgb(15, 17, 21);">一句话</font>**<font style="color:rgb(15, 17, 21);">：包结构不是目录美学，而是</font>**<font style="color:rgb(15, 17, 21);">把四层边界变成编译期可校验的依赖规则</font>**<font style="color:rgb(15, 17, 21);">。</font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">semantic.contract</font>`<font style="color:rgb(15, 17, 21);"> 和 </font>`<font style="color:rgb(15, 17, 21);background-color:rgb(235, 238, 242);">runtime.api</font>`<font style="color:rgb(15, 17, 21);"> 这两个“缝”是整套包规划的核心——只要这两个包不被穿透，四层解耦就不会在代码里腐化。</font>

