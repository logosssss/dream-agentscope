package com.zhu.bootstrap;

import com.zhu.access.application.chat.AbsentChatMessageStore;
import com.zhu.access.application.chat.ChatAppService;
import com.zhu.access.application.chat.ConcurrentRunGuard;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentResponse;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 同会话排队改由 Channel Gateway 负责后，ChatAppService 不再占 turn 锁。
 */
class SessionTurnTest {

    /** 对话会把用户留在线程上，结束时清掉。 */
    @AfterEach
    void clearIdentity() {
        TenantContext.clear();
        UserContext.clear();
    }

    /** 对话进行中不再写 turn 键；同会话串行交给 Gateway。 */
    @Test
    void chatDoesNotOccupySessionTurnLock() {
        FakeAccessCache cache = new FakeAccessCache();
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any())).thenAnswer(invocation -> {
            assertThat(cache.lock("turn:t:u:s", "probe", Duration.ofMinutes(1))).isTrue();
            cache.unlock("turn:t:u:s", "probe");
            return new AgentResponse("run-1", "ok");
        });
        ChatAppService chats = new ChatAppService(
                executor,
                (key, variables, fallback) -> fallback,
                new TestSemanticProvider(),
                new QuotaGuard(cache, new AccessLimitProperties(), Clock.systemUTC()),
                new ConcurrentRunGuard(cache, 120),
                (tenant, user, session, channel, text) -> {
                },
                (tenant, user, session, trigger, status, runId, input, output, error, steps, started) -> {
                },
                tenantId -> {
                },
                (tenantId, userId) -> {
                },
                (tenantId, userId, sessionId) -> {
                },
                new AbsentChatMessageStore());

        TenantContext.set("t");
        UserContext.set("u");
        chats.chat(new ChatRequest(null, "t", "u", "s", null, "hi", null));

        assertThat(cache.lock("turn:t:u:s", "again", Duration.ofMinutes(1))).isTrue();
    }
}
