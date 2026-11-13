package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.zhu.access.api.dto.chat.ChatRequest;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.application.chat.ApiChatAppService;
import com.zhu.access.application.chat.ChatAppService;
import com.zhu.access.application.chat.gateway.AccessChannelGateway;
import com.zhu.access.application.chat.message.AbsentChatMessageStore;
import com.zhu.access.application.chat.run.ConcurrentRunGuard;
import com.zhu.access.application.chat.session.SessionOwnerGate;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import java.time.Clock;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

/**
 * API Channel 落地 ③：旧路径拒绝显式 API；ApiChatAppService 走 Gateway.executeApi。
 */
class ApiAgentChatPathTest {

    @AfterEach
    void clearIdentity() {
        TenantContext.clear();
        UserContext.clear();
    }

    /** 旧 /api/chat 形状：显式 channel=API → BAD_REQUEST。 */
    @Test
    void webChatPath_rejectsExplicitApiChannel() {
        TenantContext.set("demo");
        UserContext.set("u1");
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any())).thenReturn(new AgentResponse("r1", "ok"));
        ChatAppService chats = webChats(executor);
        ChatRequest api = new ChatRequest(null, null, null, "s1", null, "hi", AccessChannel.API);
        assertThatThrownBy(() -> chats.chat(api))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> chats.stream(api).blockFirst())
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getMessage()).contains("/api/agent/chat"));
    }

    /** ApiChatAppService 调用 executeApi，且会话通道记为 API。 */
    @Test
    void apiChatPath_usesExecuteApi_andRecordsApiChannel() {
        TenantContext.set("demo");
        UserContext.set("u1");
        AtomicReference<String> recordedChannel = new AtomicReference<>();
        AtomicReference<Boolean> apiCalled = new AtomicReference<>(false);
        AccessChannelGateway gateway = mock(AccessChannelGateway.class);
        when(gateway.executeApi(any(AgentRequest.class))).thenAnswer(invocation -> {
            apiCalled.set(true);
            return new AgentResponse("api-run", "api-ok");
        });
        when(gateway.streamApi(any(AgentRequest.class))).thenReturn(Flux.empty());
        ApiChatAppService apiChats = new ApiChatAppService(
                gateway,
                (key, variables, fallback) -> fallback,
                new TestSemanticProvider(),
                new QuotaGuard(new FakeAccessCache(), new AccessLimitProperties(), Clock.systemUTC()),
                new ConcurrentRunGuard(new FakeAccessCache(), 120),
                (tenant, user, session, channel, text) -> recordedChannel.set(channel),
                (tenant, user, session, trigger, status, runId, input, output, error, steps, started) -> {
                },
                tenantId -> {
                },
                (tenantId, userId) -> {
                },
                allowAllOwners(),
                new AbsentChatMessageStore());
        AgentResponse response = apiChats.chat(new ChatRequest(null, null, null, "s1", null, "hi", AccessChannel.WEB));
        assertThat(apiCalled.get()).isTrue();
        assertThat(response.text()).isEqualTo("api-ok");
        assertThat(recordedChannel.get()).isEqualTo(AccessChannel.API.name());
    }

    private static ChatAppService webChats(AgentExecutor executor) {
        return new ChatAppService(
                executor,
                (key, variables, fallback) -> fallback,
                new TestSemanticProvider(),
                new QuotaGuard(new FakeAccessCache(), new AccessLimitProperties(), Clock.systemUTC()),
                new ConcurrentRunGuard(new FakeAccessCache(), 120),
                (tenant, user, session, channel, text) -> {
                },
                (tenant, user, session, trigger, status, runId, input, output, error, steps, started) -> {
                },
                tenantId -> {
                },
                (tenantId, userId) -> {
                },
                allowAllOwners(),
                new AbsentChatMessageStore());
    }

    private static SessionOwnerGate allowAllOwners() {
        return (tenant, user, session) -> {
        };
    }
}
