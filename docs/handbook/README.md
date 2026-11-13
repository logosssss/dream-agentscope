# 学习手册（仓库内）

对内自学 / 接手用的**索引型**路径。不重写业务结论；结论真相源仍是各模块 `README`、「还没有」与 `docs/*-落地.md`。

对外发文母版在 [`docs/tutorial/`](../tutorial/)，与本目录隔离。

## 已锁定约定

| 项 | 约定 |
| --- | --- |
| 用途 | 从 0 读到深入；不绑公众号 / CSDN |
| 形态 | 阅读顺序 + 必读链接 + 问题清单 + 检查点 |
| 范围 | 全仓：双进程 + `web` / `web-admin` + `deploy` |
| 维护 | 只改路径、问题、检查点；专题结论变了改落地文，不在此双写 |
| 默认关专题 | MCP / Sandbox / Wakeup / Skills Curator 等：**只读**搞清约束即可，不强制本机打开 |
| 硬检查点 | 标准集（见下）；部署文档要读，**不强制**现网发布 |

## 五段脊柱

| 段 | 文件 | 内容 |
| --- | --- | --- |
| ① | [01-repo-map.md](./01-repo-map.md) | 仓库与双进程地图（正文以根 README 为准） |
| ② | [02-middleware-seed.md](./02-middleware-seed.md) | 本地中间件、Nacos、种子数据 |
| ③ | [03-user-main-path.md](./03-user-main-path.md) | 用户主链路：登录 → 对话 → 落库 |
| ④ | [04-runtime-topics.md](./04-runtime-topics.md) | 运行层与已收口专题（依赖序全覆盖） |
| ⑤ | [05-admin-frontend-deploy.md](./05-admin-frontend-deploy.md) | 管理端、双前端、部署运维 |

建议按序；② 不过关时不要硬闯③ 的联调检查点。

## 章模板（每段 / 每专题）

1. **学习目标**  
2. **必读链接**（有序）  
3. **要搞清的问题**（3～7 个）  
4. **检查点**（无实操则写「只读」）

## 硬检查点（标准集）

命令与配置细节以根 [`README.md`](../../README.md) 为准，此处只点名：

1. 仓库根目录 JDK 21：`mvn test` 全绿（测试不连真实 Redis / MySQL / Milvus / Nacos）  
2. 本机 Redis、MySQL、Milvus 可连；按需 Nacos；执行演示种子 SQL  
3. 用户进程 `8080`：`/health/live` 与 `/health/ready`  
4. 登录后打通一轮 **WEB** 流式对话（`/api/chat/stream` 或搭子页）  
5. 管理进程 `8100`：live / ready  
6. `web`（5173）与 `web-admin`（5174）能进主界面  
7. `deploy` 相关说明读完即可，不强制跑现网发布脚本

## 明确不做

- 不在 handbook 另写第三套架构长文  
- 不把本目录当对外教程工作流  
- 不强制开启默认关闭能力，不把手册写成部署演练全集  
- 不宣称 IM 消息通道、Remote Subagent 生产、`mode=mq`、API Key 鉴权等未交付能力  

## 状态

第一版骨架（总 README + 五段索引）已交齐。后续只按阅读反馈改问题质量与断链。
