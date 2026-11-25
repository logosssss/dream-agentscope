package com.zhu.access.application.chat.run;

import com.zhu.runtime.api.agent.AgentStreamEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import reactor.core.publisher.SignalType;

/**
 * 把流式事件收成运行步骤。WEB 和 API 对同一事件记同一份步骤。
 */
public final class StreamRunSteps {

    /** 只提供静态方法，不给接入层再 new 一份规则。 */
    private StreamRunSteps() {
    }

    /**
     * 增量文本不留下。工具开始和待确认才进入步骤。结果文本只留给运行摘要。
     */
    public static void observe(
            ConcurrentLinkedQueue<AgentStreamEvent> seen,
            AtomicReference<String> output,
            AgentStreamEvent event) {
        if (event == null || event.type() == null) {
            return;
        }
        if ("agent_result".equals(event.type())) {
            output.set(event.text());
            return;
        }
        if ("tool_call_start".equals(event.type()) || "require_user_confirm".equals(event.type())) {
            seen.add(event);
        }
    }

    /**
     * 工具和待确认按看到的顺序编号。最后补一步推理。
     * 工具步骤只表示调用已经开始，不表示工具成功。
     */
    public static List<RunStep> from(ConcurrentLinkedQueue<AgentStreamEvent> seen, String runStatus) {
        List<RunStep> steps = new ArrayList<>();
        for (AgentStreamEvent event : seen) {
            if ("tool_call_start".equals(event.type())) {
                addNamed(steps, RunStep.TOOL, event.text());
            } else if ("require_user_confirm".equals(event.type())) {
                addNamed(steps, RunStep.APPROVAL, event.text());
            }
        }
        steps.add(reasoning(runStatus));
        return List.copyOf(steps);
    }

    /** 同步一轮没有工具事件，只记一步推理。 */
    public static List<RunStep> reasoningOnly(String runStatus) {
        return List.of(reasoning(runStatus));
    }

    /** 取消单独记。其它非正常结束都算失败。 */
    public static String statusOf(SignalType signal) {
        if (signal == SignalType.ON_COMPLETE) {
            return RunLog.SUCCEEDED;
        }
        if (signal == SignalType.CANCEL) {
            return RunLog.CANCELLED;
        }
        return RunLog.FAILED;
    }

    /** 逗号分开的多个工具名各记一步。空白名字跳过，超过 128 字截断。 */
    private static void addNamed(List<RunStep> steps, String type, String names) {
        if (names == null || names.isBlank()) {
            return;
        }
        for (String name : names.split(",")) {
            String toolId = name.trim();
            if (toolId.isEmpty()) {
                continue;
            }
            if (toolId.length() > 128) {
                toolId = toolId.substring(0, 128);
            }
            steps.add(new RunStep(type, toolId, RunLog.SUCCEEDED));
        }
    }

    /** 取消没有推理结果，记成跳过。失败和成功沿用运行状态。 */
    private static RunStep reasoning(String runStatus) {
        String status = RunLog.CANCELLED.equals(runStatus) ? RunStep.SKIPPED : runStatus;
        if (!RunLog.FAILED.equals(status) && !RunStep.SKIPPED.equals(status)) {
            status = RunLog.SUCCEEDED;
        }
        return new RunStep(RunStep.REASONING, null, status);
    }
}
