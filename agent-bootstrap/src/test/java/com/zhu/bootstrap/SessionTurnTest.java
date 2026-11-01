package com.zhu.bootstrap;

import com.zhu.access.application.ChatAppService;
import com.zhu.access.application.QuotaGuard;
import com.zhu.access.application.SessionTurnGuard;
import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentResponse;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 同一会话进行中时，第二次对话被拒绝。结束后可以再进入。
 */
class SessionTurnTest {

    /** 执行过程中再开一轮同一会话会冲突，结束后锁已释放。 */
    @Test
    void busySessionRejectsUntilTheCallFinishes() {
        FakeAccessCache cache = new FakeAccessCache();
        SessionTurnGuard turns = new SessionTurnGuard(cache, 120);
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any())).thenAnswer(invocation -> {
            assertThat(cache.lock("turn:t:u:s", "other", Duration.ofMinutes(1))).isFalse();
            return new AgentResponse("run-1", "ok");
        });
        ChatAppService chats = new ChatAppService(
                executor,
                (key, variables, fallback) -> fallback,
                new TestSemanticProvider(),
                new QuotaGuard(cache, new AccessLimitProperties(), Clock.systemUTC()),
                turns,
                (tenant, user, session, channel, text) -> {
                },
                (tenant, user, session, trigger, status, runId, input, output, error, steps, started) -> {
                },
                tenantId -> {
                },
                (tenantId, userId) -> {
                },
                (tenantId, userId, sessionId) -> {
                });

        chats.chat(new ChatRequest(null, "t", "u", "s", null, "hi", null));

        assertThat(cache.lock("turn:t:u:s", "again", Duration.ofMinutes(1))).isTrue();
    }

    /** 另一个会话不受影响。用户或会话为空时不占锁。 */
    @Test
    void blankSessionIsRejected() {
        SessionTurnGuard turns = new SessionTurnGuard(new FakeAccessCache(), 0);
        assertThatThrownBy(() -> turns.open("t", "u", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
    }
}
