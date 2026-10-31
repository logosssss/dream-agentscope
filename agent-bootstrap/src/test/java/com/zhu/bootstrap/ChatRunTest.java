package com.zhu.bootstrap;

import com.zhu.access.api.dto.ChatRequest;
import com.zhu.access.application.ChatAppService;
import com.zhu.access.application.QuotaGuard;
import com.zhu.access.application.RunLog;
import com.zhu.access.application.RunStep;
import com.zhu.access.application.SessionIndex;
import com.zhu.access.application.SessionTurnGuard;
import com.zhu.access.config.AccessLimitProperties;
import com.zhu.access.domain.channel.AccessChannel;
import com.zhu.runtime.api.AgentExecutor;
import com.zhu.runtime.api.AgentResponse;
import com.zhu.runtime.api.AgentStreamEvent;
import java.time.Clock;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 运行记录只留短摘要和异常类型。写失败不改变回复。不连接 MySQL。
 */
class ChatRunTest {

    /** 成功时记下回复标识，输出超过 40 个字符被截断。 */
    @Test
    void successStoresRunIdAndShortSummary() {
        Captured captured = new Captured();
        AtomicReference<String> attached = new AtomicReference<>();
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any())).thenReturn(new AgentResponse("run-1", "y".repeat(50)));
        ChatAppService chats = chats(executor, captured, attached);

        AgentResponse response = chats.chat(request("x".repeat(50), AccessChannel.API));

        assertThat(response.runId()).isEqualTo("run-1");
        assertThat(captured.status).isEqualTo(RunLog.SUCCEEDED);
        assertThat(captured.runId).isEqualTo("run-1");
        assertThat(captured.trigger).isEqualTo("API");
        assertThat(captured.input).hasSize(40);
        assertThat(captured.output).hasSize(40);
        assertThat(captured.errorCode).isNull();
        assertThat(attached.get()).isEqualTo("run-1");
        assertThat(captured.steps).containsExactly(new RunStep(RunStep.REASONING, null, RunLog.SUCCEEDED));
    }

    /** 失败只留异常类型。异常原文里的输入不会进记录，异常仍向外抛。 */
    @Test
    void failureKeepsExceptionTypeOnly() {
        Captured captured = new Captured();
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any())).thenThrow(new IllegalStateException("用户原文不要入库"));
        ChatAppService chats = chats(executor, captured, new AtomicReference<>());

        assertThatThrownBy(() -> chats.chat(request("hi", null)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(captured.status).isEqualTo(RunLog.FAILED);
        assertThat(captured.errorCode).isEqualTo("IllegalStateException");
        assertThat(captured.output).isEmpty();
        assertThat(captured.steps).containsExactly(new RunStep(RunStep.REASONING, null, RunLog.FAILED));
    }

    /** 记录抛错时回复仍然返回。 */
    @Test
    void recordFailureDoesNotHideReply() {
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.execute(any())).thenReturn(new AgentResponse("run-2", "ok"));
        RunLog runs = (tenant, user, session, trigger, status, runId, input, output, error, steps, started) -> {
            throw new IllegalStateException("db down");
        };
        ChatAppService chats = service(executor, runs, (tenant, user, session, channel, text) -> {
        });

        assertThat(chats.chat(request("hi", null)).text()).isEqualTo("ok");
    }

    /** 流正常结束记成功，并摘出结果事件里的短文本。 */
    @Test
    void streamSuccessUsesResultText() {
        Captured captured = new Captured();
        AtomicReference<String> attached = new AtomicReference<>();
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.stream(any())).thenReturn(Flux.just(
                new AgentStreamEvent("text_block_delta", "片段"),
                new AgentStreamEvent("tool_call_start", "shell"),
                new AgentStreamEvent("require_user_confirm", "shell, http"),
                new AgentStreamEvent("agent_result", "z".repeat(50))));
        ChatAppService chats = chats(executor, captured, attached);

        chats.stream(request("hi", null)).blockLast();

        assertThat(captured.status).isEqualTo(RunLog.SUCCEEDED);
        assertThat(captured.output).hasSize(40);
        assertThat(captured.errorCode).isNull();
        assertThat(attached.get()).isEqualTo(captured.runId);
        assertThat(captured.steps).containsExactly(
                new RunStep(RunStep.TOOL, "shell", RunLog.SUCCEEDED),
                new RunStep(RunStep.APPROVAL, "shell", RunLog.SUCCEEDED),
                new RunStep(RunStep.APPROVAL, "http", RunLog.SUCCEEDED),
                new RunStep(RunStep.REASONING, null, RunLog.SUCCEEDED));
    }

    /** 流报错记失败，异常类型来自错误对象，不来自原文。 */
    @Test
    void streamErrorStoresType() {
        Captured captured = new Captured();
        AgentExecutor executor = mock(AgentExecutor.class);
        when(executor.stream(any())).thenReturn(Flux.error(new IllegalArgumentException("secret")));
        ChatAppService chats = chats(executor, captured, new AtomicReference<>());

        assertThatThrownBy(() -> chats.stream(request("hi", null)).blockLast())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(captured.status).isEqualTo(RunLog.FAILED);
        assertThat(captured.errorCode).isEqualTo("IllegalArgumentException");
        assertThat(captured.steps).containsExactly(new RunStep(RunStep.REASONING, null, RunLog.FAILED));
    }

    /** 拼一个不查库的对话服务。额度不限制。 */
    private static ChatAppService chats(AgentExecutor executor, Captured captured, AtomicReference<String> attached) {
        SessionIndex sessions = new SessionIndex() {
            /** 会话列表不在这次测试里。 */
            @Override
            public void record(String tenantId, String userId, String sessionId, String channel, String text) {
            }

            /** 只记住运行标识。 */
            @Override
            public void attachRun(String tenantId, String userId, String sessionId, String runId) {
                attached.set(runId);
            }
        };
        return service(executor, captured, sessions);
    }

    /** 执行器、记录器和会话索引都由测试传入。 */
    private static ChatAppService service(AgentExecutor executor, RunLog runs, SessionIndex sessions) {
        return new ChatAppService(
                executor,
                (key, variables, fallback) -> fallback,
                new TestSemanticProvider(),
                new QuotaGuard(new FakeAccessCache(), new AccessLimitProperties(), Clock.systemUTC()),
                new SessionTurnGuard(new FakeAccessCache(), 120),
                sessions,
                runs,
                tenantId -> {
                },
                (tenantId, userId, sessionId) -> {
                });
    }

    /** 最小请求。通道空则按页面。 */
    private static ChatRequest request(String text, AccessChannel channel) {
        return new ChatRequest(null, "t", "u", "s", null, text, channel);
    }

    /** 记下最后一次运行记录。 */
    private static final class Captured implements RunLog {

        private String status;

        private String runId;

        private String trigger;

        private String input;

        private String output;

        private String errorCode;

        private List<RunStep> steps = List.of();

        /** 保存调用参数。测试结束后再断言。 */
        @Override
        public void complete(
                String tenantId,
                String userId,
                String sessionId,
                String trigger,
                String status,
                String runId,
                String inputSummary,
                String outputSummary,
                String errorCode,
                List<RunStep> steps,
                long startedNanos) {
            this.trigger = trigger;
            this.status = status;
            this.runId = runId;
            this.input = inputSummary;
            this.output = outputSummary;
            this.errorCode = errorCode;
            this.steps = steps == null ? List.of() : List.copyOf(steps);
        }
    }
}
