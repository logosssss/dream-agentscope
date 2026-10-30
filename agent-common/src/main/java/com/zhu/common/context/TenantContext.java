package com.zhu.common.context;

/**
 * 当前请求的租户。只给接入层在请求进来时写入、结束时清理，业务身份仍以显式参数为准。
 */
public final class TenantContext {

    private static final ThreadLocal<String> CURRENT = new ThreadLocal<>();

    private TenantContext() {
    }

    /** 绑定当前线程的租户。 */
    public static void set(String tenantId) {
        CURRENT.set(tenantId);
    }

    /** 当前线程的租户，未设置时为 null。 */
    public static String get() {
        return CURRENT.get();
    }

    /** 请求结束时必须调用，避免线程池复用串租户。 */
    public static void clear() {
        CURRENT.remove();
    }
}
