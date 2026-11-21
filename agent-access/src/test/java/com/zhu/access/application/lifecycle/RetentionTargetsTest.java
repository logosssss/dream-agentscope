package com.zhu.access.application.lifecycle;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 保留策略的类型映射是纯函数，这里直接钉住表名与时间列，避免以后改错列还不报错。
 */
class RetentionTargetsTest {

    /** 四种可清理类型的表与时间列。 */
    @Test
    void mapsSupportedTypesToTablesAndTimeColumns() {
        assertThat(RetentionTargets.of("SESSION"))
                .hasValueSatisfying(target -> {
                    assertThat(target.table()).isEqualTo("agent_session_meta");
                    assertThat(target.timeColumn()).isEqualTo("last_active_at");
                    assertThat(target.tenantScoped()).isTrue();
                });
        assertThat(RetentionTargets.of("MESSAGE"))
                .hasValueSatisfying(target -> {
                    assertThat(target.table()).isEqualTo("chat_message");
                    assertThat(target.timeColumn()).isEqualTo("created_at");
                });
        assertThat(RetentionTargets.of("EVENT"))
                .hasValueSatisfying(target -> {
                    assertThat(target.table()).isEqualTo("agent_event_audit");
                    // 审计表没有租户列，只能按时间切。
                    assertThat(target.tenantScoped()).isFalse();
                });
        assertThat(RetentionTargets.of("ATTACHMENT"))
                .hasValueSatisfying(target -> assertThat(target.table()).isEqualTo("attachment"));
    }

    /** 不认识或这次不处理的类型一律空，调用方据此跳过。 */
    @Test
    void rejectsUnknownOrBlankTypes() {
        assertThat(RetentionTargets.of("UNKNOWN")).isEmpty();
        assertThat(RetentionTargets.of("  ")).isEmpty();
        assertThat(RetentionTargets.of(null)).isEmpty();
        assertThat(RetentionTargets.supported())
                .containsExactlyInAnyOrder("SESSION", "MESSAGE", "EVENT", "ATTACHMENT");
    }
}
