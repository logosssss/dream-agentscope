package com.zhu.bootstrap;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.application.chat.AbsentChatMessageStore;
import com.zhu.access.application.chat.ChatAppService;
import com.zhu.access.application.chat.ConcurrentRunGuard;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.AgentExecutor;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * 对话在执行前检查租户用户。不连接 MySQL。
 */
class ChatTenantUserTest {

    /** 对话会把用户留在线程上，结束时清掉。 */
    @AfterEach
    void clearIdentity() {
        TenantContext.clear();
        UserContext.clear();
    }

    /** 没登记或不可用时不进模型。 */
    @Test
    void rejectsBeforeExecute() {
        AgentExecutor executor = mock(AgentExecutor.class);
        ChatAppService chats = new ChatAppService(
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
                    throw new BusinessException(ErrorCode.NOT_FOUND, "tenant user not found");
                },
                (tenantId, userId, sessionId) -> {
                },
                new AbsentChatMessageStore());

        TenantContext.set("t");
        UserContext.set("u");
        assertThatThrownBy(() -> chats.chat(new ChatRequest(null, "t", "u", "s", null, "hi", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verifyNoInteractions(executor);
    }
}
