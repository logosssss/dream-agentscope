package com.zhu.bootstrap;

import com.zhu.common.security.WakeupTokens;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 内部唤醒令牌比较与占位串拒收。不连网络。
 */
class WakeupTokensTest {

    /** 内部唤醒路径识别。 */
    @Test
    void recognizesInternalWakeupPath() {
        assertThat(WakeupTokens.isInternalWakeup("/api/internal/wakeups")).isTrue();
        assertThat(WakeupTokens.isInternalWakeup("/api/sessions")).isFalse();
    }

    /** 文档占位串不论大小写都视为不安全。 */
    @Test
    void rejectsDocumentedPlaceholders() {
        assertThat(WakeupTokens.isInsecurePlaceholder("change-me-wakeup")).isTrue();
        assertThat(WakeupTokens.isInsecurePlaceholder("CHANGE-ME")).isTrue();
        assertThat(WakeupTokens.isInsecurePlaceholder("real-wakeup-token")).isFalse();
        assertThat(WakeupTokens.matches("change-me-wakeup", "change-me-wakeup")).isFalse();
    }

    /** 启动校验：空白和占位串都抛。 */
    @Test
    void requireSecureRejectsBlankAndPlaceholder() {
        assertThatThrownBy(() -> WakeupTokens.requireSecure("agentscope.wakeup.token", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> WakeupTokens.requireSecure("agentscope.wakeup.token", "change-me-wakeup"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
        WakeupTokens.requireSecure("agentscope.wakeup.token", "real-wakeup-token");
    }

    /** 定长比较：对得上才通过。 */
    @Test
    void matchesOnlyEqualNonBlank() {
        assertThat(WakeupTokens.matches("secret-a", "secret-a")).isTrue();
        assertThat(WakeupTokens.matches("secret-a", "secret-b")).isFalse();
        assertThat(WakeupTokens.matches("", "secret-a")).isFalse();
    }
}
