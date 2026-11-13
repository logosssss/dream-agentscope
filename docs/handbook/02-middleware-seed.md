# ② 本地中间件与种子数据

## 学习目标

弄清启动前依赖什么、配置优先级、演示租户从哪来；避免后面联调反复卡在环境。

## 必读链接（有序）

1. [根 README — 环境与依赖](../../README.md#环境与依赖)  
2. [根 README — 本地启动（推荐：Nacos）](../../README.md#本地启动推荐nacos)  
3. [根 README — 配置优先级与令牌安全](../../README.md#配置优先级与令牌安全)  
4. [根 README — 演示种子、额度与公开演示](../../README.md#演示种子额度与公开演示)  
5. 种子脚本：[`deploy/mysql/seed-demo-tenant-user.sql`](../../deploy/mysql/seed-demo-tenant-user.sql)  
6. （对照）[`docs/release-gate.md`](../release-gate.md) 第 0 节环境前提 — 只对照清单，不在本段做完整门禁  

## 要搞清的问题

1. 用户/管理进程启动时，Redis、MySQL、Milvus 连不上会怎样？会不会回退进程内存储？  
2. `NACOS_ADDR` 为空与已配置时，行为差在哪里？infra yaml 与业务 yaml 谁可热更？  
3. 哪些密钥必须留在环境变量、不能进 Nacos 明文？  
4. 演示种子解决什么问题？没有种子时注册/登录常见失败是什么？  
5. `AGENTSCOPE_CALLER_TOKEN` 与 `AGENTSCOPE_ADMIN_TOKEN` 为什么必须不同？  

## 检查点

- 本机 Redis / MySQL / Milvus 可连（或明确记下当前阻塞项）。  
- 已按根 README 执行（或确认已有）演示种子。  
- 配置好 Caller / Admin 令牌与（若用）Nacos 四件套；**不要把真实密钥写进 handbook 或提交进 git**。  
- 仓库根目录 `mvn test` 全绿（可与③合并验收，但②结束前至少跑过一次更稳）。  
