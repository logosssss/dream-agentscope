package com.zhu.bootstrap;

import com.zhu.access.api.dto.ChatMessageItem;
import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.api.dto.SessionDetail;
import com.zhu.access.application.chat.AbsentChatMessageStore;
import com.zhu.access.application.chat.ChatAppService;
import com.zhu.access.application.chat.ChatMessageStore;
import com.zhu.access.application.chat.ConcurrentRunGuard;
import com.zhu.access.application.account.QuotaGuard;
import com.zhu.access.application.chat.SessionListService;
import com.zhu.access.application.chat.SessionMetaStore;
import com.zhu.access.application.chat.SessionTurnGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentResponse;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 成功对话双写气泡；读接口先校验归属。不连接 MySQL。
 */
class ChatMessageHistoryTest {

    /** 对话会把用户留在线程上，结束时清掉。 */
    @AfterEach
    void clearIdentity() {
        TenantContext.clear();
        UserContext.clear();
    }

    /** 成功后追加 user + assistant，失败不写。 */
    @Test
    void successAppendsTurnFailureSkips() {
        RecordingMessages store = new RecordingMessages();
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any()))
                .thenReturn(new AgentResponse("run-1", "你好"))
                .thenThrow(new IllegalStateException("boom"));
        ChatAppService chats = chats(executor, store);

        TenantContext.set("t");
        UserContext.set("u");
        chats.chat(new ChatRequest(null, "t", "u", "s1", null, "嗨", null));
        assertThatThrownBy(() -> chats.chat(new ChatRequest(null, "t", "u", "s1", null, "再问", null)))
                .isInstanceOf(IllegalStateException.class);

        assertThat(store.turns).hasSize(1);
        assertThat(store.turns.get(0).runId()).isEqualTo("run-1");
        assertThat(store.turns.get(0).userText()).isEqualTo("嗨");
        assertThat(store.turns.get(0).assistantText()).isEqualTo("你好");
    }

    /** 别人的会话按找不到，不读消息。 */
    @Test
    void messagesRequireOwnedSession() {
        SessionMetaStore meta = mock(SessionMetaStore.class);
        when(meta.findOwned("t", "u", "missing")).thenReturn(null);
        SessionListService sessions =
                new SessionListService(meta, new FakeAccessCache(), new AbsentChatMessageStore());
        assertThatThrownBy(() -> sessions.messages("t", "u", "missing", 100))
                .isInstanceOf(BusinessException.class)
                .extracting(ex -> ((BusinessException) ex).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /** 本人会话能读到仓储返回的气泡。 */
    @Test
    void messagesReturnsStoreRows() {
        SessionMetaStore meta = mock(SessionMetaStore.class);
        when(meta.findOwned("t", "u", "s1"))
                .thenReturn(new SessionDetail("s1", "标题", "WEB", "ACTIVE", null, 1, "2026-01-01T00:00:00.000"));
        ChatMessageStore store = new ChatMessageStore() {
            @Override
            public void appendTurn(
                    String tenantId,
                    String userId,
                    String sessionId,
                    String runId,
                    String userText,
                    String assistantText) {
            }

            @Override
            public List<ChatMessageItem> list(String tenantId, String userId, String sessionId, int limit) {
                return List.of(new ChatMessageItem(1, "user", "嗨", "run-1", "2026-01-01T00:00:00.000"));
            }
        };
        SessionListService sessions = new SessionListService(meta, new FakeAccessCache(), store);
        assertThat(sessions.messages("t", "u", "s1", 100))
                .extracting(ChatMessageItem::content)
                .containsExactly("嗨");
    }

    private static ChatAppService chats(AgentExecutor executor, ChatMessageStore messages) {
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
                (tenantId, userId, sessionId) -> {
                },
                messages);
    }

    private static final class RecordingMessages implements ChatMessageStore {

        private final List<Turn> turns = new ArrayList<>();

        @Override
        public void appendTurn(
                String tenantId,
                String userId,
                String sessionId,
                String runId,
                String userText,
                String assistantText) {
            turns.add(new Turn(runId, userText, assistantText));
        }

        @Override
        public List<ChatMessageItem> list(String tenantId, String userId, String sessionId, int limit) {
            return List.of();
        }
    }

    private record Turn(String runId, String userText, String assistantText) {
    }
}
