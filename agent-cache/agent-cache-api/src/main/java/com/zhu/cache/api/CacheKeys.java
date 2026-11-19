package com.zhu.cache.api;

/**
 * 缓存键拼接（K4）。统一 {@code agentscope:} 前缀，可选租户段，避免模块间裸键碰撞。
 */
public final class CacheKeys {

    /** 全局前缀。所有业务键必须经本类拼出或显式以该前缀开头。 */
    public static final String PREFIX = "agentscope:";

    private CacheKeys() {
    }

    /**
     * 拼键：{@code agentscope:{capability}:[{tenant}:]{parts...}}。
     * tenant 空白则省略租户段。capability / parts 空白会跳过。
     *
     * @param capability 能力段，如 {@code quota}、{@code lock}、{@code kv}
     * @param tenantId   租户；可空
     * @param parts      业务后缀，按顺序追加
     */
    public static String of(String capability, String tenantId, String... parts) {
        StringBuilder sb = new StringBuilder(PREFIX);
        appendSegment(sb, capability);
        if (tenantId != null && !tenantId.isBlank()) {
            appendSegment(sb, tenantId.trim());
        }
        if (parts != null) {
            for (String part : parts) {
                appendSegment(sb, part);
            }
        }
        // 去掉末尾多余冒号：PREFIX 已以冒号结尾，若无段则保留 agentscope:
        if (sb.length() > PREFIX.length() && sb.charAt(sb.length() - 1) == ':') {
            sb.setLength(sb.length() - 1);
        }
        return sb.toString();
    }

    /**
     * 无租户的快捷拼法。
     */
    public static String ofCapability(String capability, String... parts) {
        return of(capability, null, parts);
    }

    private static void appendSegment(StringBuilder sb, String segment) {
        if (segment == null || segment.isBlank()) {
            return;
        }
        sb.append(segment.trim()).append(':');
    }
}
