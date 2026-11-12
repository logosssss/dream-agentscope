package com.zhu.semantic.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.format.SignStyle;
import java.time.temporal.ChronoField;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 权限规则上的条件。认 UTC 时分窗和参数长度范围；不能解释的 JSON 不当成无条件命中。
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

    /** 只做条件解析，不复用业务侧的 ObjectMapper 配置。 */
    private static final ObjectMapper JSON = new ObjectMapper();

    /** 工具类，不实例化。 */
    private PermissionConditions() {
    }

    /**
     * 空白或空对象无条件命中。只接受 after / before / maxLen / minLen。
     * 不能解释时返回 {@link Outcome#UNKNOWN}，由调用方跳过整条规则。
     */
    public static Outcome evaluate(String json, Clock clock) {
        return evaluate(json, clock, null);
    }

    /**
     * 带工具入参求值。时间窗与参数范围同时存在时取与。
     * 缺参数按长度 0；参数不是标量时整条 UNKNOWN。
     */
    public static Outcome evaluate(String json, Clock clock, Map<String, ?> params) {
        if (json == null || json.isBlank()) {
            return Outcome.MATCH;
        }
        Clock used = clock == null ? Clock.systemUTC() : clock;
        Parsed parsed = parse(json.trim());
        if (parsed == null) {
            return Outcome.UNKNOWN;
        }
        LocalTime now = LocalTime.ofInstant(used.instant(), ZoneOffset.UTC);
        if (!inWindow(now, parsed.after, parsed.before)) {
            return Outcome.MISS;
        }
        return matchLengths(parsed.maxLen, parsed.minLen, params);
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

    /**
     * maxLen：该参数字符串长度不超过上限才命中；minLen：不少于下限才命中。
     * 缺省参数按长度 0。复杂对象无法量长度时 UNKNOWN。
     */
    static Outcome matchLengths(Map<String, Integer> maxLen, Map<String, Integer> minLen, Map<String, ?> params) {
        Map<String, ?> actual = params == null ? Map.of() : params;
        for (Map.Entry<String, Integer> bound : maxLen.entrySet()) {
            Integer length = scalarLength(actual.get(bound.getKey()));
            if (length == null) {
                return Outcome.UNKNOWN;
            }
            if (length > bound.getValue()) {
                return Outcome.MISS;
            }
        }
        for (Map.Entry<String, Integer> bound : minLen.entrySet()) {
            Integer length = scalarLength(actual.get(bound.getKey()));
            if (length == null) {
                return Outcome.UNKNOWN;
            }
            if (length < bound.getValue()) {
                return Outcome.MISS;
            }
        }
        return Outcome.MATCH;
    }

    /**
     * 只拆一层对象。嵌套只允许 maxLen / minLen 的参数表；其它嵌套、数组、未知键都当成不能解释。
     */
    private static Parsed parse(String json) {
        JsonNode root;
        try {
            root = JSON.readTree(json);
        } catch (Exception ex) {
            return null;
        }
        if (root == null || !root.isObject()) {
            return null;
        }
        LocalTime after = null;
        LocalTime before = null;
        Map<String, Integer> maxLen = Map.of();
        Map<String, Integer> minLen = Map.of();
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String key = field.getKey();
            JsonNode value = field.getValue();
            if ("after".equals(key)) {
                if (after != null || value == null || !value.isTextual()) {
                    return null;
                }
                after = time(value.asText());
                if (after == null) {
                    return null;
                }
            } else if ("before".equals(key)) {
                if (before != null || value == null || !value.isTextual()) {
                    return null;
                }
                before = time(value.asText());
                if (before == null) {
                    return null;
                }
            } else if ("maxLen".equals(key)) {
                if (!maxLen.isEmpty()) {
                    return null;
                }
                Map<String, Integer> parsed = lengthBounds(value);
                if (parsed == null) {
                    return null;
                }
                maxLen = parsed;
            } else if ("minLen".equals(key)) {
                if (!minLen.isEmpty()) {
                    return null;
                }
                Map<String, Integer> parsed = lengthBounds(value);
                if (parsed == null) {
                    return null;
                }
                minLen = parsed;
            } else {
                return null;
            }
        }
        return new Parsed(after, before, maxLen, minLen);
    }

    /**
     * 参数名非空，上限/下限是非负整数。空对象表示没有长度条件。
     */
    private static Map<String, Integer> lengthBounds(JsonNode value) {
        if (value == null || !value.isObject()) {
            return null;
        }
        Map<String, Integer> bounds = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String name = field.getKey();
            JsonNode raw = field.getValue();
            if (name == null || name.isBlank() || raw == null || !raw.isIntegralNumber() || raw.isBigInteger()) {
                return null;
            }
            int bound = raw.intValue();
            if (bound < 0 || raw.asLong() != bound) {
                return null;
            }
            bounds.put(name, bound);
        }
        return bounds.isEmpty() ? Map.of() : Collections.unmodifiableMap(bounds);
    }

    /**
     * 标量才能量长度。null 按 0；Map / 数组 / 其它对象返回 null，由调用方判 UNKNOWN。
     */
    static Integer scalarLength(Object value) {
        if (value == null) {
            return 0;
        }
        if (value instanceof CharSequence text) {
            return text.length();
        }
        if (value instanceof Number || value instanceof Boolean) {
            return String.valueOf(value).length();
        }
        return null;
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
        /** 无条件或当前时刻与参数都落在范围内。 */
        MATCH,
        /** 认识这条条件，但不在时间窗或参数范围。 */
        MISS,
        /** JSON 无效、含还不支持的键，或参数不是标量。 */
        UNKNOWN
    }

    private record Parsed(
            LocalTime after,
            LocalTime before,
            Map<String, Integer> maxLen,
            Map<String, Integer> minLen) {
    }
}
