# 工程护栏 P4：配置语义校验与文档导航

状态：**已完成**（2026-11-21）。承接 [`engineering-p3-testing-落地.md`](./engineering-p3-testing-落地.md)。

日期：2026-11-21。
背景：前一块把「顺序」和「边界」变成了可测的东西，但还留着两个隐性负债——
① Nacos 热更只保证**语法**能绑上（yaml 解析 + 白名单），语义非法（`max-iters=0`、`timeout-seconds=0`、空 `model`）会被照单收下，然后在下一次对话里以「模型不调、回复为空」的形式暴露；
② 文档已经有三十多篇，导航靠人记，`doc/` 与 `docs/` 双目录并存，搬家时链接烂掉没人发现。

## 已锁定

| 项 | 口径 |
| --- | --- |
| 校验入口 | `AgentRuntimeProperties#validate()`；`NacosBusinessConfigWatcher#rebind()` 绑完 `agentscope.runtime` 立刻调用 |
| 失败语义 | 抛 `IllegalStateException`，`onChange` 已有的 `catch (RuntimeException)` 把它当「推送被拒」，`replace(previous)` + 重新 `rebind()` 回滚到上一份配置 |
| 校验项 | `model` 非空白、`agent-id` 非空白、`max-iters >= 1`、`timeout-seconds >= 1` |
| 边界值 | `1` 合法（只拒 `< 1`）——最小可用配置不该被当成非法 |
| 与 `compilePermissions` 的分工 | 权限规则在 `rebind()` 里已有同款「立刻编译一遍」的写法，P4 只是给 runtime 属性补上对等的一条 |
| 文档索引 | 新增 [`docs/INDEX.md`](./INDEX.md)：按「工程调整 / 能力落地 / 门禁 / 手册图册教程 / 参考长文」分组 |
| 链接检查 | `agent-bootstrap/src/test/java/com/zhu/bootstrap/DocsLinkTest.java`：扫根 `README.md`、`docs/**/*.md`、各模块 `README.md`，相对链接必须命中真实文件 |
| 参考长文归位 | `doc/` 三份参考长文 → `docs/reference/`，文件名去序号与空格（`agentscope-architecture-guide.md`、`frontend-engineering-plan.md`、`product-rag-landing.md`） |
| 迁移方式 | `git mv` 保留历史；旧链接（`./doc/...`、`../doc/...`、`../../doc/...`）逐处改写 |

## 明确不做

| 编号 | 不做 | 原因 |
| --- | --- | --- |
| X1 | 不把 `Mcp` / `Sandbox` / `Wakeup` 拆成三个独立 `@ConfigurationProperties` 类 | 三组在生产路径上只有一个 MCP 消费点（`CompanionResidentAgentBinding:133`）+ 两个仅测试路径；拆开要改 6 个文件（含产品装配与 `WakeupCoordinator`），收益只是分层美观 |
| X2 | 不引入 jakarta validation 注解 + `@Validated` | `Binder.bind` 的手工 rebind 不经过 Spring 的 `ConfigurationPropertiesBindingPostProcessor`，注解在热更场景根本不生效；显式方法才是唯一真起作用的校验 |
| X3 | 不在启动阶段强校验 | 启动值由 `deploy/nacos/*.yaml` 模板保证；补启动校验会把「模板写错」变成「进程起不来」，与 P0 的降级启动口径冲突 |
| X4 | 不追求 100% 链接覆盖 | 只检查 Markdown 相对链接；HTML 教程、外链可用性、锚点是否命中标题都不查（收益低、误报高） |
| X5 | 不让 `INDEX.md` 自动生成 | 自动生成需要构建期写文件、还得定分组，反而多一份需要维护的生成器；手写 + 链接检查足够 |

## 改动

1. `agent-runtime/src/main/java/com/zhu/runtime/config/AgentRuntimeProperties.java`：新增 `public void validate()`（javadoc 说明「语法合法但语义非法」的推送场景）。
2. `agent-bootstrap/src/main/java/com/zhu/bootstrap/nacos/NacosBusinessConfigWatcher.java`：`rebind()` 里 `binder.bind("agentscope.runtime", ...)` 之后加 `runtime.validate();`（注释说明坏配置在这里被拒、外层回滚）。
3. `agent-runtime/src/test/java/com/zhu/runtime/config/AgentRuntimePropertiesTest.java`：6 例——默认值通过、空 `model` 拒、空 `agent-id` 拒、`max-iters=0` 拒、`timeout-seconds=0` 拒、边界值 `1` 通过。
4. `agent-bootstrap/src/test/java/com/zhu/bootstrap/DocsLinkTest.java`：仓库根定位（向上找含 `<modules>` 的 `pom.xml`）+ 相对链接解析 + 示例写法（通配符、`$`、`<`）跳过。
5. `docs/INDEX.md`：新建，五组导航 + 「怎么加文档」三条。
6. `docs/reference/`：三份参考长文归位并改 ASCII 文件名；同步改 `README.md:371-372`、`docs/agentscope-landing-review.md:5`、`docs/handbook/01-repo-map.md:27`、`docs/handbook/05-admin-frontend-deploy.md:14` 的链接。
7. `docs/engineering-p1-readmodel-落地.md`、`docs/engineering-p2-product-module-落地.md`：补 `状态：**已完成**` 行（P2 标题也从「进行中」改「已完成」），与 P3/P4 同格式。
8. `docs/engineering-p0-guardrails-落地.md`：块索引表补 P4 行、P3 改「已完成」；`README.md` 文档段加 `docs/INDEX.md` 入口。

## 验收实测（2026-11-21 本机）

| 验收项 | 结果 |
| --- | --- |
| `mvn -B -ntp -Pstrict -pl agent-runtime -am -Dtest=AgentRuntimePropertiesTest test` | 6 例通过（0.125s），`agent-runtime` 模块 5.6s |
| `mvn -B -ntp -Pstrict -pl agent-bootstrap -am -Dtest=DocsLinkTest test` | 1 例通过（0.145s），全仓相对链接 0 broken |
| `mvn -B -ntp -Pstrict test "-Dtest=!OssObjectStoreTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | 521 例，0 失败 0 错误（P3 的 514 + 本项目 7） |
| `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | BUILD SUCCESS（18 个 reactor 项，含 `agent-bootstrap` 的 spring-boot repackage） |

## 残留

1. 语义校验只覆盖 `agentscope.runtime` 的四个字段；`Mcp` / `Sandbox` / `Wakeup` 内部（例如 transport 缺 url、command 与 url 同时为空）仍由各接线方在首次使用时判空。
2. `DocsLinkTest` 不校验标题锚点，`README.md#架构鸟瞰` 这类链接改名后仍会「文件存在但锚点失效」。
3. `docs/tutorial/` 里的 HTML 版本与图片不参与链接检查（它们是成品）。
4. P0–P3 四份文档里引用的「旧 `doc/` 路径」只在 P4 这一处做了说明；再往前翻历史记录看到的仍是旧路径，属历史描述，不改写。

## 下一步

P0–P4 到此收口：构建护栏、共享表、产品缝、闸门顺序、配置校验与文档导航各自的验收命令都在本系列文档里可复跑。
往后如果继续，优先级是 **Testcontainers 端到端**（P3 的 X1，本机无 Docker）与 **`agent-access` 之外的模块补测试**（`semantic` / `state` / `cache` / `products` 目前仍是 0 例）。
