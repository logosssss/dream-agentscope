# 工程护栏 P0 落地（已锁定）

日期：2026-11-21。
背景：`docs/engineering-module-review.md` 之后，仓库缺的是**构建期**约束而不是新功能：没有 wrapper、没有 enforcer、
没有 CI，前端两套 lockfile、无 lint 无单测，且用户端生产构建会因为缺 `VITE_CALLER_TOKEN` 直接抛错。
本专题只做工程护栏，不动业务行为、不动模块边界。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 后端护栏 | 根 `pom.xml`：surefire 固定 `argLine=-Duser.home=${project.build.directory}/test-home`；enforcer 在 `validate` 阶段校验 Java 21 / Maven 3.9 / 重复依赖；`-Pstrict` 再加 `dependencyConvergence` |
| 依赖收敛 | BOM 钉 `protobuf-java 3.25.5`、`error_prone_annotations 2.38.0`、`httpclient 4.5.14`；`dependencyConvergence` 只排除 `com.squareup.okhttp3:*`、`com.squareup.okio:*`、`com.github.victools:jsonschema-generator`（该规则没有 `ignoredDependencies`，写错会直接报 `Cannot find 'ignoredDependencies'`） |
| 模块级禁令 | `agent-access` 禁 `io.agentscope:*` 与 `com.zhu:agent-cache-redis`；`agent-runtime`、`agent-product-companion` 禁 `com.zhu:agent-cache-redis`。`searchTransitive=false`，只查直接声明 |
| 未用依赖 | `agent-access` 的 `io.agentscope:agentscope-harness` 已删：全包 0 处 `io.agentscope` 引用 |
| wrapper | `mvnw` / `mvnw.cmd` / `.mvn/wrapper/maven-wrapper.properties`，Maven 3.9.13，`distributionType=only-script` |
| CI | `.github/workflows/ci.yml`：`boundary`（ArchUnit + Schema/Migration 契约）→ `verify`（全量）→ `frontend`（web / web-admin 各自 lint + 单测 + 构建） |
| 用户端令牌 | `web` 新增 `VITE_GATEWAY`：网关模式不设 `X-Caller-Token`，由 Nginx 代填；本地模式缺令牌直接在浏览器里给出可操作提示 |
| 部署链路 | `agent-deploy.ps1` 改用 pnpm，构建时设 `VITE_GATEWAY=true` 并在 finally 清掉；nginx 示例 upstream 由 8080 改为现网 8093 |
| 前端工程化 | `web` / `web-admin` 统一 pnpm（单一 `pnpm-lock.yaml`，删 `package-lock.json`）、ESLint flat config（`flat/essential`）、Vitest + jsdom |
| pnpm 设置 | 两端 `pnpm-workspace.yaml`：`nodeLinker: hoisted`（Windows 上 Node 跟随 junction 不稳定）、`overrides: estree-walker 2.0.2`（vitest 5 拉 ESM-only 3.x，与 vue-tsc 的 CJS 2.x 冲突） |

## 明确不做（本专题）

1. **X1** 不改业务代码行为、不改模块划分；`agent-readmodel` / `ProductModule` / `GateChain` 都在 P1–P3。
2. **X2** 不做全仓 reformat，不加 spotless/checkstyle。
3. **X3** 不引入 Gradle、不拆微服务、不换 DI。
4. **X4** 不在 CI 里连真实 MySQL / Redis / Milvus / Nacos；Testcontainers E2E 原计划排在 P3，因本机无 Docker 顺延到有 Docker 的环境再做（P3 文档记为 X1）。
5. **X5** 不把 `-Duser.home` 之类的本地绕法写进源码；本机沙箱限制只记在本文。

## 验收与实测

| 命令 | 结果 |
| --- | --- |
| `mvn -B -ntp -Pstrict validate` | BUILD SUCCESS |
| `mvn -B -ntp -Pstrict test` | **Tests run: 429, Failures: 0, Errors: 0** |
| `mvn -B -ntp -Pstrict verify` | 打包 + 测试通过（本机需排除 `OssObjectStoreTest`，原因见下） |
| `web`：`pnpm exec vue-tsc -b` / `pnpm lint` | exit 0 / exit 0 |
| `web-admin`：`pnpm exec vue-tsc -b` / `pnpm lint` | exit 0 / exit 0 |
| `web` / `web-admin`：`pnpm install --frozen-lockfile` | 均 up to date |

修掉的一处真实脆弱点：`PromptTemplateCheckTest.classpathPromptsPass` 原来用 `Path.of(root.toURI())`，
`mvn test` 时上游模块是 `target/classes`（`file:`）能过，`mvn verify` 时是已打好的 jar（`jar:`）会抛
`FileSystemNotFoundException`。现按协议分流，`jar:` 用 `FileSystems.newFileSystem` 读。

## 本机沙箱限制（不是仓库问题，CI 上无此限制）

1. **Mockito inline mock maker 需要 JVM 自 attach**，沙箱禁命名管道。本机绕法：往各模块
   `target/test-classes/mockito-extensions/org.mockito.plugins.MockMaker` 写 `mock-maker-subclass`；
   但这会让 `OssObjectStoreTest` 无法 mock final 类 `PutObjectResult`，所以本机跑全量时加
   `-Dtest=!OssObjectStoreTest`。CI 的 Linux runner 不受此限。
2. **Vite 8 / Vitest 5 在上游打包配置时会 `spawn` 子进程做 realpath**，沙箱返回 `EPERM`。
   因此 `pnpm test`、`pnpm build` 无法在本机跑通，只能由 CI 的 `frontend` job 给结论；
   `vue-tsc -b` 与 `eslint` 不经子进程，本机可跑。
3. npm 默认缓存目录（`%LOCALAPPDATA%\npm-cache`）与 `D:\newrepository` 在沙箱内只读，
   本机装依赖要 `--store-dir=.pnpm-store`，Maven 生成 wrapper 要 `-Dmaven.repo.local=...`。

## 落地顺序（后续块）

P1 `agent-readmodel` → P2 `ProductModule` 聚合 → P3 [`test-support` / `GateChain` / access 首批测试](./engineering-p3-testing-落地.md) →
P4 配置拆分校验与文档 status/链接检查。

P0 完成后各块单独成文，按顺序：

| 块 | 文档 | 状态 |
| --- | --- | --- |
| P0 | 本文 | 已完成 |
| P1 | [`docs/engineering-p1-readmodel-落地.md`](./engineering-p1-readmodel-落地.md) | 已完成 |
| P2 | [`docs/engineering-p2-product-module-落地.md`](./engineering-p2-product-module-落地.md) | 已完成 |
| P3 | [`docs/engineering-p3-testing-落地.md`](./engineering-p3-testing-落地.md) | 已完成 |
| P4 | [`docs/engineering-p4-config-docs-落地.md`](./engineering-p4-config-docs-落地.md) | 已完成 |

