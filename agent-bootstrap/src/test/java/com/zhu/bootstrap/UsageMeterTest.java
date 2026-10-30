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
 * 用量只在回复带 token 时记录，明细不含提示词。
 */
class UsageMeterTest {

    /** 有用量时带上模型名和输入输出 token。 */
    @Test
    void recordsTokensWhenReplyHasUsage() {
        Msg reply = Msg.builder()
                .role(MsgRole.ASSISTANT)
                .textContent("你好，这是回复")
                .usage(new ChatUsage(3, 5, 0.2))
                .build();
        Captured captured = new Captured();

        ModelCallUsage.record(captured, "user-a", "dashscope:qwen-plus", reply, System.nanoTime());

        assertThat(captured.userId).isEqualTo("user-a");
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

        ModelCallUsage.record(captured, "user-a", "m", reply, System.nanoTime());

        assertThat(captured.called).isFalse();
    }

    /** 审计明细只有模型和数字。模型名里的换行会被换成空格。 */
    @Test
    void detailOmitsReplyText() {
        String[] detail = new String[1];
        UsageMeter meter = new AuditUsageMeter((action, actorId, text) -> detail[0] = text);

        meter.onCall("user-a", "dashscope:qwen-plus\nextra", 3, 5, 12);

        assertThat(detail[0]).isEqualTo("model=dashscope:qwen-plus extra inputTokens=3 outputTokens=5 latencyMillis=12");
        assertThat(detail[0]).doesNotContain("回复");
    }

    /** 审计写失败时不向外抛，调用方仍可返回回复。 */
    @Test
    void auditFailureDoesNotEscape() {
        UsageMeter meter = new AuditUsageMeter((action, actorId, detail) -> {
            throw new IllegalStateException("db down");
        });

        meter.onCall("user-a", "m", 1, 2, 3);
    }

    /** 测试里记下最后一次调用。 */
    private static final class Captured implements UsageMeter {

        private boolean called;

        private String userId;

        private String model;

        private int inputTokens;

        private int outputTokens;

        private long latencyMillis;

        @Override
        public void onCall(String userId, String model, int inputTokens, int outputTokens, long latencyMillis) {
            this.called = true;
            this.userId = userId;
            this.model = model;
            this.inputTokens = inputTokens;
            this.outputTokens = outputTokens;
            this.latencyMillis = latencyMillis;
        }
    }
}
