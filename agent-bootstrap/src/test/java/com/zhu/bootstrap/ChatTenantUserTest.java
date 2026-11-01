package com.zhu.bootstrap;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.application.ChatAppService;
import com.zhu.access.application.QuotaGuard;
import com.zhu.access.application.SessionTurnGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.AgentExecutor;
import java.time.Clock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 对话在执行前检查租户用户。不连接 MySQL。
 */
class ChatTenantUserTest {

    /** 没登记或不可用时不进模型。 */
    @Test
    void rejectsBeforeExecute() {
        AgentExecutor executor = mock(AgentExecutor.class);
        ChatAppService chats = new ChatAppService(
                executor,
                (key, variables, fallback) -> fallback,
                new TestSemanticProvider(),
                new QuotaGuard(new FakeAccessCache(), new AccessLimitProperties(), Clock.systemUTC()),
                new SessionTurnGuard(new FakeAccessCache(), 120),
                (tenant, user, session, channel, text) -> {
                },
                (tenant, user, session, trigger, status, runId, input, output, error, steps, started) -> {
                },
                tenantId -> {
                },
                (tenantId, userId) -> {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "tenant user not found");
                },
                (tenantId, userId, sessionId) -> {
                });

        assertThatThrownBy(() -> chats.chat(new ChatRequest(null, "t", "u", "s", null, "hi", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verifyNoInteractions(executor);
    }
}
