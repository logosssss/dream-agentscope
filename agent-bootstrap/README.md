# agent-bootstrap

启动模块。把各模块扫进同一个进程，自己不写业务。可执行的边界测试也放在这里，因为只有这里的 classpath 同时有全部模块。

坐标：`com.zhu:agent-bootstrap`。包名：`com.zhu.bootstrap`。

## 依赖

依赖 `agent-common`、`agent-state`、`agent-semantic`、`agent-runtime`、`agent-audit`、`agent-access`。测试依赖 `spring-boot-starter-test` 和 ArchUnit。

`@SpringBootApplication(scanBasePackages = "com.zhu")` 会扫到兄弟包。如果只扫 `com.zhu.bootstrap`，接入层控制器和状态配置都不会生效。

## 启动

在仓库根、使用 JDK 21：

```text
mvn -pl agent-bootstrap -am spring-boot:run
```

本机若默认 `JAVA_HOME` 仍是 JDK 8，需要先指到 JDK 21。JDK 8 无法编译当前源码。

默认配置在 `src/main/resources/application.yml`：

- 排除数据源和 Flyway 自动配置，没有 MySQL 也能启动。
- `agentscope.distributed.mysql.enabled=false`。
- 模型默认 `dashscope:qwen-plus`，可用环境变量 `AGENTSCOPE_MODEL` 覆盖。密钥不要写进这个文件。

要启用 MySQL 状态时，同时做三件事：去掉上面的自动配置排除、配置 `spring.datasource`、把 `agentscope.distributed.mysql.enabled` 设为 `true`，并保持 `create-if-not-exist: false`。

## 测试

`mvn test`。JDK 21。

| 测试 | 断言 |
| --- | --- |
| `ArchitectureTest` | 语义不依赖运行层和接入层；运行层不碰语义实现包；状态层不碰语义、运行层和接入层；接入层不引用 harness；控制器不访问持久化包。导入的类必须包含各模块的代表类型，避免规则空过 |
| `BootstrapContextTest` | 上下文能起来；恰好一个 `AgentExecutor`；没有 `HarnessAgent` Bean；没有 `DistributedStore` Bean；`GET /health/live` 返回 UP；默认 `GET /health/ready` 跳过 MySQL 和 Redis |

## 不放这里的东西

业务 Bean、DistributedStore 装配、提示词和权限。那些分别留在 `agent-state`、`agent-semantic` 和 `agent-runtime`。本模块只聚合。
