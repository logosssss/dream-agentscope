package com.zhu.access.infrastructure.security;

import com.zhu.common.context.RequestIdContext;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 接入层身份过滤器。请求头只补线程上的租户、用户和请求标识，不拒绝缺头的请求。
 * 存活检查不需要这些头。请求体里的身份由 ChatAppService 优先使用。
 */
@Component
public class AccessIdentityFilter extends OncePerRequestFilter {

    /** 租户请求头。空值不当成已登录。 */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /** 用户请求头。空值不当成已登录。 */
    public static final String USER_HEADER = "X-User-Id";

    /** 幂等请求头。空值表示这次不防重复提交。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /**
     * 头有非空值才写入上下文。请求结束必须清掉，避免线程池串话。
     * 不按路径拒绝，所以 /health/live 和 /health/ready 没有头也能过。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String tenantId = text(request.getHeader(TENANT_HEADER));
        String userId = text(request.getHeader(USER_HEADER));
        String requestId = text(request.getHeader(REQUEST_ID_HEADER));
        if (tenantId != null) {
            TenantContext.set(tenantId);
        }
        if (userId != null) {
            UserContext.set(userId);
        }
        if (requestId != null) {
            RequestIdContext.set(requestId);
        }
        try {
            chain.doFilter(request, response);
        } finally {
            TenantContext.clear();
            UserContext.clear();
            RequestIdContext.clear();
        }
    }

    /** 空白头视为没传。 */
    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }
}
