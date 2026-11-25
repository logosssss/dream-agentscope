package com.zhu.access.application.chat;

import com.zhu.access.application.chat.run.RunLog;
import com.zhu.access.application.chat.run.RunStep;
import com.zhu.access.application.chat.run.StreamRunSteps;
import com.zhu.common.context.RequestIdContext;
import com.zhu.common.util.Ids;
import com.zhu.runtime.api.agent.AgentResponse;
import com.zhu.runtime.api.agent.AgentStreamEvent;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import reactor.core.publisher.Flux;

/**
 * WEB 与 API、同步与流式共用的一轮收口。发送和落库内容仍由各自入口提供。
 */
public final class ChatTurn {

    /** 只提供静态方法，不让四个入口各写一份占位释放。 */
    private ChatTurn() {
    }

    /**
     * 请求号在发送前抄走。发送或落库抛错都记一笔失败，然后原样抛出。
     * 名额在失败记录之后释放。
     */
    public static AgentResponse sync(
            ConcurrentGate concurrent,
            ConcurrentGate.Slot slot,
            Runnable remember,
            Supplier<AgentResponse> call,
            Recorder recorder) {
        long started = System.nanoTime();
        String requestId = RequestIdContext.get();
        try {
            remember.run();
            AgentResponse response = call.get();
            recorder.finish(
                    RunLog.SUCCEEDED,
                    response.runId(),
                    response.text(),
                    null,
                    requestId,
                    started,
                    StreamRunSteps.reasoningOnly(RunLog.SUCCEEDED));
            return response;
        } catch (RuntimeException error) {
            recorder.finish(
                    RunLog.FAILED,
                    Ids.newId(),
                    "",
                    error.getClass().getSimpleName(),
                    requestId,
                    started,
                    StreamRunSteps.reasoningOnly(RunLog.FAILED));
            throw error;
        } finally {
            concurrent.close(slot);
        }
    }

    /**
     * 流式回调不在入口线程上，所以请求号在返回 Flux 之前抄走。
     * 准备阶段抛错立刻记失败并放名额；订上之后先落库再放名额。
     */
    public static Flux<AgentStreamEvent> stream(
            ConcurrentGate concurrent,
            ConcurrentGate.Slot slot,
            Runnable remember,
            Supplier<Flux<AgentStreamEvent>> call,
            Recorder recorder) {
        long started = System.nanoTime();
        String requestId = RequestIdContext.get();
        try {
            remember.run();
            String runId = Ids.newId();
            AtomicReference<String> output = new AtomicReference<>("");
            AtomicReference<String> errorCode = new AtomicReference<>();
            ConcurrentLinkedQueue<AgentStreamEvent> seen = new ConcurrentLinkedQueue<>();
            return call.get()
                    .doOnNext(event -> StreamRunSteps.observe(seen, output, event))
                    .doOnError(error -> errorCode.set(error.getClass().getSimpleName()))
                    .doFinally(signal -> {
                        try {
                            String status = StreamRunSteps.statusOf(signal);
                            String code = RunLog.FAILED.equals(status) ? errorCode.get() : null;
                            recorder.finish(
                                    status,
                                    runId,
                                    output.get(),
                                    code,
                                    requestId,
                                    started,
                                    StreamRunSteps.from(seen, status));
                        } finally {
                            concurrent.close(slot);
                        }
                    });
        } catch (RuntimeException error) {
            try {
                recorder.finish(
                        RunLog.FAILED,
                        Ids.newId(),
                        "",
                        error.getClass().getSimpleName(),
                        requestId,
                        started,
                        StreamRunSteps.reasoningOnly(RunLog.FAILED));
            } finally {
                concurrent.close(slot);
            }
            throw error;
        }
    }

    /** 四个入口各自决定摘要、触发方式和附件，这里只交结果。 */
    @FunctionalInterface
    public interface Recorder {
        void finish(
                String status,
                String runId,
                String output,
                String errorCode,
                String requestId,
                long startedNanos,
                List<RunStep> steps);
    }
}
