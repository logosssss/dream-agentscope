package com.zhu.bootstrap;

import com.zhu.access.api.dto.chat.ChatRequest;
import com.zhu.access.application.chat.message.AbsentChatMessageStore;
import com.zhu.access.application.chat.ChatAppService;
import com.zhu.access.application.chat.run.ConcurrentRunGuard;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.access.infrastructure.security.AccessIdentityFilter;
import com.zhu.common.context.RequestIdContext;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.semantic.api.prompt.PromptService;
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
 * 对话身份只来自线程里的登录会话。请求体里的租户和用户不采用。
 */
class AccessIdentityTest {

    @AfterEach
    void clearIdentity() {
        TenantContext.clear();
        UserContext.clear();
        RequestIdContext.clear();
    }

    /** 没写通道时按 WEB。请求体里的租户不覆盖会话。 */
    @Test
    void blankChannelDefaultsToWebAndSessionWins() {
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any())).thenReturn(new AgentResponse("run-1", "ok"));
        TenantContext.set("header-tenant");
        UserContext.set("header-user");
        ChatRequest request = new ChatRequest(null, "body-tenant", null, "session-1", "客户端人设", "hi", null);

        PromptService prompts = (key, variables, fallback) -> fallback;
        QuotaGuard quota = new QuotaGuard(new FakeAccessCache(), new AccessLimitProperties(), Clock.systemUTC());
        new ChatAppService(executor, prompts, new TestSemanticProvider(), quota, new ConcurrentRunGuard(new FakeAccessCache(), 120), (tenant, user, session, channel, text) -> {
        }, (tenant, user, session, trigger, status, runId, input, output, error, steps, started) -> {
        }, tenantId -> {
        }, (tenantId, userId) -> {
        }, (tenantId, userId, sessionId) -> {
        }, new AbsentChatMessageStore()).chat(request);

        ArgumentCaptor<AgentRequest> sent = ArgumentCaptor.forClass(AgentRequest.class);
        verify(executor).execute(sent.capture());
        assertThat(request.channelOrDefault()).isEqualTo(AccessChannel.WEB);
        assertThat(sent.getValue().tenantId()).isEqualTo("header-tenant");
        assertThat(sent.getValue().userId()).isEqualTo("header-user");
        assertThat(sent.getValue().systemPrompt()).isEqualTo(".");
    }

    /** 请求头里的租户和用户不写入上下文。请求号仍然可见，链结束必须清掉。 */
    @Test
    void filterIgnoresIdentityHeadersAndClearsAfter() throws Exception {
        AccessIdentityFilter filter = new AccessIdentityFilter(caches(null));
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

        assertThat(seenTenant.get()).isNull();
        assertThat(seenUser.get()).isNull();
        assertThat(seenRequest.get()).isEqualTo("req-1");
        assertThat(TenantContext.get()).isNull();
        assertThat(UserContext.get()).isNull();
        assertThat(RequestIdContext.get()).isNull();
    }

    /** Bearer 登录会话优先于请求头。无效令牌直接 401。 */
    @Test
    void bearerOverridesHeaders() throws Exception {
        FakeAccessCache cache = new FakeAccessCache();
        cache.put("login-session:tok-1", "tenant-a\nuser-a", java.time.Duration.ofHours(1));
        AccessIdentityFilter filter = new AccessIdentityFilter(caches(cache));
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(AccessIdentityFilter.AUTHORIZATION_HEADER, "Bearer tok-1");
        request.addHeader(AccessIdentityFilter.TENANT_HEADER, "header-tenant");
        request.addHeader(AccessIdentityFilter.USER_HEADER, "header-user");
        AtomicReference<String> seenTenant = new AtomicReference<>();
        AtomicReference<String> seenUser = new AtomicReference<>();

        filter.doFilter(request, new MockHttpServletResponse(), (req, res) -> {
            seenTenant.set(TenantContext.get());
            seenUser.set(UserContext.get());
        });

        assertThat(seenTenant.get()).isEqualTo("tenant-a");
        assertThat(seenUser.get()).isEqualTo("user-a");

        MockHttpServletResponse rejected = new MockHttpServletResponse();
        MockHttpServletRequest bad = new MockHttpServletRequest();
        bad.addHeader(AccessIdentityFilter.AUTHORIZATION_HEADER, "Bearer missing");
        filter.doFilter(bad, rejected, (req, res) -> {
        });
        assertThat(rejected.getStatus()).isEqualTo(401);
    }

    private static org.springframework.beans.factory.ObjectProvider<com.zhu.access.infrastructure.cache.AccessCache> caches(
            com.zhu.access.infrastructure.cache.AccessCache cache) {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public com.zhu.access.infrastructure.cache.AccessCache getObject() {
                return cache;
            }

            @Override
            public com.zhu.access.infrastructure.cache.AccessCache getIfAvailable() {
                return cache;
            }

            @Override
            public com.zhu.access.infrastructure.cache.AccessCache getIfUnique() {
                return cache;
            }
        };
    }
}
