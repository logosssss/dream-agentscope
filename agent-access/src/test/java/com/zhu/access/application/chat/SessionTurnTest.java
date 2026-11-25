package com.zhu.access.application.chat;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.api.dto.chat.ChatMessageItem;
import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.run.RunStep;
import com.zhu.access.application.chat.session.SessionIndex;
import com.zhu.runtime.api.agent.AgentRequest;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * 会话与结束落库的身份来源。不启动 Spring。
 */
class SessionTurnTest {

    /** 记会话和结束落库都用已解析请求上的身份，正文用调用方给的标题。 */
    @Test
    void identityComesFromAgentRequest() {
        RecordingSessions sessions = new RecordingSessions();
        RecordingRuns runs = new RecordingRuns();
        RecordingMessages messages = new RecordingMessages();
        AtomicLong linked = new AtomicLong(-1);
        AgentRequest request = new AgentRequest("docs-v1", "tenant-a", "user-a", "session-a", "prompt", "body");
        SessionTurn.remember(sessions, request, "WEB", "shown");
        SessionTurn.finish(
                Logger.getLogger("test"),
                runs,
                sessions,
                messages,
                request,
                "USER",
                RunLog.SUCCEEDED,
                "run-1",
                "req",
                "shown",
                "ok",
                null,
                List.of(),
                3L,
                linked::set);
        assertThat(sessions.tenantId).isEqualTo("tenant-a");
        assertThat(sessions.userId).isEqualTo("user-a");
        assertThat(sessions.sessionId).isEqualTo("session-a");
        assertThat(sessions.channel).isEqualTo("WEB");
        assertThat(sessions.text).isEqualTo("shown");
        assertThat(runs.tenantId).isEqualTo("tenant-a");
        assertThat(runs.trigger).isEqualTo("USER");
        assertThat(messages.tenantId).isEqualTo("tenant-a");
        assertThat(messages.userText).isEqualTo("shown");
        assertThat(linked.get()).isEqualTo(4);
    }

    /** 记下会话索引收到的身份、通道和标题。 */
    private static final class RecordingSessions implements SessionIndex {
        private String tenantId;
        private String userId;
        private String sessionId;
        private String channel;
        private String text;

        /** 原样留下调用参数，供断言身份来自 AgentRequest。 */
        @Override
        public void record(String tenantId, String userId, String sessionId, String channel, String text) {
            this.tenantId = tenantId;
            this.userId = userId;
            this.sessionId = sessionId;
            this.channel = channel;
            this.text = text;
        }
    }

    /** 记下运行记录收到的租户和触发来源。 */
    private static final class RecordingRuns implements RunLog {
        private String tenantId;
        private String trigger;

        /** 只留租户和触发来源，摘要截断由 TurnRecorder 自己测。 */
        @Override
        public void complete(
                String tenantId,
                String userId,
                String sessionId,
                String trigger,
                String status,
                String runId,
                String requestId,
                String inputSummary,
                String outputSummary,
                String errorCode,
                List<RunStep> steps,
                long startedNanos) {
            this.tenantId = tenantId;
            this.trigger = trigger;
        }
    }

    /** 记下气泡正文，并返回固定的 user 序号。 */
    private static final class RecordingMessages implements ChatMessageStore {
        private String tenantId;
        private String userText;

        /** 成功序号固定为 4，用来确认附加动作拿到的是这次写入的序号。 */
        @Override
        public long appendTurn(
                String tenantId,
                String userId,
                String sessionId,
                String runId,
                String userText,
                String assistantText) {
            this.tenantId = tenantId;
            this.userText = userText;
            return 4;
        }

        /** 本测试不读列表。 */
        @Override
        public List<ChatMessageItem> list(String tenantId, String userId, String sessionId, int limit) {
            return List.of();
        }
    }
}
