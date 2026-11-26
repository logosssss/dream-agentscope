package com.zhu.access.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 接入层取这一次 HTTP 的来源地址。只在请求线程上调用，抄下来的字符串再交给运行记录。
 */
public final class ClientIp {

    /** Nginx 把连到自己的对端写成这个头。不读 X-Forwarded-For。 */
    static final String REAL_IP_HEADER = "X-Real-IP";

    private static final int LIMIT = 45;

    /** 只提供静态方法。地址规则集中在这里，避免两条对话入口各写一遍。 */
    private ClientIp() {
    }

    /**
     * 当前请求的来源地址。没有请求上下文时给空串，测试和调度都走这条。
     * 必须在过滤器清掉请求之前调用。流式离开请求线程之后不能再调。
     */
    public static String capture() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (!(attributes instanceof ServletRequestAttributes servlet)) {
            return "";
        }
        return from(servlet.getRequest());
    }

    /**
     * 头是单一地址就用它，否则用连接对端里的单一地址，再没有就空串。
     * 含逗号、空白或其它字符的头视为调用方伪造，不记。
     */
    static String from(HttpServletRequest request) {
        if (request == null) {
            return "";
        }
        String header = one(request.getHeader(REAL_IP_HEADER));
        if (header != null) {
            return header;
        }
        String remote = one(request.getRemoteAddr());
        return remote == null ? "" : remote;
    }

    /** 单一地址：长度 1 到 45，只含数字、十六进制、冒号和点，且至少有一位数字或字母。 */
    private static String one(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > LIMIT) {
            return null;
        }
        boolean hasLetterOrDigit = false;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean digit = c >= '0' && c <= '9';
            boolean hex = (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
            if (digit || hex) {
                hasLetterOrDigit = true;
                continue;
            }
            if (c != ':' && c != '.') {
                return null;
            }
        }
        return hasLetterOrDigit ? trimmed : null;
    }
}
