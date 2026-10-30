package com.zhu.common.health;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 就绪检查结果。给存活检查和就绪检查共用同一套状态词，避免各进程各写一套。
 *
 * @param status 总状态。任一依赖为 DOWN 时是 DOWN，否则是 UP
 * @param checks 各项依赖的状态。未启用的依赖是 SKIPPED
 */
public record ReadinessSnapshot(String status, Map<String, String> checks) {

    /** 依赖可用。 */
    public static final String UP = "UP";

    /** 依赖已启用但当前不可用。 */
    public static final String DOWN = "DOWN";

    /** 依赖没启用，这次不探测。 */
    public static final String SKIPPED = "SKIPPED";

    /**
     * 由各项结果汇总。空表算就绪，因为没有必须探测的依赖。
     * 传入的 map 会被复制，调用方之后再改不影响结果。
     */
    public static ReadinessSnapshot of(Map<String, String> checks) {
        Map<String, String> copy = new LinkedHashMap<>();
        if (checks != null) {
            copy.putAll(checks);
        }
        boolean down = copy.values().stream().anyMatch(DOWN::equals);
        return new ReadinessSnapshot(down ? DOWN : UP, Map.copyOf(copy));
    }

    /** 总状态为 UP 时可以接流量。 */
    public boolean ready() {
        return UP.equals(status);
    }
}
