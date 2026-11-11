---
no: 0
slug: your-slug
title: 公众号标题（现象 + 数字，如"6 个后端默认假设的失效"）
csdn_title: CSDN 标题（关键词前置 + 序号，如"Java 转 AI Agent（一）：…"）
series: 第一季 · 破冰
tag: lesson-00
version: v0.1.0
date: 2026-11-05
keywords: AgentScope, Java, AI Agent, Spring Boot, RAG
summary: 一句话摘要，说明本文覆盖的现象与结论，用于 CSDN 摘要栏和公众号分享描述。
cover: assets/cover-900x383.png
next: 下一篇标题
---

> **《AgentScope 落地实战》第 N 篇。** 代码基线 `lesson-00`，仓库：https://github.com/logosssss/dream-agentscope
>
> 上一篇：xxx ｜ 下一篇：xxx

## 现象

（3–5 行。可复现的故障：触发条件、观测到的结果、影响范围。不写"我曾经遇到"，直接陈述事实。）

## 一、失效的默认假设

（说明该故障与哪条常见假设冲突。给出按该假设写出的代码，标注其问题。）

```java
// 按默认假设写出的实现
```

## 二、机制

（为什么失效：框架行为、调用顺序、状态生命周期。这一段是文章主体，必须能独立解释故障。）

## 三、改法

（可运行的最小实现，≤20 行。标注关键约束。）

```java
// 实际实现
```

> 完整实现见 [XXX.java](https://github.com/.../blob/lesson-00/.../XXX.java#L12)

## 四、连带约束

（改法引入的新约束，逐条列：配置项、超时、幂等、资源释放、上限。）

| 约束 | 原因 | 取值 |
|---|---|---|
|  |  |  |

## 五、适用边界

（什么场景不适用；当前未实现的部分。）

## 小结

（3 条以内，每条为可执行的规则，不加形容词。）

---

**下一篇**：xxx。

> 系列目录：[`docs/tutorial/series-plan.md`](https://github.com/.../blob/main/docs/tutorial/series-plan.md)

---

<!-- 写作约束（发布前删除本段）：
1. 不用第一人称与第二人称，不出现"我/我们/你/大家"。
2. 不写口述语："聊聊""踩坑""说实话""不得不说""其实"。
3. 不写主观判断："非常好用""设计很优雅""显然"。
4. 数字优先：耗时、条数、上限、默认值，全部写具体值。
5. 每段不超过 4 行，代码块单行不超过 60 字符。
-->
