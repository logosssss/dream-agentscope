package com.zhu.bootstrap;

import com.zhu.semantic.domain.PermissionConditions;
import com.zhu.semantic.domain.PermissionConditions.Outcome;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 权限条件只认时分窗和参数长度。未知键或非标量参数整条不能解释。
 */
class PermissionConditionsTest {

    /** 空白和空对象无条件命中。 */
    @Test
    void blankAndEmptyMatch() {
        assertThat(PermissionConditions.evaluate(null, Clock.systemUTC())).isEqualTo(Outcome.MATCH);
        assertThat(PermissionConditions.evaluate("{}", Clock.systemUTC())).isEqualTo(Outcome.MATCH);
    }

    /** maxLen 超界 MISS；恰好等于上限仍命中。 */
    @Test
    void maxLenBoundsParam() {
        Clock clock = Clock.systemUTC();
        String json = "{\"maxLen\":{\"content\":5}}";
        assertThat(PermissionConditions.evaluate(json, clock, Map.of("content", "12345")))
                .isEqualTo(Outcome.MATCH);
        assertThat(PermissionConditions.evaluate(json, clock, Map.of("content", "123456")))
                .isEqualTo(Outcome.MISS);
        assertThat(PermissionConditions.evaluate(json, clock, Map.of()))
                .isEqualTo(Outcome.MATCH);
    }

    /** minLen 缺参数按 0，达不到下限则 MISS。 */
    @Test
    void minLenRequiresParam() {
        Clock clock = Clock.systemUTC();
        String json = "{\"minLen\":{\"path\":1}}";
        assertThat(PermissionConditions.evaluate(json, clock, Map.of("path", "a")))
                .isEqualTo(Outcome.MATCH);
        assertThat(PermissionConditions.evaluate(json, clock, null)).isEqualTo(Outcome.MISS);
    }

    /** 时间窗与长度同时存在时取与。 */
    @Test
    void windowAndMaxLenBothApply() {
        Clock evening = Clock.fixed(Instant.parse("2026-11-02T19:00:00Z"), ZoneOffset.UTC);
        String json = "{\"after\":\"18:00\",\"maxLen\":{\"content\":3}}";
        assertThat(PermissionConditions.evaluate(json, evening, Map.of("content", "ab")))
                .isEqualTo(Outcome.MATCH);
        assertThat(PermissionConditions.evaluate(json, evening, Map.of("content", "abcd")))
                .isEqualTo(Outcome.MISS);
    }

    /** 嵌套对象参数不能量长度，整条 UNKNOWN。 */
    @Test
    void nestedParamIsUnknown() {
        assertThat(PermissionConditions.evaluate(
                        "{\"maxLen\":{\"meta\":1}}",
                        Clock.systemUTC(),
                        Map.of("meta", Map.of("k", "v"))))
                .isEqualTo(Outcome.UNKNOWN);
    }

    /** 未知键仍 UNKNOWN，不能当成无条件。 */
    @Test
    void unknownKeyIsUnknown() {
        assertThat(PermissionConditions.evaluate("{\"paramMax\":10}", Clock.systemUTC()))
                .isEqualTo(Outcome.UNKNOWN);
    }
}
