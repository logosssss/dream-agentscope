# 文档索引

仓库里文档分四层：**工程调整记录**（按 P0–P6 顺序做过的结构性改动）、**能力落地记录**（每个功能块一份，含「明确不做」）、**手册 / 图册 / 教程**（面向人读）、**参考长文**（含历史草案，冲突时听现码）。

约定：每份 `*-落地.md` 都在标题下写 `状态：**已完成**`；相对链接由 `agent-bootstrap` 的 `DocsLinkTest` 在 CI 里守着，改名或搬家时不会悄悄烂掉。

## 工程调整（P0 → P6）

| 文档 | 一句话 |
| --- | --- |
| [P0 构建护栏与前端工程化](./engineering-p0-guardrails-落地.md) | mvnw、enforcer、surefire 固定 `-Duser.home`、CI 三段、前端网关令牌与 lint/test |
| [P1 `agent-readmodel` 抽取](./engineering-p1-readmodel-落地.md) | 15 张跨进程共用表的 entity / Mapper / XML 收成一个只读模块 |
| [P2 `ProductModule` 契约合并](./engineering-p2-product-module-落地.md) | `agent-product-api`：一个产品只实现一次装配与语义 |
| [P3 闸门顺序与可测单元](./engineering-p3-testing-落地.md) | `GateChain` + 五个窄端口 + `agent-test-support` 替身 |
| [P4 配置语义校验与文档导航](./engineering-p4-config-docs-落地.md) | `AgentRuntimeProperties.validate()` 拒收语义非法推送；本索引与链接检查 |
| [P5 端到端测试](./engineering-p5-e2e-落地.md) | Testcontainers 起真 MySQL + Redis：Flyway → 注册 → 登录 → 附件落库 |
| [P6 数据生命周期](./engineering-p6-data-lifecycle-落地.md) | 删除请求批准后真删（含字节与向量）、审计脱敏、保留策略真清理 |
| [工程 / 模块评估](./engineering-module-review.md) | P0 之前的模块与测试现状评估（决策用，已被 P0–P4 部分推翻） |
| [AgentScope 落地评估](./agentscope-landing-review.md) | 与参考长文的漂移对照 |

## 能力落地

| 文档 | 一句话 |
| --- | --- |
| [Channel Gateway](./channel-gateway-落地.md) | 一条进程内网关按 channel 分发到 main Agent |
| [API Channel](./api-channel-落地.md) | API 通道可指定 agentId，且必须已注册 |
| [Cache](./cache-落地.md) | `agent-cache-api` 端口 + Redis 实现，业务不碰 Redisson |
| [Audit 拆分](./audit-split-落地.md) | 撤销 `agent-audit`，写入回落各模块 |
| [Packaging](./packaging-落地.md) | 12→14 模块的分包口径与依赖方向 |
| [Permission Gate](./permission-gate-落地.md) | 工具级放行 / 询问 / 拒绝 |
| [Permission Engine](./permission-engine-落地.md) | 规则编译与热更校验 |
| [Compaction](./compaction-落地.md) | 上下文压缩接入 |
| [Compaction Hook](./compaction-hook-落地.md) | 紧急溢出的窄范围兜底 |
| [Tool Result Eviction](./tool-result-eviction-落地.md) | 工具结果淘汰 |
| [Memory 长期](./memory-longterm-落地.md) | 情景记忆迁入 Harness |
| [Memory 硬删](./memory-hard-delete-落地.md) | 情景记忆与画像表下线 |
| [审批硬删](./approval-hard-delete-落地.md) | 审批表下线，改由进程内 `PendingAskFacade` 驱动 |
| [Subagent](./subagent-落地.md) | 本地子 Agent |
| [Remote Subagent](./remote-subagent-落地.md) | 远程子 Agent（学习窄专题） |
| [Skills](./skills-落地.md) | 技能装载 |
| [Skills Curator](./skills-curator-落地.md) | 技能策展 / manage |
| [MCP](./mcp-落地.md) | MCP 工具接入 |
| [Sandbox](./sandbox-落地.md) | Docker 沙箱接线（窄范围） |
| [Wakeup](./wakeup-落地.md) | 进程内 Bus + Dispatcher |
| [Bootstrap 测下沉](./bootstrap-test-sink-落地.md) | 测试从 bootstrap 往所属模块下沉的试点 |
| [README 拆条 D1](./readme-debt-d1-落地.md) | 「还没有」清单拆条 |
| [图册整理](./diagrams-落地.md) | 图册从散落到 `docs/diagrams/` |

## 门禁

| 文档 | 一句话 |
| --- | --- |
| [开源前门禁清单](./release-gate.md) | 自动化 / 冒烟 / 敏感面三段检查 |
| [门禁复验 R1](./release-gate-r1-落地.md) | 2026-11-17 的复验记录 |

## 手册 / 图册 / 教程

| 入口 | 内容 |
| --- | --- |
| [handbook](./handbook/README.md) | 五篇上手手册：仓库地图、中间件与种子、用户主链路、运行层专题、管理端与前端部署 |
| [diagrams](./diagrams/README.md) | 架构、链路、扩展、流程四张图 |
| [tutorial](./tutorial/README.md) | 面向读者的系列长文（含配图与公众号/Html 版本） |

## 参考长文（含历史草案）

| 文档 | 用法 |
| --- | --- |
| [AgentScope Java 2.0.4 产品级架构与工程落地指南](./reference/agentscope-architecture-guide.md) | 设计长文；与现码冲突时以 `docs/*-落地.md` 与 README 为准 |
| [企业级多 Agent 平台前端工程开发方案](./reference/frontend-engineering-plan.md) | 前端方案参考 |
| [产品级 RAG 功能落地文档](./reference/product-rag-landing.md) | RAG 能力参考 |

## 怎么加文档

1. 新的能力块写 `*-落地.md`，标题下写 `状态：`，结尾写「明确不做」与「后续」。
2. 在本文加一行链接（分组里挑一个）。
3. 跑 `./mvnw -pl agent-bootstrap -am -Dtest=DocsLinkTest test`，链接断了当场失败。
