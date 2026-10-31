package com.zhu.bootstrap;

import com.zhu.runtime.api.UsageMeter;
import com.zhu.runtime.application.AuditUsageMeter;
import com.zhu.runtime.application.ModelCallUsage;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.model.ChatUsage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 用量只在回复带 token 时记录，明细不含提示词。用量表失败不改变回复。
 */
class UsageMeterTest {

    /** 有用量时带上身份、模型名和输入输出 token。 */
    @Test
    void recordsTokensWhenReplyHasUsage() {
        Msg reply = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("你好，这是回复")
                .usage(new ChatUsage(3, 5, 0.2))
                .build();
        Captured captured = new Captured();

        ModelCallUsage.record(captured, "tenant-a", "user-a", "session-a", "run-1", "dashscope:qwen-plus", reply, System.nanoTime());

        assertThat(captured.called).isTrue();
        assertThat(captured.tenantId).isEqualTo("tenant-a");
        assertThat(captured.userId).isEqualTo("user-a");
        assertThat(captured.sessionId).isEqualTo("session-a");
        assertThat(captured.runId).isEqualTo("run-1");
        assertThat(captured.model).isEqualTo("dashscope:qwen-plus");
        assertThat(captured.inputTokens).isEqualTo(3);
        assertThat(captured.outputTokens).isEqualTo(5);
        assertThat(captured.latencyMillis).isGreaterThanOrEqualTo(0);
    }

    /** 框架没带回用量时不记，避免审计里出现全 0。 */
    @Test
    void skipsWhenUsageMissing() {
        Msg reply = Msg.builder().role(MsgRole.ASSISTANT).textContent("hi").build();
        Captured captured = new Captured();

        ModelCallUsage.record(captured, "t", "user-a", "s", "run-1", "m", reply, System.nanoTime());

        assertThat(captured.called).isFalse();
    }

    /** 审计明细只有模型和数字。模型名里的换行会被换成空格。用量表同时收到 token。 */
    @Test
    void detailOmitsReplyText() {
        String[] detail = new String[1];
        long[] tokens = new long[2];
        UsageMeter meter = new AuditUsageMeter((action, actorId, text) -> detail[0] = text, (tenant, user, session, run, model, input, output) -> {
            tokens[0] = input;
            tokens[1] = output;
        });

        meter.onCall("t", "user-a", "s", "run-1", "dashscope:qwen-plus\nextra", 3, 5, 12);

        assertThat(detail[0]).isEqualTo("model=dashscope:qwen-plus extra inputTokens=3 outputTokens=5 latencyMillis=12");
        assertThat(detail[0]).doesNotContain("回复");
        assertThat(tokens).containsExactly(3L, 5L);
    }

    /** 审计写失败时不向外抛，用量表仍然会记。 */
    @Test
    void auditFailureDoesNotEscape() {
        boolean[] wrote = new boolean[1];
        UsageMeter meter = new AuditUsageMeter((action, actorId, detail) -> {
            throw new IllegalStateException("db down");
        }, (tenant, user, session, run, model, input, output) -> wrote[0] = true);

        meter.onCall("t", "user-a", "s", "run-1", "m", 1, 2, 3);

        assertThat(wrote[0]).isTrue();
    }

    /** 用量表写失败时不向外抛，审计仍然会记。 */
    @Test
    void tokenTableFailureDoesNotEscape() {
        String[] action = new String[1];
        UsageMeter meter = new AuditUsageMeter((name, actorId, detail) -> action[0] = name, (tenant, user, session, run, model, input, output) -> {
            throw new IllegalStateException("db down");
        });

        meter.onCall("t", "user-a", "s", "run-1", "m", 1, 2, 3);

        assertThat(action[0]).isEqualTo("model.usage");
    }

    /** 测试里记下最后一次调用。 */
    private static final class Captured implements UsageMeter {

        private boolean called;

        private String tenantId;

        private String userId;

        private String sessionId;

        private String runId;

        private String model;

        private int inputTokens;

        private int outputTokens;

        private long latencyMillis;

        /** 保存调用参数。 */
        @Override
        public void onCall(
                String tenantId,
                String userId,
                String sessionId,
                String runId,
                String model,
                int inputTokens,
                int outputTokens,
                long latencyMillis) {
            this.called = true;
            this.tenantId = tenantId;
            this.userId = userId;
            this.sessionId = sessionId;
            this.runId = runId;
            this.model = model;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.latencyMillis = latencyMillis;
        }
    }
}
