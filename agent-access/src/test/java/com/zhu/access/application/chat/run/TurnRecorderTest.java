package com.zhu.access.application.chat.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.access.application.chat.message.ChatMessageStore;
import com.zhu.access.application.chat.session.SessionIndex;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import org.junit.jupiter.api.Test;

/**
 * 结束落库的失败约定。不启动 Spring。
 */
class TurnRecorderTest {

    /** 成功时写运行、挂会话、写气泡，并把 user 序号交给附加动作。空运行号会补一个。 */
    @Test
    void successWritesRunThenMessage() {
        FakeRuns runs = new FakeRuns();
        FakeSessions sessions = new FakeSessions();
        FakeMessages messages = new FakeMessages(7);
        AtomicLong linked = new AtomicLong(-1);
        TurnRecorder.complete(
                Logger.getLogger("test"),
                runs,
                sessions,
                messages,
                "t",
                "u",
                "s",
                "USER",
                RunLog.SUCCEEDED,
                "  ",
                "req",
                "hello\nworld",
                "ok",
                null,
                List.of(),
                1L,
                linked::set);

        assertThat(runs.runId).isNotBlank();
        assertThat(runs.inputSummary).isEqualTo("hello world");
        assertThat(sessions.runId).isEqualTo(runs.runId);
        assertThat(messages.userText).isEqualTo("hello\nworld");
        assertThat(linked.get()).isEqualTo(7);
    }

    /** 运行记录失败仍写气泡。失败状态不写气泡。气泡写入失败不再做附加动作。 */
    @Test
    void failureDoesNotChangeTheReply() {
        FakeRuns runs = new FakeRuns();
        runs.fail = true;
        FakeMessages messages = new FakeMessages(1);
        AtomicLong linked = new AtomicLong(-1);
        TurnRecorder.complete(
                Logger.getLogger("test"),
                runs,
                new FakeSessions(),
                messages,
                "t",
                "u",
                "s",
                "API",
                RunLog.SUCCEEDED,
                "run-1",
                null,
                null,
                null,
                "Boom",
                List.of(),
                2L,
                linked::set);
        assertThat(messages.userText).isEmpty();
        assertThat(linked.get()).isEqualTo(1);

        FakeMessages skipped = new FakeMessages(1);
        TurnRecorder.complete(
                Logger.getLogger("test"),
                new FakeRuns(),
                new FakeSessions(),
                skipped,
                "t",
                "u",
                "s",
                "API",
                RunLog.FAILED,
                "run-2",
                null,
                "q",
                "",
                "Boom",
                List.of(),
                3L,
                null);
        assertThat(skipped.userText).isNull();

        FakeMessages broken = new FakeMessages(1);
        broken.fail = true;
        AtomicLong after = new AtomicLong(-1);
        TurnRecorder.complete(
                Logger.getLogger("test"),
                new FakeRuns(),
                new FakeSessions(),
                broken,
                "t",
                "u",
                "s",
                "USER",
                RunLog.SUCCEEDED,
                "run-3",
                null,
                "q",
                "a",
                null,
                List.of(),
                4L,
                after::set);
        assertThat(after.get()).isEqualTo(-1);
    }

    private static final class FakeRuns implements RunLog {
        private boolean fail;
        private String runId;
        private String inputSummary;

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
            if (fail) {
                throw new IllegalStateException("db");
            }
            this.runId = runId;
            this.inputSummary = inputSummary;
        }
    }

    private static final class FakeSessions implements SessionIndex {
        private String runId;

        @Override
        public void record(String tenantId, String userId, String sessionId, String channel, String text) {
        }

        @Override
        public void attachRun(String tenantId, String userId, String sessionId, String runId) {
            this.runId = runId;
        }
    }

    private static final class FakeMessages implements ChatMessageStore {
        private final long seq;
        private String userText;
        private boolean fail;

        private FakeMessages(long seq) {
            this.seq = seq;
        }

        @Override
        public long appendTurn(
                String tenantId,
                String userId,
                String sessionId,
                String runId,
                String userText,
                String assistantText) {
            if (fail) {
                throw new IllegalStateException("db");
            }
            this.userText = userText;
            return seq;
        }

        @Override
        public java.util.List<com.zhu.access.api.dto.chat.ChatMessageItem> list(
                String tenantId, String userId, String sessionId, int limit) {
            return List.of();
        }
    }
}
