package com.zhu.access.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 响应头兜底：网关漏配时接口也不至于裸奔。
 *
 * <p>顺序排在令牌校验之前（见 {@link CallerTokenFilter} 的 +1、{@link WakeupTokenFilter} 的 +2）：
 * 401 的响应同样要带这些头，否则错误页可能被代理缓存、被别的站嵌进 iframe。
 *
 * <p>接口只回 JSON，所以 CSP 用「默认什么都不许」最省事：即使某天有人在 JSON 里回显了
 * 用户文本，也不会被当成子资源加载。页面自己的 CSP 在网关那一层（静态文件），
 * 两条策略互不干扰：浏览器按各自文档执行，接口文档上这条更严。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpSecurityHeadersFilter extends OncePerRequestFilter {

    /**
     * 不放行任何子资源：没有 script/style/img/font，也就没有可借道的地方。
     * 保留 frame-ancestors 与 base-uri 是给「用户直接在浏览器里打开接口地址」这种边缘情况兜底。
     */
    static final String API_CSP = "default-src 'none'; frame-ancestors 'none'; base-uri 'none'";

    /**
     * 加完头再往下走。用 setHeader 而不是 addHeader，避免同一响应上出现两行取值不同的同名头。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Content-Security-Policy", API_CSP);
        // 接口里会出现令牌相关的回执与个人数据，不允许任何一层缓存留存。
        if (RequestPaths.isApi(RequestPaths.of(request))) {
            response.setHeader("Cache-Control", "no-store");
        }
        chain.doFilter(request, response);
    }
}
