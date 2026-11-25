package com.zhu.access.application.chat.run;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.runtime.api.agent.AgentStreamEvent;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.SignalType;

/**
 * WEB 和 API 共用的步骤折叠。不启动 Spring。
 */
class StreamRunStepsTest {

    /** 结果文本进摘要，工具名拆开，最后补一步成功的推理。 */
    @Test
    void foldsToolsAndResult() {
        ConcurrentLinkedQueue<AgentStreamEvent> seen = new ConcurrentLinkedQueue<>();
        AtomicReference<String> output = new AtomicReference<>();
        StreamRunSteps.observe(seen, output, null);
        StreamRunSteps.observe(seen, output, new AgentStreamEvent("text_block_delta", "hi"));
        StreamRunSteps.observe(seen, output, new AgentStreamEvent("tool_call_start", "search, "));
        StreamRunSteps.observe(seen, output, new AgentStreamEvent("require_user_confirm", "write_file"));
        StreamRunSteps.observe(seen, output, new AgentStreamEvent("agent_result", "done"));

        assertThat(output.get()).isEqualTo("done");
        assertThat(StreamRunSteps.from(seen, RunLog.SUCCEEDED))
                .containsExactly(
                        new RunStep(RunStep.TOOL, "search", RunLog.SUCCEEDED),
                        new RunStep(RunStep.APPROVAL, "write_file", RunLog.SUCCEEDED),
                        new RunStep(RunStep.REASONING, null, RunLog.SUCCEEDED));
    }

    /** 取消把推理记成跳过。同步失败只有一步失败的推理。 */
    @Test
    void cancelledAndFailedReasoning() {
        assertThat(StreamRunSteps.reasoningOnly(RunLog.CANCELLED))
                .containsExactly(new RunStep(RunStep.REASONING, null, RunStep.SKIPPED));
        assertThat(StreamRunSteps.reasoningOnly(RunLog.FAILED))
                .containsExactly(new RunStep(RunStep.REASONING, null, RunLog.FAILED));
        assertThat(StreamRunSteps.statusOf(SignalType.ON_COMPLETE)).isEqualTo(RunLog.SUCCEEDED);
        assertThat(StreamRunSteps.statusOf(SignalType.CANCEL)).isEqualTo(RunLog.CANCELLED);
        assertThat(StreamRunSteps.statusOf(SignalType.ON_ERROR)).isEqualTo(RunLog.FAILED);
        String longName = "t".repeat(130);
        ConcurrentLinkedQueue<AgentStreamEvent> seen = new ConcurrentLinkedQueue<>();
        seen.add(new AgentStreamEvent("tool_call_start", longName));
        assertThat(StreamRunSteps.from(seen, RunLog.SUCCEEDED).get(0).toolId()).hasSize(128);
    }
}
