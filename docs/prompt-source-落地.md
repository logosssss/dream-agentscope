# 产品系统提示词的外部化

状态：**已完成**（2026-11-26）。范围：搭子（`companion-v1`）长驻 Agent 的系统提示词来源。管理进程不改。

## 为什么有这一块

平台早就有一条提示词通道：`PromptService.get(key, variables, fallback)` → Nacos 模板（`agent-semantic`），
接入层也一直在按 `prompt.companion.system` 取值，填进 `AgentRequest.systemPrompt`。
但运行层只把这个字段用于**校验**（`ModelTextGuard.beforeCall`），长驻 Agent 的 `sysPrompt` 在装配时
直接取 `promptFallback(null)`——也就是硬编码的 `PlatformPrompt.SYSTEM_FALLBACK`。

结果：Nacos 提示词热更、`PromptTemplateCheck`、`PromptRelease` 全部对线上人格零效果，改的是接入层那份
只用来校验的字符串。

## 已锁定

| 项 | 定法 |
|----|------|
| 取值时机 | Agent 装配时读一次（`CompanionResidentAgentBinding.systemPrompt()`）：外部模板优先，取不到用产品兜底 |
| 键 | 平台约定 `PlatformPrompt.SYSTEM_KEY`（`prompt.companion.system`），不传变量 |
| 兜底 | `ProductSemanticProvider.promptFallback(key)`，搭子返回 `PlatformPrompt.SYSTEM_FALLBACK` |
| 外部来源失败 | `PromptAppService` 自己退回兜底并记一行日志；不把空串或未替换的占位符交给模型 |
| 生效时机 | 读一次就在 Agent 上；改完要 `rebuildAll()` 或重启进程。不做按请求切换 |
| 接入层那份 | `AgentRequest.systemPrompt` 仍只用于「未替换占位符」校验，不再承担装配职责 |

## 明确不做

- **X1** 不做按请求/按租户换系统提示词：长驻 Agent 一经装配就固定，按请求换要让每轮重建，代价与收益都不划算。
- **X2** 不做提示词灰度（百分比分流）：先只有一条键，灰度要等有第二个候选再说。
- **X3** 不改危机话术的来源：危机稿仍在 classpath，`PlatformPrompt.publishable` 明确不许推到 Nacos。
- **X4** 不在装配时校验模板变量：`PromptTemplateCheck` 已在发布侧拦，这里只认「取到就用、取不到用兜底」。

## 改动

1. `agent-product-companion/.../binding/CompanionResidentAgentBinding.java`：注入 `PromptService`；`customize` 改用 `systemPrompt()`；新增 `systemPrompt()` 并写清「装配时读一次」。
2. `agent-product-companion/pom.xml`：加 `spring-boot-starter-test`（test scope），让产品能自带用例，不必再借 bootstrap。
3. `agent-product-companion/src/test/.../binding/CompanionSystemPromptTest.java`：外部模板优先、缺失退兜底、读取口缺席仍有兜底、问的是平台键且不带变量。

## 验收（2026-11-26 本机 JDK 21）

`CompanionSystemPromptTest` 4 例全绿；全仓 `mvn test` 716 例、7 跳过（Testcontainers，需 Docker）、0 失败。

## 残留

1. **生效要重启或重建**：改 Nacos 模板后不会自动重载到已装配的 Agent；`ResidentAgentReloader` 已在，但没有配置变更监听去调它。
2. **仍有一条硬编码**：`CompanionSemantics.promptFallback` 忽略 `key` 一律返回 `SYSTEM_FALLBACK`；有了第二个产品键时要改成按 key 分支。
