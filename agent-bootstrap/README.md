# agent-bootstrap

启动模块。把各模块扫进同一个进程，自己不写业务。需要全仓 classpath 的边界测（ArchUnit、接线、`@SpringBootTest`、spike）仍放这里。纯领域测试点已下沉到所属模块（见 `docs/bootstrap-test-sink-落地.md`）；**不强制大搬家**，旧测仍观察。

坐标：`com.zhu:agent-bootstrap`。包名：`com.zhu.bootstrap`。

## 依赖

依赖 `agent-common`、`agent-db`（Flyway 脚本）、`agent-vector`、`agent-object-store`、`agent-state`、`agent-semantic`、`agent-runtime`、`agent-access`、`agent-cache-redis`、`agent-product-companion`（及日后其它 `agent-product-*`）。测试依赖 `spring-boot-starter-test` 和 ArchUnit。管理进程不依赖 `agent-db` / `agent-vector` / 产品模块；同样引入 `agent-cache-redis` 做建连。

本模块是唯一用户 App（A1）：只做扫描 / 装配 / Nacos / Flyway，不含产品业务类。产品实现落在 `agent-products/*`，经 `ProductModule` 挂入。

`@SpringBootApplication(scanBasePackages = "com.zhu")` 会扫到兄弟包（含 `com.zhu.product..`）。如果只扫 `com.zhu.bootstrap`，接入层控制器、状态配置和产品 Binding 都不会生效。

## 启动

在仓库根、使用 JDK 21：

```text
mvn -pl agent-bootstrap -am spring-boot:run
```

本机若默认 `JAVA_HOME` 仍是 JDK 8，需要先指到 JDK 21。JDK 8 无法编译当前源码。

默认配置在 `src/main/resources/application.yml`：

- 数据源、Redis 主机和 Milvus 地址来自环境变量。没配或连不上就退出。连通后创建 `knowledge` 集合，向量维度默认 1024。不写入、不检索。不再创建遗留 `episodic_memory`（见 `docs/memory-hard-delete-落地.md`）。换嵌入模型要先删旧集合。本进程执行 Flyway。连接池最大 20、最小空闲 5。
- `agentscope.distributed.mysql.enabled=true`，`create-if-not-exist` 保持 `false`。
- 模型默认 `dashscope:qwen-plus`，可用环境变量 `AGENTSCOPE_MODEL` 覆盖。密钥不要写进这个文件。
- `application.yml` 只留进程骨架（停机、上传上限、agent-id、Flyway/MyBatis、Nacos 地址）。业务与连接默认在 `deploy/nacos/`；无 Nacos 时取消注释 `application.yml` 文末兜底块。
- `NACOS_ADDR` 为空时不建提示词客户端，也不拉启动配置。配了地址则启动必须拉到 `agent-bootstrap-infra.yaml`（连接/密钥，不热更）与 `agent-bootstrap.yaml`（业务旋钮，可热更），group `AGENT_PLATFORM`，缺一退出。允许键写在 `classpath:nacos-allowlist.yml`；非法热更拒收并保留上一份。提示词仍走 AI Prompt 监听，拉失败用兜底。不做服务发现。MCP：业务白名单前缀 `agentscope.runtime.mcp.servers`；用户进程 companion 已接线（空清单不连；`${ENV}` 占位；`required` 硬/软失败）；**不做 MCP 服务发现**；密钥不进 Nacos 明文。见 `docs/mcp-落地.md`。Sandbox：白名单前缀 `agentscope.runtime.sandbox`；默认关；`enabled=true` 时仅 companion 换 DockerFilesystem（缺 Store / `docker version` 硬失败）；echo 仍 Remote；Docker socket/凭证不进 Nacos。见 `docs/sandbox-落地.md`。Wakeup：白名单前缀 `agentscope.runtime.wakeup`；默认关；`enabled=true` 时用户进程启进程内 Bus + Dispatcher（仅 companion）；`POST /api/internal/wakeups` + `AGENTSCOPE_WAKEUP_TOKEN`；令牌不进业务热更明文。见 `docs/wakeup-落地.md`。

`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`AGENTSCOPE_REDIS_HOST`、`MILVUS_URI` 必须在环境里提供。密码用 `SPRING_DATASOURCE_PASSWORD`，可以为空。

## 测试

`mvn test`。JDK 21。根目录会跑各模块测（含 `agent-runtime` 已下沉的 T1 四测）。本模块表只列仍留在 bootstrap 的代表项。

| 测试 | 断言 |
| --- | --- |
| `ArchitectureTest` | 语义不依赖运行层/接入层/产品/缓存；运行层不碰语义实现与 `com.zhu.product..`；产品不碰接入层；access/runtime/product/audit 不碰 `cache.redis` 实现；状态/向量/对象存储不碰业务与缓存；接入层不碰 `io.agentscope` 与 `runtime.infrastructure`；控制器不访问持久化包。导入须含各模块代表类型（含 Binding / CacheFacade） |
| `BootstrapContextTest` | 上下文能起来；恰好一个 `AgentExecutor`；没有 `HarnessAgent` Bean；没有 `DistributedStore` Bean；向量口是空实现；有嵌入器但测试不连网；`GET /health/live` 返回 UP；测试配置下 `GET /health/ready` 跳过 MySQL、Redis 和 Milvus |
| `NacosStartupConfigSupportTest` | 业务白名单含 `agentscope.runtime.mcp.servers` / `sandbox` / `wakeup` 前缀；展平键可热更收进 Environment |
| `AgentRuntimeSandboxPropertiesTest` | Sandbox 缺省关、镜像空；`DEFAULT_IMAGE` 常量为 `ubuntu:24.04` |
| `AgentRuntimeWakeupPropertiesTest` | Wakeup 缺省关；`setWakeup(null)` 回默认；不启 Dispatcher |
| `WakeupTokensTest` | 唤醒令牌路径识别、占位串拒收、`requireSecure`、定长比较 |
| `WakeupTokenFilterTest` | 对的上才放行；错令牌 401；Caller 过滤器跳过内部唤醒路径 |
| `InMemoryMessageBusWakeupTest` | `enqueueWakeup` 入队并发信号；关闭后拒绝操作；不连外部总线 |
| `WakeupCoordinatorTest` | 默认关 enqueue=false；无令牌硬失败；忙会话由 Dispatcher 跳过 |
| `CompanionSandboxFilesystemTest` | 关→null；缺 Store / Docker 探测失败硬失败；开→Docker USER + 默认镜像；companion 源码接线、echo 不挂；不连真 Docker |
| `DockerCliProbeTest` | 注入假 runner：退出 0 通过；非 0 / IO 异常硬失败；不启动真 docker 进程 |
| `McpToolsConfigFactoryTest` | 空清单不装配；占位展开；`required` 硬/软失败（非法 transport，不连真 MCP）；companion 源码接线、echo 不挂 |
| `RemoteSubagentSurfaceSpikeTest` | Remote 声明面：无 url 非 Remote；有占位 url→`isRemote`；DENY/PROPAGATE 与 streaming 读回；Manager 可召回；不发 HTTP、不改工厂。见 `docs/remote-subagent-落地.md` |

## 不放这里的东西

业务 Bean、DistributedStore 装配、提示词和权限。那些分别留在 `agent-state`、`agent-semantic` 和 `agent-runtime`。本模块只聚合。
