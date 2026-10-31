package com.zhu.access.infrastructure.security;

import com.zhu.common.security.CallerTokens;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 用户进程的调用方令牌。只拦业务路径，不校验租户和用户是谁。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CallerTokenFilter extends OncePerRequestFilter {

    private final String expected;

    /**
     * 令牌来自配置。正式环境为空时，除存活和就绪外全部拒绝。
     */
    public CallerTokenFilter(@Value("${agentscope.caller.token:}") String expected) {
        this.expected = expected;
    }

    /**
     * 存活和就绪不带令牌也能过，方便探活。其余路径对不上就返回 401，不进入控制器。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        if (CallerTokens.isHealth(path(request))) {
            chain.doFilter(request, response);
            return;
        }
        if (!CallerTokens.matches(expected, request.getHeader(CallerTokens.HEADER))) {
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * MockMvc 和正式容器的 servletPath 不一致，用请求 URI 去掉 context path。
     */
    private static String path(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null) {
            return "";
        }
        String context = request.getContextPath();
        if (context != null && !context.isEmpty() && uri.startsWith(context)) {
            return uri.substring(context.length());
        }
        return uri;
    }

    /** 响应体和业务失败同一结构。不把提交的令牌写进日志或正文。 */
    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"ok\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"caller token is invalid\",\"data\":null}");
    }
}
