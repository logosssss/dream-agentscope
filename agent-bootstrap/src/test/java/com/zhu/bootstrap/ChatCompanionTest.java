package com.zhu.bootstrap;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.application.chat.AbsentChatMessageStore;
import com.zhu.access.application.chat.ChatAppService;
import com.zhu.access.application.chat.ConcurrentRunGuard;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.application.chat.SessionTurnGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentRequest;
import com.zhu.runtime.api.AgentResponse;
import java.time.Clock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 搭子对话必须有用户，人设不接受请求体改写。不连接 MySQL。
 */
class ChatCompanionTest {

    /** 对话会把用户留在线程上，结束时清掉。 */
    @AfterEach
    void clearIdentity() {
        TenantContext.clear();
        UserContext.clear();
    }

    /** 没有用户时不执行。请求体里的提示词丢掉。 */
    @Test
    void userIsRequiredAndClientPromptIsDropped() {
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any())).thenReturn(new AgentResponse("run-1", "ok"));
        ChatAppService chats = service(executor, (tenant, user, session) -> {
        });

        assertThatThrownBy(() -> chats.chat(new ChatRequest(null, "t", " ", "s", "客户端人设", "hi", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.UNAUTHORIZED);
        verify(executor, never()).execute(any());

        TenantContext.set("t");
        UserContext.set("u");
        chats.chat(new ChatRequest(null, "t", "u", "s", "客户端人设", "hi", null));
        ArgumentCaptor<AgentRequest> sent = ArgumentCaptor.forClass(AgentRequest.class);
        verify(executor).execute(sent.capture());
        assertThat(sent.getValue().systemPrompt()).isEqualTo(".");
        assertThat(sent.getValue().systemPrompt()).isNotEqualTo("客户端人设");
    }

    /** 会话已属于别人时不执行。 */
    @Test
    void sessionOwnedByAnotherUserIsRejected() {
        AgentExecutor executor = mock(AgentExecutor.class);
        ChatAppService chats = service(executor, (tenant, user, session) -> {
            throw new BusinessException(ErrorCode.CONFLICT, "session belongs to another user");
        });

        TenantContext.set("t");
        UserContext.set("u2");
        assertThatThrownBy(() -> chats.chat(new ChatRequest(null, "t", "u2", "s", null, "hi", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(executor, never()).execute(any());
    }

    /** 额度不限制。会话归属由测试传入。 */
    private static ChatAppService service(AgentExecutor executor, com.zhu.access.application.chat.SessionOwnerGate owners) {
        return new ChatAppService(
                executor,
                (key, variables, fallback) -> fallback,
                new TestSemanticProvider(),
                new QuotaGuard(new FakeAccessCache(), new AccessLimitProperties(), Clock.systemUTC()),
                new SessionTurnGuard(new FakeAccessCache(), 120),
                new ConcurrentRunGuard(new FakeAccessCache(), 120),
                (tenant, user, session, channel, text) -> {
                },
                (tenant, user, session, trigger, status, runId, input, output, error, steps, started) -> {
                },
                tenantId -> {
                },
                (tenantId, userId) -> {
                },
                owners,
                new AbsentChatMessageStore());
    }
}
