package com.zhu.access.infrastructure.security;

import com.zhu.common.security.WakeupTokens;
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
 * 接入层：只校验 {@code /api/internal/wakeups} 的 {@link WakeupTokens#HEADER}。
 * 其它路径直接放行（Caller 令牌仍由 {@link CallerTokenFilter} 负责）。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class WakeupTokenFilter extends OncePerRequestFilter {

    private final String expected;

    public WakeupTokenFilter(@Value("${agentscope.wakeup.token:}") String expected) {
        this.expected = expected;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String path = path(request);
        if (!WakeupTokens.isInternalWakeup(path)) {
            chain.doFilter(request, response);
            return;
        }
        if (!WakeupTokens.matches(expected, request.getHeader(WakeupTokens.HEADER))) {
            reject(response);
            return;
        }
        chain.doFilter(request, response);
    }

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

    private static void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"ok\":false,\"code\":\"UNAUTHORIZED\",\"message\":\"wakeup token is invalid\",\"data\":null}");
    }
}
