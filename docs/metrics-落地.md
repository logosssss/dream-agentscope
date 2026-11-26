# 指标：模型调用与一轮对话的结局

状态：**已完成**（2026-11-26）。范围：两个进程的指标端点与运行层埋点。**不是**链路追踪（那走 OTel）。

## 为什么有这一块

在此之前，线上想问四个问题都没有答案：

| 问题 | 之前只能 | 现在 |
| --- | --- | --- |
| 模型慢不慢？P99 多少？ | 翻 `token_usage.latency_ms` 算 | `agentscope.model.latency{model}` 直方图 |
| 失败率多少？是闸拒的还是执行炸的？ | 翻 `agent_run.status` | `agentscope.turn{channel,outcome}` + `http.server.requests` |
| token 烧得多快？ | 翻 `token_usage` 汇总 | `agentscope.model.tokens{model,direction}` |
| 进程本身健康吗（线程、堆、GC、连接池）？ | 只能看 `/health/ready` 的 UP/DOWN | `jvm.*`、`hikari.*` 等 actuator 自带指标 |

「管理端运维面」补的是**数据面**（谁、什么时候、做了什么），这块补的是**运行面**（现在多热、多快、多失败）。

## 已锁定

| 项 | 定法 |
| ---- | ---- |
| 依赖 | `micrometer-core` 只在 `agent-runtime`（只用 `MeterRegistry` API）；`spring-boot-starter-actuator` + `micrometer-registry-prometheus` 只在两个可部署进程 |
| 端点位置 | **独立管理端口 + 只绑回环**：用户进程 `127.0.0.1:8094`，管理进程 `127.0.0.1:8101`。不经 Nginx、不要 Caller Token |
| 暴露内容 | `health`、`info`、`prometheus` 三个，其余不暴露 |
| 低基数 | 标签只有 `model` / `channel` / `outcome` / `direction`；**不放 tenantId / userId**，否则时序库被撑爆 |
| 指标名 | `agentscope.turn`（计数）、`agentscope.turn.duration`（计时）、`agentscope.model.latency`（计时）、`agentscope.model.tokens`（按 `direction` 分开计） |
| 失败处理 | 指标写失败一律吞掉：记账与对话不允许因为指标出问题 |
| 埋点位置 | 模型侧在 `AuditUsageMeter`（用量唯一咽喉，库写失败也会记指标）；对话结局在 `ChannelGateway` 四个入口（同步 catch，流式 `doFinally`） |
| 缺注册表时 | `TurnMetrics` 空转；单测与没引 actuator 的装配照常跑 |

## 改动

1. 父 pom 无需加版本（Boot BOM 管 micrometer/actuator）。
2. `agent-runtime/pom.xml`：`micrometer-core`。
3. `agent-bootstrap/pom.xml`、`agent-admin/pom.xml`：actuator + prometheus registry。
4. `agent-runtime/.../application/metrics/TurnMetrics.java`（新）：两个埋点 + 空转；标签常量。
5. `AuditUsageMeter`：第四个参数（`@Nullable TurnMetrics`），保留三参构造给单测；记账最后一步记指标。
6. `ChannelGateway`：注入 `TurnMetrics`，同步两入口包 try/catch，流式两入口 `doFinally` 按 `SignalType` 判结局。
7. 两个 `application.yml`：`management.server.address/port` 与暴露清单。
8. 测试：`TurnMetricsTest` 4 例（耗时与 token 标签、按通道记结局、空标签折 unknown、无注册表空转）。

## 抓取示例

```yaml
# prometheus.yml（在部署机本地跑）
scrape_configs:
  - job_name: agentscope-user
    static_configs: [{targets: ['127.0.0.1:8094']}]
  - job_name: agentscope-admin
    static_configs: [{targets: ['127.0.0.1:8101']}]
```

```bash
curl -s http://127.0.0.1:8094/actuator/prometheus | grep '^agentscope_'
```

## 明确不做

- **X1** 不在仓库里放 Grafana 面板 JSON 与告警规则：先让数能出来，面板按实际部署再调。
- **X2** 不给指标端点加鉴权：它只绑回环，加 token 只会让 Prometheus 更难配。要暴露到内网就上 Nginx + 只读凭据，别改 `0.0.0.0`。
- **X3** 不打租户/用户标签：需要按租户看得去查库或按租户做聚合任务。
- **X4** 不做链路追踪：OTel 那条路另算（`agentscope.otel.endpoint`），两者不合并。
- **X5** 不把已有的 `/health/live|ready` 换掉：它们是给探活用的一跳判断，actuator 的 health 是另一套。

## 残留

1. **成本指标缺**：金额在管理进程由 `MysqlTokenUsageWriter` 按价算，用户进程埋点拿不到，需要另一处埋点或按 token × 单价在 Prometheus 侧算。
2. **部署侧要放开回环抓取**：现网进程由 `nohup` 起，管理端口随进程一起监听；确认防火墙只对本机开放（默认 127.0.0.1 已足够），且 Nginx **不要**代理 8094/8101。
3. **闸门拒绝没有单独指标**：目前靠 `http.server.requests{status}` 看；要区分「额度拒 / 并发拒 / 会话占用」得在闸门里再埋一层（下一轮）。
4. **流式的结局是「流终态」**：`ON_COMPLETE` 记 ok，哪怕流里带了错误事件。要精确到「这一轮成没成」得看 `agent_run.status`。
