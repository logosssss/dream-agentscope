# agent-admin

管理进程。给运维看进程状态，不参与对话。和用户进程分开启动，端口 `8100`。

坐标：`com.zhu:agent-admin`。包名：`com.zhu.admin`。

## 依赖

依赖 `agent-common`、`agent-audit` 和 Web。用审计模块的查询接口读库。不依赖 `agent-runtime`、`agent-access`，也不依赖 Harness。

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
| `GET /api/admin/info` | 角色为 `admin`。说明查询读的是 MySQL，关闭时列表为空 |
| `GET /api/admin/audits` | 最近审计，默认 50 条，最多 100 条。MySQL 关闭时为空数组 |
| `GET /api/admin/approvals` | 决定为 `PENDING` 的审批，条数规则同上 |

扫描包只有 `com.zhu.admin`。另外显式引入 `AuditQuery` 和 MySQL 打开时的 Mapper 配置，不引入 `AuditRecorder`、`ApprovalRecorder`。写入仍在用户进程。用户进程不依赖本模块，管理接口不会混进对话进程。

## 还没有

登录、审批通过或拒绝、模型配置修改。本进程不提供聊天接口。
