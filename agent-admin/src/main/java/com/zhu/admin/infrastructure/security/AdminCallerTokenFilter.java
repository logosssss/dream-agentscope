package com.zhu.admin.infrastructure.security;

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
 * 管理进程的调用方令牌。和用户进程分开配置，不执行工具，也不认用户登录。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AdminCallerTokenFilter extends OncePerRequestFilter {

    private final String expected;

    /**
     * 令牌来自配置。正式环境为空时，除存活和就绪外全部拒绝。
     */
    public AdminCallerTokenFilter(@Value("${agentscope.caller.token:}") String expected) {
        this.expected = expected;
    }

    /**
     * 存活和就绪不带令牌也能过。其余路径对不上就返回 401。
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
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setCharacterEncoding("UTF-8");
            response.setContentType("application/json");
            response.getWriter().write(
                    "{\"ok\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"caller token is invalid\",\"data\":null}");
            return;
        }
        chain.doFilter(request, response);
    }

    /**
     * 用请求 URI 去掉 context path。servletPath 在测试容器里可能是空的。
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
}
