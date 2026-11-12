# agent-bootstrap

启动模块。把各模块扫进同一个进程，自己不写业务。可执行的边界测试也放在这里，因为只有这里的 classpath 同时有全部模块。

坐标：`com.zhu:agent-bootstrap`。包名：`com.zhu.bootstrap`。

## 依赖

依赖 `agent-common`、`agent-vector`、`agent-state`、`agent-semantic`、`agent-runtime`、`agent-audit`、`agent-access`。测试依赖 `spring-boot-starter-test` 和 ArchUnit。管理进程不依赖 `agent-vector`。

`@SpringBootApplication(scanBasePackages = "com.zhu")` 会扫到兄弟包。如果只扫 `com.zhu.bootstrap`，接入层控制器和状态配置都不会生效。

## 启动

在仓库根、使用 JDK 21：

```text
mvn -pl agent-bootstrap -am spring-boot:run
```

本机若默认 `JAVA_HOME` 仍是 JDK 8，需要先指到 JDK 21。JDK 8 无法编译当前源码。

默认配置在 `src/main/resources/application.yml`：

- 数据源、Redis 主机和 Milvus 地址来自环境变量。没配或连不上就退出。连通后创建 `knowledge` 和 `episodic_memory` 集合，向量维度默认 1024。不写入、不检索。换嵌入模型要先删旧集合。本进程执行 Flyway。连接池最大 20、最小空闲 5。
- `agentscope.distributed.mysql.enabled=true`，`create-if-not-exist` 保持 `false`。
- 模型默认 `dashscope:qwen-plus`，可用环境变量 `AGENTSCOPE_MODEL` 覆盖。密钥不要写进这个文件。
- `application.yml` 只留进程骨架（停机、上传上限、agent-id、Flyway/MyBatis、Nacos 地址）。业务与连接默认在 `deploy/nacos/`；无 Nacos 时取消注释 `application.yml` 文末兜底块。
- `NACOS_ADDR` 为空时不建提示词客户端，也不拉启动配置。配了地址则启动必须拉到 `agent-bootstrap-infra.yaml`（连接/密钥，不热更）与 `agent-bootstrap.yaml`（业务旋钮，可热更），group `AGENT_PLATFORM`，缺一退出。允许键写在 `classpath:nacos-allowlist.yml`；非法热更拒收并保留上一份。提示词仍走 AI Prompt 监听，拉失败用兜底。不做服务发现。MCP：业务白名单前缀 `agentscope.runtime.mcp.servers`；用户进程 companion 已接线（空清单不连；`${ENV}` 占位；`required` 硬/软失败）；**不做 MCP 服务发现**；密钥不进 Nacos 明文。见 `docs/mcp-落地.md`。Sandbox：白名单前缀 `agentscope.runtime.sandbox`；默认关；`enabled=true` 时仅 companion 换 DockerFilesystem（缺 Store / `docker version` 硬失败）；echo 仍 Remote；Docker socket/凭证不进 Nacos。见 `docs/sandbox-落地.md`。Wakeup：白名单前缀 `agentscope.runtime.wakeup`；默认关；`enabled=true` 时用户进程启进程内 Bus + Dispatcher（仅 companion）；`POST /api/internal/wakeups` + `AGENTSCOPE_WAKEUP_TOKEN`；令牌不进业务热更明文。见 `docs/wakeup-落地.md`。

`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`AGENTSCOPE_REDIS_HOST`、`MILVUS_URI` 必须在环境里提供。密码用 `SPRING_DATASOURCE_PASSWORD`，可以为空。

## 测试

`mvn test`。JDK 21。

| 测试 | 断言 |
| --- | --- |
| `ArchitectureTest` | 语义不依赖运行层和接入层；运行层不碰语义实现包；状态层不碰语义、运行层和接入层；向量层不碰状态、语义、运行层和接入层；接入层不引用 harness；控制器不访问持久化包。导入的类必须包含各模块的代表类型，避免规则空过 |
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

## 不放这里的东西

业务 Bean、DistributedStore 装配、提示词和权限。那些分别留在 `agent-state`、`agent-semantic` 和 `agent-runtime`。本模块只聚合。
