package com.zhu.common.context;

/**
 * 当前请求的幂等标识。只给接入层在请求进来时写入、结束时清理。
 * 没有这个头就不做重复提交判断。
 */
public final class RequestIdContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private RequestIdContext() {
    }

    /** 绑定当前线程的请求标识。 */
    public static void set(String requestId) {
        CURRENT.set(requestId);
    }

    /** 当前线程的请求标识，未设置时为 null。 */
    public static String get() {
        return CURRENT.get();
    }

    /** 请求结束时必须调用，避免线程池复用串请求。 */
    public static void clear() {
        CURRENT.remove();
    }
}
