# agent-bootstrap

启动模块。把各模块扫进同一个进程，自己不写业务。可执行的边界测试也放在这里，因为只有这里的 classpath 同时有全部模块。

坐标：`com.zhu:agent-bootstrap`。包名：`com.zhu.bootstrap`。

## 依赖

依赖 `agent-common`、`agent-state`、`agent-semantic`、`agent-runtime`、`agent-audit`、`agent-access`、`milvus-sdk-java`。测试依赖 `spring-boot-starter-test` 和 ArchUnit。管理进程不带这个客户端。

`@SpringBootApplication(scanBasePackages = "com.zhu")` 会扫到兄弟包。如果只扫 `com.zhu.bootstrap`，接入层控制器和状态配置都不会生效。

## 启动

在仓库根、使用 JDK 21：

```text
mvn -pl agent-bootstrap -am spring-boot:run
```

本机若默认 `JAVA_HOME` 仍是 JDK 8，需要先指到 JDK 21。JDK 8 无法编译当前源码。

默认配置在 `src/main/resources/application.yml`：

- 数据源、Redis 主机和 Milvus 地址来自环境变量。没配或连不上就退出。连通后创建 `knowledge` 集合，字段含 `user_id` 和 `agent_id`，向量维度默认 1024。不写入、不检索。换嵌入模型要先删旧集合。本进程执行 Flyway。连接池最大 20、最小空闲 5。
- `agentscope.distributed.mysql.enabled=true`，`create-if-not-exist` 保持 `false`。
- 模型默认 `dashscope:qwen-plus`，可用环境变量 `AGENTSCOPE_MODEL` 覆盖。密钥不要写进这个文件。
- `NACOS_ADDR` 为空时不建提示词客户端。配了也只监听提示词，拉失败用兜底，不让进程退出。

`SPRING_DATASOURCE_URL`、`SPRING_DATASOURCE_USERNAME`、`AGENTSCOPE_REDIS_HOST`、`MILVUS_URI` 必须在环境里提供。密码用 `SPRING_DATASOURCE_PASSWORD`，可以为空。

## 测试

`mvn test`。JDK 21。

| 测试 | 断言 |
| --- | --- |
| `ArchitectureTest` | 语义不依赖运行层和接入层；运行层不碰语义实现包；状态层不碰语义、运行层和接入层；接入层不引用 harness；控制器不访问持久化包。导入的类必须包含各模块的代表类型，避免规则空过 |
| `BootstrapContextTest` | 上下文能起来；恰好一个 `AgentExecutor`；没有 `HarnessAgent` Bean；没有 `DistributedStore` Bean；`GET /health/live` 返回 UP；测试配置下 `GET /health/ready` 跳过 MySQL、Redis 和 Milvus |

## 不放这里的东西

业务 Bean、DistributedStore 装配、提示词和权限。那些分别留在 `agent-state`、`agent-semantic` 和 `agent-runtime`。本模块只聚合。
