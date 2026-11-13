# ① 仓库与双进程地图

## 学习目标

建立「两个进程、十来个模块、两个前端」的心智模型；知道边界从哪读、架构长文何时翻。

## 必读链接（有序）

1. [根 README — 项目是什么 / 不是什么](../../README.md#项目是什么--不是什么)  
2. [根 README — 架构鸟瞰](../../README.md#架构鸟瞰)  
3. [根 README — 目录与二次开发入口](../../README.md)（全文扫一遍导航即可）  
4. 模块 README 包表（先扫标题与「还没有」，不深挖实现）：  
   - [`agent-bootstrap`](../../agent-bootstrap/README.md)  
   - [`agent-access`](../../agent-access/README.md)  
   - [`agent-runtime`](../../agent-runtime/README.md)  
   - [`agent-semantic`](../../agent-semantic/README.md)  
   - [`agent-common`](../../agent-common/README.md)  
   - [`agent-state`](../../agent-state/README.md)  
   - [`agent-vector`](../../agent-vector/README.md)  
   - [`agent-audit`](../../agent-audit/README.md)  
   - [`agent-object-store`](../../agent-object-store/README.md)  
   - [`agent-admin`](../../agent-admin/README.md)  
5. 前端入口：[web/README](../../web/README.md)、[web-admin/README](../../web-admin/README.md)  
6. （可选加深）[`doc/AgentScope Java 2.0.4 产品级架构与工程落地指南 (2).md`](../../doc/AgentScope%20Java%202.0.4%20产品级架构与工程落地指南%20(2).md) — 按需翻，不作从 0 唯一入口  
7. （可选）[`docs/packaging-落地.md`](../packaging-落地.md) — 分包边界；正式问题放在 ④ 首节  

## 要搞清的问题

1. 用户进程与管理进程默认端口各是什么？管理进程为什么不跑对话？  
2. 身份在用户侧靠什么？管理侧靠什么？二者令牌能否混用？  
3. 「还没有」以哪里为准？根 README 与模块 README 冲突时听谁的？  
4. 架构指南与当前代码不一致时，以什么为准？  
5. `docs/tutorial` 和本 handbook 各服务什么读者？  

## 检查点

- 能用自己的话画出：浏览器 → 用户进程 / 管理进程 → Redis·MySQL·Milvus（及可选 Nacos）的关系。  
- 不要求本机已启动进程（启动在 ②③）。  
