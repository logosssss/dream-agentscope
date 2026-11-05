package com.zhu.bootstrap;

import com.zhu.common.security.CallerTokens;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 调用方令牌比较与占位串拒收。不连网络。
 */
class CallerTokensTest {

    /** 文档占位串不论大小写都视为不安全。 */
    @Test
    void rejectsDocumentedPlaceholders() {
        assertThat(CallerTokens.isInsecurePlaceholder("change-me-caller")).isTrue();
        assertThat(CallerTokens.isInsecurePlaceholder("CHANGE-ME-ADMIN")).isTrue();
        assertThat(CallerTokens.isInsecurePlaceholder("test-token")).isFalse();
        assertThat(CallerTokens.matches("change-me-caller", "change-me-caller")).isFalse();
    }

    /** 启动校验：空白和占位串都抛。 */
    @Test
    void requireSecureRejectsBlankAndPlaceholder() {
        assertThatThrownBy(() -> CallerTokens.requireSecure("agentscope.caller.token", " "))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> CallerTokens.requireSecure("agentscope.admin.token", "change-me-admin"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("placeholder");
        CallerTokens.requireSecure("agentscope.caller.token", "real-token-value");
    }
}
