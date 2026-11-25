package com.zhu.access.application.chat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.zhu.common.context.TenantContext;
import com.zhu.semantic.api.prompt.PlatformPrompt;
import com.zhu.semantic.api.prompt.PromptService;
import com.zhu.semantic.contract.product.ProductSemanticProvider;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * 调用方租户和人设。不启动 Spring。
 */
class AccessCallerTest {

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /** 线程上没有租户时写成空串。有值则原样带上，包括空白。 */
    @Test
    void tenantNullBecomesEmpty() {
        TenantContext.clear();
        assertThat(AccessCaller.tenantId()).isEmpty();
        TenantContext.set("  ");
        assertThat(AccessCaller.tenantId()).isEqualTo("  ");
        TenantContext.set("t");
        assertThat(AccessCaller.tenantId()).isEqualTo("t");
    }

    /** 人设键固定，兜底来自产品语义。请求体里的 systemPrompt 没有入口。 */
    @Test
    void systemPromptUsesProductKeyAndFallback() {
        AtomicReference<String> key = new AtomicReference<>();
        AtomicReference<Map<String, String>> variables = new AtomicReference<>();
        PromptService prompts = (promptKey, promptVariables, fallback) -> {
            key.set(promptKey);
            variables.set(promptVariables);
            return "persona:" + fallback;
        };
        ProductSemanticProvider semantics = mock(ProductSemanticProvider.class);
        when(semantics.promptFallback(PlatformPrompt.SYSTEM_KEY)).thenReturn("local");
        assertThat(AccessCaller.systemPrompt(prompts, semantics)).isEqualTo("persona:local");
        assertThat(key.get()).isEqualTo(PlatformPrompt.SYSTEM_KEY);
        assertThat(variables.get()).isEmpty();
        verify(semantics).promptFallback(PlatformPrompt.SYSTEM_KEY);
    }
}
