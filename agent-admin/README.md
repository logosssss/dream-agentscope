# agent-admin

管理进程。给运维看进程状态，不参与对话。和用户进程分开启动，端口 `8100`。

坐标：`com.zhu:agent-admin`。包名：`com.zhu.admin`。

## 依赖

依赖 `agent-common`、`agent-audit`、Web 和 Redis。用审计模块的查询接口读库。不依赖 `agent-runtime`、`agent-access`，也不依赖 Harness。Redis 只用于启动探测。

`agent-bootstrap` 不依赖本模块。用户进程扫描 `com.zhu` 时，classpath 上没有这些类，管理接口不会混进对话进程。

## 启动

在仓库根、使用 JDK 21：

```text
mvn -pl agent-admin -am spring-boot:run
```

`src/main/resources/application.yml` 把端口固定为 `8100`。本进程排除 Flyway，不改表。MySQL、Redis、Milvus 没配或连不上就退出。连接池最大 20、最小空闲 5，和用户进程加在一起要算进 MySQL `max_connections`。停机时先不再接新 HTTP，最多再等 30 秒。

## 接口

| 路径 | 作用 |
| --- | --- |
| `GET /health/live` | 只表示管理进程还在，不查用户进程、数据库或模型 |
| `GET /health/ready` | 探测 MySQL、Redis、Milvus。任一项失败 HTTP 503。测试里三项为 SKIPPED |
| `GET /api/admin/info` | 角色为 `admin` |
| `GET /api/admin/audits` | 最近审计，默认 50 条，最多 100 条 |
| `GET /api/admin/approvals` | 决定为 `PENDING` 的审批，条数规则同上 |
| `POST /api/admin/approvals/{approvalId}/decision` | 把该单改成 `APPROVED` 或 `REJECTED`。通道固定为 `CONSOLE`。不执行工具。单据不存在时返回找不到 |
| `POST /api/admin/billing/drafts` | 按租户和区间生成一张草稿账单。MySQL 关闭时返回不可用。不开票 |
| `POST /api/admin/billing/confirmations` | 把对应草稿改成已确认，不重算金额。没有账单时返回找不到 |
| `GET /api/admin/model-prices` | 最近的模型价格，默认 50 条，最多 100 条。MySQL 关闭时返回不可用 |
| `POST /api/admin/model-prices` | 再插入一行价格。不改旧行。取价时用生效开始最晚且仍有效的一行 |

业务接口要带 `X-Caller-Token`，值来自 `AGENTSCOPE_ADMIN_TOKEN`，和用户进程的令牌分开。没配或对不上时 HTTP 401。存活和就绪不校验。这不是用户登录。

扫描包只有 `com.zhu.admin`。另外显式引入 `AuditQuery`、`ApprovalDecider` 和 MySQL 打开时的 Mapper 配置，不引入 `AuditRecorder`、`ApprovalRecorder`。申请单的写入仍在用户进程。用户进程不依赖本模块，管理接口不会混进对话进程。

## 还没有

用户账号登录。本进程不提供聊天接口，也不在批准后执行工具。账单开票和付款还没有。仓库不放模型单价，也不放调用方令牌。
