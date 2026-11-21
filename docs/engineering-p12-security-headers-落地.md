# 安全响应头与页面 CSP（P12）

状态：**已完成**（2026-12-01）

## 为什么有这一块

上线门禁的「敏感面」清单里一直缺一条：**响应头**。查了一遍代码：

- `deploy/nginx/dream-saas.com.conf.example` 里 **0 处 `add_header`**；
- 后端（access / admin）**0 处** `X-Content-Type-Options` / `X-Frame-Options` / `Referrer-Policy` / `Content-Security-Policy`；
- `web/src/shared/auth/session.ts` 把 accessToken 存在 `localStorage`（第 20–22、36–38 行）。

也就是说：令牌在 localStorage，而页面没有任何 CSP 兜底。真出现一处 XSS（比如某天有人给 Markdown 渲染开了
`html: true` 却没配白名单），令牌就能被整段读走。改这一块的成本很低，缺它的理由不成立。

## 已锁定

| 决定 | 内容 | 理由 |
| --- | --- | --- |
| 分两层 | 接口侧由后端过滤器兜底；页面侧由 nginx 的 `add_header` 给 | 接口只回 JSON、页面回 HTML，两者能用不同强度的策略 |
| 接口 CSP | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'` | 接口不需要任何子资源；浏览器直接打开接口地址时也不至于被嵌 |
| 页面 CSP | `default-src 'self'` + 字体白名单 + `style-src 'unsafe-inline'` | Tailwind 与 Vue 的 `:style` 绑定都写行内样式；`index.html` 引了 Google Fonts |
| HSTS 只放 nginx | 后端不加 | 后端在 nginx 后面看到的是 http，`request.isSecure()` 永远 false，加在应用层只会误导 |
| 接口加 `Cache-Control: no-store` | 只对 `/api` 前缀 | 接口里有令牌回执与个人数据；探活结果不需要，避免影响探针 |
| 顺序在令牌校验之前 | `HttpSecurityHeadersFilter` 用 `HIGHEST_PRECEDENCE`，`CallerTokenFilter` 让到 `+1`、`WakeupTokenFilter` 到 `+2` | 401 的响应也要带这些头，否则错误页可能被代理缓存或被别的站嵌入 |
| 不合并两个进程的过滤器 | 管理进程不加 | 管理端不挂公网（`deploy/nginx/README.md`「管理端不要挂到同一公开 CTA」），先不为它维护第二份 |

## 明确不做

- **X1 不做 nonce-based CSP**。要按请求生成 nonce 并透传到模板，而这是纯静态 SPA（`index.html` 由 nginx 直接发），
  需要改成模板渲染或 Edge 注入；收益是能去掉 `style-src 'unsafe-inline'`，但 Vue 的 `:style` 绑定依然会被拦。
- **X2 不做 COOP/COEP/CORP**。现在没有需要跨源隔离的能力（SharedArrayBuffer、wasm 线程），开了反而更容易把
  第三方字体或图片拦掉。
- **X3 不接管管理进程**。管理端不在公网入口，加第二份过滤器只会引入漂移；真要挂公网时照这份文档抄一遍即可。
- **X4 不在应用层加 HSTS**。理由见上表。
- **X5 不做自动化的「浏览器里真的不白屏」验证**。本机 `vite build` 与 vitest 都被沙箱的 `spawn EPERM` 拦住
  （Vite 8 rolldown 用 `node:child_process` 做 realpath），页面级验证只能人工开一次浏览器。

## 改动

1. 新增 `agent-access/src/main/java/com/zhu/access/infrastructure/security/HttpSecurityHeadersFilter.java`：
   `@Component @Order(Ordered.HIGHEST_PRECEDENCE)`，`OncePerRequestFilter`，设置
   `X-Content-Type-Options: nosniff`、`X-Frame-Options: DENY`、`Referrer-Policy: strict-origin-when-cross-origin`、
   `Content-Security-Policy`（`API_CSP`），`/api` 前缀再加 `Cache-Control: no-store`。
2. 新增 `agent-access/src/main/java/com/zhu/access/infrastructure/security/RequestPaths.java`：
   `of(request)`（去 context path 的路径）与 `isApi(path)`；`CallerTokenFilter` 改为复用，避免两处各写一份路径判断。
3. `CallerTokenFilter` 的 `@Order` 改 `HIGHEST_PRECEDENCE + 1`、`WakeupTokenFilter` 改 `+ 2`（顺序理由写在过滤器 javadoc 里）。
4. 新增 `agent-access/src/test/java/com/zhu/access/infrastructure/security/HttpSecurityHeadersFilterTest.java`：4 例
   （接口路径五个头 + `no-store`；探活四个头但无 `no-store`；链路继续往下走；三个过滤器的顺序关系）。
5. `deploy/nginx/dream-saas.com.conf.example`：server 级只放 HSTS；`location /` 里重复 HSTS 并补齐
   `nosniff` / `X-Frame-Options` / `Referrer-Policy` / 页面 CSP；注释写明「location 里一旦有 `add_header` 就不再继承 server 级」，
   以及 `style-src 'unsafe-inline'`、字体白名单、`img-src data: blob:` 各自为什么需要。
6. 文档：`README.md` 的「配置优先级与令牌安全」加「响应头」表；`docs/release-gate.md` 敏感面加一条；
   `docs/INDEX.md` 改 P0–P12 并加行。

## 验收实测

| 项 | 命令 | 结果 |
| --- | --- | --- |
| 编译 | `mvn -B -ntp -Pstrict -pl agent-access -am -DskipTests compile` | BUILD SUCCESS |
| 新增单测 | `mvn -B -ntp -Pstrict -pl agent-access -am -Dtest=HttpSecurityHeadersFilterTest …` | Tests run: 4, 0 失败 |
| 全量 | `mvn -B -ntp -Pstrict verify "-Dtest=!OssObjectStoreTest" "-Dsurefire.failIfNoSpecifiedTests=false"` | 见提交说明 |
| 页面 CSP | 人工打开线上页面看 console | **未在本机做**（沙箱构建被拦），靠 CI 构建 + 线上首访确认 |

## 残留

1. **页面 CSP 只是配置示例，未在真实浏览器里贴过一遍**。上线后第一次访问要看 console 有没有
   `Refused to ...` ；如果白屏，先把 `Content-Security-Policy` 那行注释掉再逐条加回。
2. **管理进程没有响应头**（见 X3）。
3. **没有 CSP 违规上报**（`report-uri` / `report-to`）。真要收集得先有端点，属于可观测性那一块。
4. **`localStorage` 存令牌这件事没动**。更稳的是 HttpOnly Cookie + CSRF 令牌，但要改登录态、CORS 与所有前端调用点，
   不是一次能顺手做完的改动。

## 下一步

- 可观测性：`request_id` 串起 `agent_run` 与 `agent_event_audit`，让「刚才那轮出错」可查（`agent_run` 侧已由 [P13](./engineering-p13-request-id-落地.md) 落地）。
- 上线时把 `docs/release-gate.md` 的 R2 签字补完（冒烟仍待跑）。
