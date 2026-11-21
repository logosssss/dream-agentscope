package com.zhu.runtime.task.handle;

import java.util.Map;

/**
 * 任务 payload 的取值口径。payload 是外部传进来的 {@code Map<String, Object>}，
 * 两种类型最常见（字符串与数字），各执行器都用同一套转换，避免一处 trim 一处不 trim。
 */
public final class TaskPayload {

    private TaskPayload() {
    }

    /** 取字符串，去空白；缺失或空返回空串。 */
    public static String text(Map<String, Object> payload, String key) {
        if (payload == null || key == null) {
            return "";
        }
        Object value = payload.get(key);
        if (value == null) {
            return "";
        }
        String text = value.toString().trim();
        return text;
    }

    /**
     * 取整数；缺失、非数字或超界时用默认值。
     *
     * <p>不抛异常：payload 是数据不是代码，写错一个数不该让整条任务死在解析上。
     */
    public static int number(Map<String, Object> payload, String key, int fallback) {
        if (payload == null || key == null) {
            return fallback;
        }
        Object value = payload.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.toString().trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    /** 取整数并夹到 {@code [min, max]}，用于批量上限这类必须有界的参数。 */
    public static int bounded(Map<String, Object> payload, String key, int fallback, int min, int max) {
        int value = number(payload, key, fallback);
        if (value < min) {
            return min;
        }
        return Math.min(value, max);
    }
}
