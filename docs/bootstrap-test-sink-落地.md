# bootstrap 测下沉试点落地（已锁定）

日期：2026-11-16。  
本文只记 grill 收口结论与落地顺序。不改架构指南原文。  
背景：单测几乎全在 `agent-bootstrap`；`agent-runtime` / `agent-access` 无 `src/test`（见 `docs/engineering-module-review.md` 故事 3）。  
本专题做**工程小改试点**：打通 runtime 模块测，不强制大搬家，不升产品能力。

## 已锁定

| 项 | 约定 |
| --- | --- |
| 主轴 | **H**：债 / 工程小改 |
| 范围 | **S**：bootstrap **测下沉试点**（非旧测大搬家） |
| 深度 | **D1**：只开 `agent-runtime` 的 `src/test`；搬一小撮纯 JUnit |
| 用例集 | **T1**：`CrisisReplyGuardTest`、`PersonaConsistencyGuardTest`、`HybridChunkMergerTest`、`TextChunkerTest` |
| 包名 | **P1**：跟被测类同层（`com.zhu.runtime.domain` / `...domain.knowledge`）；bootstrap **删原件** |
| 依赖 | **F1**：`agent-runtime` 加 `spring-boot-starter-test`（test scope）；ArchUnit 仍留 bootstrap |
| 文档/切片 | **C1**：另开本文；评估文互指；① 锁文 → ② 搬测 → ③ README/评估文 |

## 明确不做（本专题 · X1–X5）

1. **X1** 不强制大搬家：除 T1 四测外，bootstrap 其余测不动；不宣称「测试债已清完」  
2. **X2** 不搬 `ArchitectureTest` / 任何需全仓 classpath 的 ArchUnit；不搬 `@SpringBootTest` / 接线 / spike 测  
3. **X3** 不改生产代码行为（工厂、中间件、人设、切块算法）；只搬测 + 加 runtime test 依赖 + 文档  
4. **X4** 不借本片开产品 Remote / MCP·Sandbox 真开 / `mode=mq` / Curator  
5. **X5** 不建 access/state 等其它模块的 `src/test`；不在父 POM 做统一 test 依赖治理  

## 落地顺序（一次一块）

再说「继续 / 落地」时按序做：

1. **锁定文** — 已完成  
   本文；`engineering-module-review.md` 互指。不改代码、不搬测。  
2. **runtime 试点** — 已完成  
   `agent-runtime` 加 `spring-boot-starter-test`；按 P1 迁入 T1 四测并删 bootstrap 原件；根目录 `mvn test` 绿。  
3. **README / 评估文收口** — 已完成  
   runtime / bootstrap README 与评估文：试点已做、旧测仍观察、不宣称清完。

## 验收要点

- ①：锁文与 grill 一致；评估文互指；无代码变更  
- ②：四测只在 runtime 跑；bootstrap 无同名原件；全仓 `mvn test` 绿；无生产行为变更  
- ③：文档不宣称测试债已清完 / 已大搬家  

## 状态

grill 已结束；落地 **①～③ 已完成**。本专题收口。若再改规范，先改本文再动代码。
