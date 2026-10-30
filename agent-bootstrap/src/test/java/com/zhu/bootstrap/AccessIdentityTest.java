package com.zhu.bootstrap;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.application.ChatAppService;
import com.zhu.access.application.QuotaGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.access.infrastructure.security.AccessIdentityFilter;
import com.zhu.common.context.RequestIdContext;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.semantic.api.PromptService;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 请求体身份优先，请求头只补空字段。过滤器在请求结束后清掉线程上下文。
 */
class AccessIdentityTest {

    @AfterEach
    void clearIdentity() {
        TenantContext.clear();
        UserContext.clear();
        RequestIdContext.clear();
    }

    /** 没写通道时按 WEB。通道不进入执行入参。 */
    @Test
    void blankChannelDefaultsToWebAndBodyWins() {
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any())).thenReturn(new AgentResponse("run-1", "ok"));
        TenantContext.set("header-tenant");
        UserContext.set("header-user");
        ChatRequest request = new ChatRequest(null, "body-tenant", null, "session-1", null, "hi", null);

        PromptService prompts = (key, variables, fallback) -> fallback;
        QuotaGuard quota = new QuotaGuard(new FakeAccessCache(), new AccessLimitProperties(), Clock.systemUTC());
        new ChatAppService(executor, prompts, quota).chat(request);

        ArgumentCaptor<AgentRequest> sent = ArgumentCaptor.forClass(AgentRequest.class);
        verify(executor).execute(sent.capture());
        assertThat(request.channelOrDefault()).isEqualTo(AccessChannel.WEB);
        assertThat(sent.getValue().tenantId()).isEqualTo("body-tenant");
        assertThat(sent.getValue().userId()).isEqualTo("header-user");
        assertThat(sent.getValue().systemPrompt()).isEqualTo(".");
    }

    /** 头在过滤器链内可见，链结束必须清掉。没有头也不拒绝。 */
    @Test
    void filterSetsHeaderAndClearsAfter() throws Exception {
        AccessIdentityFilter filter = new AccessIdentityFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AccessIdentityFilter.TENANT_HEADER, "t1");
        request.addHeader(AccessIdentityFilter.USER_HEADER, " ");
        request.addHeader(AccessIdentityFilter.REQUEST_ID_HEADER, "req-1");
        AtomicReference<String> seenTenant = new AtomicReference<>();
        AtomicReference<String> seenUser = new AtomicReference<>();
        AtomicReference<String> seenRequest = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            seenTenant.set(TenantContext.get());
            seenUser.set(UserContext.get());
            seenRequest.set(RequestIdContext.get());
        });

        assertThat(seenTenant.get()).isEqualTo("t1");
        assertThat(seenUser.get()).isNull();
        assertThat(seenRequest.get()).isEqualTo("req-1");
        assertThat(TenantContext.get()).isNull();
        assertThat(UserContext.get()).isNull();
        assertThat(RequestIdContext.get()).isNull();
    }
}
