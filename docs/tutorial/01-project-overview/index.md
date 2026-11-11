---
no: 1
slug: dream-saas-project-overview
title: AgentScope 落地实战 01｜Dream-SaaS：两个进程、十个模块的多 Agent 平台底座
csdn_title: AgentScope Java 开源项目 Dream-SaaS：项目结构、能力清单与本地启动
series: 第一季 · 破冰
tag: lesson-01
version: v0.1.0
date: 2026-11-05
keywords: AgentScope, Java 21, Spring Boot 3, 多 Agent 平台, 开源项目, RAG
summary: Dream-SaaS 是基于 AgentScope Java 2.0.4 的开源多 Agent 平台底座，包含两个进程、两个前端和十个 Maven 模块。本文给出仓库定位、已具备的产品能力、技术栈、部署拓扑、本地启动路径，以及当前明确未实现的部分。
cover: assets/cover-900x383.png
next: AgentScope 2.0.4 概念地图：200 行跑通最小闭环
---

> **《AgentScope 落地实战》第 1 篇。** 代码基线 `lesson-01`，仓库：<https://github.com/logosssss/dream-agentscope>
>
> 下一篇：AgentScope 概念地图，200 行跑通最小闭环。

## 项目定位

dream-agentscope 是 Dream-SaaS 的开源仓库，基于 [AgentScope Java](https://github.com/agentscope-ai/agentscope-java) 2.0.4 构建的多 Agent 平台底座，采用 Apache-2.0 许可，在线站点为 <https://www.dream-saas.com/>。

它解决的不是"如何调用大模型"，而是"Agent 能力如何进入一个有租户、有计费、有审批、有审计的生产系统"。仓库在 AgentScope 之上补齐了六类产品化组件：

| 组件 | 作用 |
|---|---|
| 身份 | 密码注册登录、微信扫码登录、会话令牌 |
| 隔离 | 租户与用户维度贯穿对话、知识库、附件、配额 |
| 成本 | 日请求 / token / 费用 / 并发运行四类上限，分钟级限流 |
| 管控 | 工具权限三态（ALLOW / ASK / DENY）与审批流 |
| 留痕 | 运行、步骤、审计、用量四类记录 |
| 运维 | 配置中心热更、管理端、保留策略登记 |

以一次对话为例：请求进入用户进程后，先由 `CallerTokenFilter` 校验 `X-Caller-Token`，再由 `AccessIdentityFilter` 解析 `Authorization: Bearer` 并写入线程身份，接着在 `ChatAppService` 完成额度判断、运行占位与幂等检查，最后交给 runtime 构建 Agent 实例、把流式事件转成 SSE。多租户隔离与成本约束都压在这条链上，也是后续各篇逐段拆解的对象。

## 仓库结构

两个后端进程、两个前端工程、十个 Maven 模块。

| 组成 | 路径 | 端口 | 说明 |
|---|---|---|---|
| 用户进程 | `agent-bootstrap` | 8080 | 对话、登录、知识库、附件等业务 API；执行 Flyway |
| 管理进程 | `agent-admin` | 8100 | 租户、额度、工具权限、审批、审计等运维 API；不执行迁移、不跑对话 |
| 用户前端 | `web` | 5173 | 介绍页、对话、附件、知识库 |
| 管理前端 | `web-admin` | 5174 | 运维界面 |

十个模块中，`agent-bootstrap` 与 `agent-admin` 是两个可独立启动的进程入口，其余八个为库：`agent-access` 提供 HTTP 入口，`agent-runtime` 为执行内核，`agent-semantic` 存放提示词与工具目录，`agent-common` / `agent-audit` / `agent-state` / `agent-vector` / `agent-object-store` 为横切支撑。依赖方向与边界约束见第 13 篇。

## 已具备的产品能力

| 能力 | 实现要点 |
|---|---|
| 注册 / 登录 / 退出 | 会话存于 Redis；身份只认 `Authorization: Bearer`，不采用请求头中的租户与用户 |
| 对话 | 同步 JSON 与 SSE 流式两条路径；`HarnessAgent` 按请求创建并在结束后关闭 |
| 待确认（审批） | 工具决策为 ASK 时产生站内确认；批准后需重新发送上一条消息，下一轮才执行工具 |
| 会话与运行查询 | 会话列表、消息、运行与步骤只读查询，范围限定本人本租户 |
| 附件 | 上传、列表、下载、停用与恢复；对象存储可落 local / OSS / S3 / MinIO |
| 知识库 | 建库、纯文本与文件入库、索引状态、对话侧 `knowledge_search` 工具 |
| 额度与限流 | 日请求、token、费用、并发运行四类上限；分钟级限流；注册登录同样受限 |
| 管理端 | 租户与用户、各类额度、工具目录与权限、审批、模型配置与单价、审计只读、账单状态机 |
| 微信扫码登录 | 密钥配齐后可用；小程序确认路径不校验 Caller Token，仍校验票据与微信 code |
| 配置中心 | Nacos 双份 yaml：infra 启动加载一次，业务配置可在白名单内热更 |

## 技术栈与运行依赖

| 类别 | 版本 / 要求 |
|---|---|
| JDK | 21 |
| Spring Boot | 3.5.5 |
| AgentScope | 2.0.4 |
| 构建 | Maven 3.6+ |
| 持久层 | MyBatis-Plus 3.5.17 |
| 向量库 | Milvus 2.6.17（Java SDK） |
| 配置中心 | Nacos Client 3.2.1 |
| 可观测 | OpenTelemetry 1.61.0（可选） |
| 模块约束 | ArchUnit 1.4.1 |
| 前端 | Node 20+，包管理器统一 pnpm |
| 中间件 | MySQL、Redis、Milvus 三项必须可达 |
| 模型 | 默认坐标 `dashscope:qwen-plus`，密钥读取 `DASHSCOPE_API_KEY` |

三类外部依赖的启动行为不同：

| 依赖 | 启动期行为 |
|---|---|
| MySQL / Redis / Milvus | 两个进程启动即探测，任一项不可达则抛异常退出（`RequiredInfrastructureRunner`），避免"启动成功但对外随机 503" |
| Nacos | `NACOS_ADDR` 或 `agentscope.nacos.server-addr` 为空时跳过连接，回落到本地 `application.yml`；有值才连配置中心 |
| 模型服务 | 坐标与密钥在请求期解析，`DASHSCOPE_API_KEY` 缺失不阻断启动，首次对话或向量化时才失败 |

测试阶段把 `agentscope.startup.dependencies.enabled` 置为 `false`，不连接真实中间件。

## 部署拓扑

| 调用方 | 被调方 | 端口 | 说明 |
|---|---|---|---|
| 浏览器 | `web` | 5173 | 介绍页、对话、附件、知识库 |
| 浏览器 | `web-admin` | 5174 | 运维界面 |
| 浏览器 | Nginx（可选） | — | 代填 `X-Caller-Token` 后转发到 8080 |
| `web` | `agent-bootstrap` | 8080 | 业务 API |
| `web-admin` | `agent-admin` | 8100 | 运维 API |
| `agent-bootstrap` / `agent-admin` | MySQL、Redis、Milvus | — | 启动即探测，任一项不可达则退出 |
| `agent-bootstrap` / `agent-admin` | Nacos | — | 有地址才连，读取 infra 与业务两份 yaml |
| `agent-bootstrap` | 模型服务 | — | 坐标与密钥在请求期解析 |

只有 `agent-bootstrap` 会调用模型服务：`agent-admin` 的 pom 不依赖 `agent-runtime`，源码中也没有 `ChatModel` / `AgentExecutor` 引用。两个进程互不调用，管理端不可用时对话链路不受影响。

一次对话请求的路径：

```text
POST /api/chat/stream
  → CallerTokenFilter      校验 X-Caller-Token
  → AccessIdentityFilter   校验 Bearer，写入线程身份
  → ChatAppService         额度、占位、幂等
  → AgentExecutor.stream   runtime 唯一入口
  → HarnessAgentFactory    每次构建 Agent 实例
  → AgentStreamEvent       接入层转为 SSE
```

## 本地启动

前置条件：JDK 21、Maven、Node 20+、MySQL、Redis、Milvus，以及一个可用的 Nacos（推荐）。

```bash
# 1. 环境变量
cp .env.example .env

# 2. Nacos 控制台创建 group AGENT_PLATFORM，粘贴四份配置
#    agent-bootstrap-infra.yaml / agent-bootstrap.yaml
#    agent-admin-infra.yaml / agent-admin.yaml

# 3. 用户进程首次启动会执行 Flyway，随后导入演示租户
mysql -u ... -p agentscope < deploy/mysql/seed-demo-tenant-user.sql

# 4. 启动
mvn -pl agent-bootstrap -am spring-boot:run
mvn -pl agent-admin -am spring-boot:run

# 5. 探活
curl -s http://127.0.0.1:8080/health/live
curl -s http://127.0.0.1:8080/health/ready
```

`/health/ready` 要求三项中间件可达。配置优先级为**环境变量 > Nacos > 本地 yml**；infra 配置不热更，改连接串与密钥需重启。

前端分别执行 `pnpm install && pnpm dev`。生产构建时不设置 `VITE_CALLER_TOKEN`，由 Nginx 通过 `X-Caller-Token` 代填。

## 当前未实现的部分

- **RocketMQ**：`agentscope.task.mode=mq` 为占位实现，切换后进程无法启动；任务当前走 `sync`。
- **服务发现与 MCP**：Nacos 仅作为配置中心。
- **infra 热更新**：仅业务 yaml 在白名单内可热更。
- **保留策略、删除审批、调度消费**：支持登记与状态变更，不执行实际清理。
- **管理端账号登录页**：当前依赖 Admin Token。
- **内置 Web 工具**：进程侧已关闭 `web_fetch` / `web_search` / shell 工具，业务样例对前两者配置为 DENY。

## 适用与不适用

**适用**：需要把 Agent 接入多租户系统、要求额度与审批可控、希望保留完整运行记录的后端团队；作为 AgentScope Java 的工程化参考实现。

**不适用**：只需要单机对话 Demo 的场景；需要开箱即用的低代码 Agent 编排平台；需要 MCP 工具生态的场景（当前未接入）。

## 系列说明

《AgentScope 落地实战》共四个部分：项目与框架入门、RAG 落地、多 Agent 编排、上线与运维。全部内容对应仓库中已存在的实现，配置值与默认值直接取自代码与 `deploy/` 模板。

---

**下一篇**：AgentScope 2.0.4 概念地图——Agent、Toolkit、Memory、Middleware 的职责边界，以及一个 200 行以内的最小闭环。

**有问题评论区见，欢迎交流~**

项目：https://www.dream-saas.com/ ｜ 仓库：https://github.com/logosssss/dream-agentscope ｜ 系列目录：https://github.com/logosssss/dream-agentscope/blob/main/docs/tutorial/series-plan.md
