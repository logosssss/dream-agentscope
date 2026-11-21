package com.zhu.access.infrastructure.security;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 取请求路径的小工具。
 *
 * <p>MockMvc 和正式容器的 servletPath 不一致，统一用请求 URI 去掉 context path；
 * 几个过滤器都要判断「是不是接口路径」，放在一处避免各写一份判断出现分歧。
 */
final class RequestPaths {

    private RequestPaths() {
    }

    /** 去掉 context path 的请求路径；拿不到时返回空串，调用方按「非接口路径」处理。 */
    static String of(HttpServletRequest request) {
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

    /** 是否接口路径。只用前缀判断，避免每加一个 controller 都要来改这里。 */
    static boolean isApi(String path) {
        return path != null && (path.equals("/api") || path.startsWith("/api/"));
    }
}
