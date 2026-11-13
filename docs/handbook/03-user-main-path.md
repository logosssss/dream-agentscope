# ③ 用户主链路（登录 → 对话 → 落库）

## 学习目标

跟完「Caller + Bearer → 额度/闸 → Channel → Agent 执行 → 会话/消息/运行落库」的主路径；能区分 WEB 与 API 入口。

## 必读链接（有序）

1. [根 README — 本地启动与健康检查](../../README.md#本地启动推荐nacos)（live / ready）  
2. [`agent-access/README.md`](../../agent-access/README.md) — 包表中的 `ChatController`、登录、会话、额度；以及 `/api/chat*` 与 `/api/agent/chat*` 说明  
3. [`agent-bootstrap/README.md`](../../agent-bootstrap/README.md) — 用户进程装配入口  
4. [`agent-runtime/README.md`](../../agent-runtime/README.md) — 执行与 Harness 边界（先扫，专题细节进④）  
5. [`web/README.md`](../../web/README.md) — 搭子页与 `/api-console`  
6. （预告，细读在④）[`docs/channel-gateway-落地.md`](../channel-gateway-落地.md)、[`docs/api-channel-落地.md`](../api-channel-落地.md)  

## 要搞清的问题

1. 对话身份为什么不能信请求体 / 请求头里的租户用户？  
2. Redis 在额度或占锁失败时返回什么？会不会改走内存或 MySQL？  
3. `/api/chat*` 与 `/api/agent/chat*` 分别对应哪条 Channel？旧路径显式 `channel=API` 会怎样？  
4. 成功对话会写哪些表/缓存线索（会话元数据、气泡、运行）？写失败是否改变已生成回复？  
5. 流式 SSE 事件里，接入层是否自己 `streamEvents`？危机档或人设检查时增量可能有什么现象？  

## 检查点

- 用户进程 `8080`：`GET /health/live`、`GET /health/ready` 成功。  
- 完成注册或登录，拿到 Bearer。  
- 打通一轮 **WEB** 流式：搭子页发送，或等价调用 `/api/chat/stream`。  
- （可选）打开 `/api-console` 走 `/api/agent/chat/stream`，侧栏只见 `channel=API` 会话。  
- 用会话列表 / 消息接口或页面确认历史可回看。  
