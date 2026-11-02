package com.zhu.semantic.domain;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;

/**
 * 权限规则上的条件。只认 UTC 时分窗，避免把还不能解释的 JSON 当成无条件命中。
 */
public final class PermissionConditions {

    /** 只接受两位小时和两位分钟，避免 ofPattern("HH:mm") 在 STRICT 下解不出 LocalTime。 */
    private static final DateTimeFormatter HOUR_MINUTE = new DateTimeFormatterBuilder()
            .parseStrict()
            .appendValue(ChronoField.HOUR_OF_DAY, 2, 2, SignStyle.NOT_NEGATIVE)
            .appendLiteral(':')
            .appendValue(ChronoField.MINUTE_OF_HOUR, 2, 2, SignStyle.NOT_NEGATIVE)
            .toFormatter(Locale.ROOT)
            .withResolverStyle(ResolverStyle.STRICT);

    /** 工具类，不实例化。 */
    private PermissionConditions() {
    }

    /**
     * 空白或空对象无条件命中。只接受 after / before。
     * 不能解释时返回 {@link Outcome#UNKNOWN}，由调用方跳过整条规则。
     */
    public static Outcome evaluate(String json, Clock clock) {
        if (json == null || json.isBlank()) {
            return Outcome.MATCH;
        }
        Clock used = clock == null ? Clock.systemUTC() : clock;
        Parsed parsed = parse(json.trim());
        if (parsed == null) {
            return Outcome.UNKNOWN;
        }
        LocalTime now = LocalTime.ofInstant(used.instant(), ZoneOffset.UTC);
        if (inWindow(now, parsed.after, parsed.before)) {
            return Outcome.MATCH;
        }
        return Outcome.MISS;
    }

    /**
     * after 为空表示不限制起点；before 为空表示不限制终点。
     * 起点晚于终点时按跨日：命中 [after, 24:00) 或 [00:00, before)。
     */
    static boolean inWindow(LocalTime now, LocalTime after, LocalTime before) {
        if (after == null && before == null) {
            return true;
        }
        if (after != null && before != null && !after.isBefore(before)) {
            return !now.isBefore(after) || now.isBefore(before);
        }
        if (after != null && now.isBefore(after)) {
            return false;
        }
        return before == null || now.isBefore(before);
    }

    /** 只拆一层对象。嵌套、数组、无引号键都当成不能解释。 */
    private static Parsed parse(String json) {
        if (!json.startsWith("{") || !json.endsWith("}")) {
            return null;
        }
        String inner = json.substring(1, json.length() - 1).trim();
        if (inner.isEmpty()) {
            return new Parsed(null, null);
        }
        LocalTime after = null;
        LocalTime before = null;
        for (String part : inner.split(",")) {
            int colon = part.indexOf(':');
            if (colon < 0) {
                return null;
            }
            String key = unquote(part.substring(0, colon).trim());
            String value = unquote(part.substring(colon + 1).trim());
            if (key == null || value == null) {
                return null;
            }
            LocalTime time = time(value);
            if (time == null) {
                return null;
            }
            if ("after".equals(key)) {
                if (after != null) {
                    return null;
                }
                after = time;
            } else if ("before".equals(key)) {
                if (before != null) {
                    return null;
                }
                before = time;
            } else {
                return null;
            }
        }
        return new Parsed(after, before);
    }

    /** 键和值都必须带双引号，避免把数字键或裸时间当成合法 JSON。 */
    private static String unquote(String token) {
        if (token.length() < 2 || token.charAt(0) != '"' || token.charAt(token.length() - 1) != '"') {
            return null;
        }
        return token.substring(1, token.length() - 1);
    }

    /** 非法时分返回 null，由 parse 整条判为 UNKNOWN。 */
    private static LocalTime time(String value) {
        try {
            return LocalTime.parse(value, HOUR_MINUTE);
        } catch (DateTimeException ex) {
            return null;
        }
    }

    /**
     * 条件求值结果。UNKNOWN 表示 JSON 还不能解释。
     */
    public enum Outcome {
        /** 无条件或当前时刻落在窗内。 */
        MATCH,
        /** 认识这条条件，但当前时刻不在窗内。 */
        MISS,
        /** JSON 无效或含还不支持的键。 */
        UNKNOWN
    }

    private record Parsed(LocalTime after, LocalTime before) {
    }
}
