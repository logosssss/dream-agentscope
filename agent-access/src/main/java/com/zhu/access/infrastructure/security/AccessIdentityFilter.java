package com.zhu.access.infrastructure.security;

import com.zhu.access.application.auth.LoginSessionCodec;
import com.zhu.access.infrastructure.cache.AccessCache;
import com.zhu.common.context.RequestIdContext;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 接入层身份过滤器。只认 Bearer 登录会话。
 * 请求头里的租户和用户不再写入上下文。请求结束必须清掉。
 */
@Component
public class AccessIdentityFilter extends OncePerRequestFilter {

    /** 租户请求头。空值不当成已登录。 */
    public static final String TENANT_HEADER = "X-Tenant-Id";

    /** 用户请求头。空值不当成已登录。 */
    public static final String USER_HEADER = "X-User-Id";

    /** 幂等请求头。空值表示这次不防重复提交。 */
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 登录会话请求头。值形如 Bearer 加空格再加令牌。 */
    public static final String AUTHORIZATION_HEADER = "Authorization";

    private static final String BEARER_PREFIX = "Bearer ";

    private final AccessCache cache;

    /**
     * 没有缓存时，带了 Bearer 按不可用处理。正式进程和测试都会提供缓存。
     */
    public AccessIdentityFilter(ObjectProvider<AccessCache> caches) {
        this.cache = caches == null ? null : caches.getIfAvailable();
    }

    /**
     * 带了 Bearer 就只信登录会话：无效 401，Redis 失败 503。
     * 没带 Bearer 时不从请求头补用户。请求号仍然读取。请求结束必须清掉。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        String bearer = bearerToken(request.getHeader(AUTHORIZATION_HEADER));
        String tenantId = null;
        String userId = null;
        if (bearer != null) {
            LoginSessionCodec.Identity identity = resolveBearer(bearer, response);
            if (identity == null) {
                return;
            }
            tenantId = identity.tenantId();
            userId = identity.userId();
        }
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

    /**
     * 从 Authorization 取出 Bearer 令牌。格式不对返回 null。
     */
    public static String bearerToken(String authorization) {
        if (authorization == null || authorization.isBlank()) {
            return null;
        }
        String raw = authorization.trim();
        if (raw.length() <= BEARER_PREFIX.length() || !raw.regionMatches(true, 0, BEARER_PREFIX, 0, BEARER_PREFIX.length())) {
            return null;
        }
        String token = raw.substring(BEARER_PREFIX.length()).trim();
        return token.isEmpty() ? null : token;
    }

    /**
     * 读会话。缓存缺失、解不开或令牌无效时写 401。
     * Redis 抛不可用时写 503。成功返回身份，失败已写响应时返回 null。
     */
    private LoginSessionCodec.Identity resolveBearer(String token, HttpServletResponse response) throws IOException {
        if (cache == null) {
            reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "UNAVAILABLE", "redis is unavailable");
            return null;
        }
        String raw;
        try {
            raw = cache.get(LoginSessionCodec.key(token));
        } catch (BusinessException ex) {
            if (ex.errorCode() == ErrorCode.UNAVAILABLE) {
                reject(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "UNAVAILABLE", ex.getMessage());
                return null;
            }
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "login session is invalid");
            return null;
        }
        LoginSessionCodec.Identity identity = LoginSessionCodec.decode(raw);
        if (identity == null) {
            reject(response, HttpServletResponse.SC_UNAUTHORIZED, "UNAUTHORIZED", "login session is invalid");
            return null;
        }
        return identity;
    }

    /** 空白头视为没传。 */
    private static String text(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value;
    }

    /** 响应体和业务失败同一结构。不把令牌写进日志或正文。 */
    private static void reject(HttpServletResponse response, int status, String code, String message) throws IOException {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json");
        response.getWriter().write(
                "{\"ok\":false,\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"data\":null}");
    }
}
