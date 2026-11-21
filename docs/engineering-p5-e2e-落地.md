# P5 端到端测试（真 MySQL + 真 Redis）

状态：**已完成（本机无 Docker，实际执行在 CI）**

## 为什么有这一块

P0–P4 修的是「结构」：模块边界、单一 owner、闸门顺序、配置校验。但有一个洞一直在：
**没有人证明这些零件能一起跑**。P3 的 X1 就写着「Testcontainers E2E 本机无 Docker，顺延」。

断言的边界也因此一直靠 mock：Flyway 的 41 个迁移与 readmodel 的 Mapper 只做过「列名 ⊆ DDL」的静态比对，
没有真的对着 MySQL 跑过；登录、会话、附件这些链路只有单元测试与 mock 的 `BootstrapContextTest`（关库启动）。

这一块补的就是这条链路：**起两个容器，跑真迁移，然后用 HTTP 走一遍用户真实会走的路径**。

## 已锁定

| 项 | 决定 |
| --- | --- |
| 容器 | `mysql:8.4` + `redis:7.4-alpine`，用 Testcontainers 起停，不依赖本机中间件 |
| 位置 | `agent-bootstrap/src/test/java/com/zhu/bootstrap/MySqlRedisE2ETest.java`（端到端要整个进程，放装配模块） |
| 开关 | `@Testcontainers(disabledWithoutDocker = true)`：本机无 Docker 时**跳过**而不是失败 |
| 覆盖 | Flyway 迁移（`flyway_schema_history` ≥ 41 条 success）→ 注册 → `/api/me` → 重新登录 → 附件上传/下载 → `tenant_user` / `login_credential` / `attachment` 落库断言 → 额度接口 |
| 不覆盖 | 模型调用（`DASHSCOPE_API_KEY` 不在 CI）——对话链路已由 [P8](./engineering-p8-chat-e2e-落地.md) 用执行缝替身补上 |
| 依赖 | `agent-bootstrap` 加 `org.testcontainers:junit-jupiter`、`org.testcontainers:mysql`（版本由 Spring Boot BOM 管） |
| 数据隔离 | 每个容器一份全新库；租户行由测试自己 `INSERT`（迁移不种数据，靠管理端登记） |
| 与 `BootstrapContextTest` 的关系 | 互补：那个证明「什么都不连也能起」，这个证明「都连上能干活」 |

## 明确不做

- **X1 不用 H2 代替 MySQL**：H2 对 `utf8mb4_bin`、FULLTEXT、`DATETIME(3)`、`JSON` 的兼容性不足，跑通没有意义。
- **X2 不引入模型桩来假装对话**：要么用真模型（要密钥、要花钱、不稳定），要么不测。留空比假绿好。（P8 换了一条路：替身执行缝而不是模型，见该文档。）
- **X3 不把 E2E 塞进 `boundary` 快速段**：容器起停 20–40 秒，边界护栏必须保持秒级。
- **X4 不做容器复用（`withReuse`）**：CI 每次都是干净 runner，复用只会在本地留下脏状态。
- **X5 不扩成人人必跑**：本地开发不需要 Docker 也能跑全量单测。

## 改动清单

1. 新增 `MySqlRedisE2ETest`（约 180 行）：容器 + `@DynamicPropertySource` 接线 + 一条端到端用例。
2. `agent-bootstrap/pom.xml` 增加两个 `test` 作用域的 Testcontainers 依赖。
3. `README.md`「测试与门禁」补一段说明（含「无 Docker 自动跳过」与「不覆盖模型调用」）。
4. `docs/engineering-p3-testing-落地.md` 的 X1 改成「已由 P5 落地」。
5. 本文件 + `docs/INDEX.md` 的 P5 行。

## 验收实测

| 项 | 结果 |
| --- | --- |
| 本机（无 Docker）`mvn -Pstrict -pl agent-bootstrap -am -Dtest=MySqlRedisE2ETest test` | `Tests run: 1, Skipped: 1`，**BUILD SUCCESS**（跳过而非失败） |
| 本机 `test-compile` | BUILD SUCCESS（126 个测试源文件编译通过） |
| CI `verify` job | Ubuntu runner 有 Docker，容器会真起；首次运行结果见 PR 的 Actions 记录 |
| 全量回归（含本类跳过） | 与 P4 基线一致：`Tests run: 521` 级别全绿 |

## 残留

1. ~~对话链路仍未端到端~~：已由 [P8](./engineering-p8-chat-e2e-落地.md) 用 `@MockitoSpyBean ChannelGateway` 补上，`agent_run` / `chat_message` / `agent_session_meta` 都真落库。
2. **本机永远跳过**：本机装 Docker Desktop 后即可真跑，届时这条测试可能暴露迁移与 MySQL 版本相关的真实问题。
3. **CI 首次运行未知**：容器镜像拉取、`mysql:8.4` 初始化时间都可能需要调；若超时优先加 `--start-period` 而非降级成跳过。

## 下一步

回到产品线：三个「可登记不执行」的缺口（保留策略真清理、删除批准后真删、调度真消费）里，先做数据生命周期的前两个。
