# agent-admin

管理进程。给运维看进程状态，不参与对话。和用户进程分开启动，端口 `8100`。

坐标：`com.zhu:agent-admin`。包名：`com.zhu.admin`。

## 依赖

只依赖 `agent-common` 和 Web。不依赖 `agent-runtime`、`agent-access`，也不依赖 Harness。

`agent-bootstrap` 不依赖本模块。用户进程扫描 `com.zhu` 时，classpath 上没有这些类，管理接口不会混进对话进程。

## 启动

在仓库根、使用 JDK 21：

```text
mvn -pl agent-admin -am spring-boot:run
```

`src/main/resources/application.yml` 把端口固定为 `8100`，并排除数据源和 Flyway。没有 MySQL 也能启动。

## 接口

| 路径 | 作用 |
| --- | --- |
| `GET /health/live` | 只表示管理进程还在，不查用户进程、数据库或模型 |
| `GET /api/admin/info` | 角色为 `admin`，并说明看不到用户进程内存里的审计和待审批 |

审计记录和待审批列表现在在用户进程的内存里。换一个 JVM 会是另一份空数据，所以这里不提供查询。等它们落库以后，管理进程再读同一份 MySQL。

## 还没有

登录、审计查询、审批处理、模型配置修改。本进程不提供聊天接口。
