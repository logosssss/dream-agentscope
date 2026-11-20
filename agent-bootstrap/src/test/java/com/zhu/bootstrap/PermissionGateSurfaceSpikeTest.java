package com.zhu.bootstrap;

import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.ConfirmResult;
import io.agentscope.core.event.RequireUserConfirmEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.message.ToolCallState;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.middleware.ActingInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import io.agentscope.harness.agent.HarnessAgent;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Permission Gate 落地 ①：对照 spike。
 * 钉清 Gate 与 onActing 先后、ASK→RequireUserConfirm、无确认再入抛错、
 * {@code Msg.METADATA_CONFIRM_RESULTS} 续跑面。不改工厂主路径，不连中间件外设。
 */
class PermissionGateSurfaceSpikeTest {

    private static final String TOOL = "echo_text";

    @TempDir
    Path workspace;

    /**
     * 无条件 ASK：工具不执行；发出 RequireUserConfirmEvent。
     * 事件内工具快照常仍是 PENDING；Gate 会把会话上下文标成 ASKING。
     */
    @Test
    void gateAsk_emitsConfirm_andDoesNotRunTool() {
        CountingEcho echo = new CountingEcho();
        List<AgentEvent> events = new ArrayList<>();
        String agentId = unique("ask");
        String sessionId = unique("s");
        try (HarnessAgent agent = agent(agentId, PermissionBehavior.ASK, echo, null)) {
            RuntimeContext ctx = context("ask-user", sessionId);
            agent.streamEvents("run ask", ctx)
                    .doOnNext(events::add)
                    .blockLast(Duration.ofSeconds(15));
            assertThat(echo.runs()).as("Gate ASK 不得执行工具").isZero();
            RequireUserConfirmEvent confirm = requireConfirm(events);
            assertThat(confirm.getToolCalls()).isNotEmpty();

            // 无确认再发普通用户消息 → 框架 HITL 暂停语义。
            AtomicReference<Throwable> error = new AtomicReference<>();
            agent.call("no confirm yet", ctx)
                    .onErrorResume(ex -> {
                        error.set(ex);
                        return Mono.empty();
                    })
                    .block(Duration.ofSeconds(15));
            assertThat(error.get())
                    .as("ASK 后无 agentscope_confirm_results 再入应抛 IllegalStateException")
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining(Msg.METADATA_CONFIRM_RESULTS);
        }
    }

    /**
     * 无条件 DENY：工具不执行；无 RequireUserConfirmEvent。
     */
    @Test
    void gateDeny_doesNotRunTool_andNoConfirm() {
        CountingEcho echo = new CountingEcho();
        List<AgentEvent> events = new ArrayList<>();
        try (HarnessAgent agent = agent(unique("deny"), PermissionBehavior.DENY, echo, null)) {
            agent.streamEvents("run deny", context("deny-user", unique("s")))
                    .doOnNext(events::add)
                    .blockLast(Duration.ofSeconds(15));
            assertThat(echo.runs()).as("Gate DENY 不得执行工具").isZero();
            assertThat(events.stream().anyMatch(RequireUserConfirmEvent.class::isInstance))
                    .as("DENY 不应 RequireUserConfirm")
                    .isFalse();
        }
    }

    /**
     * 无条件 ALLOW：工具执行一次。
     */
    @Test
    void gateAllow_runsTool() {
        CountingEcho echo = new CountingEcho();
        try (HarnessAgent agent = agent(unique("allow"), PermissionBehavior.ALLOW, echo, null)) {
            agent.call("run allow", context("allow-user", unique("s")))
                    .block(Duration.ofSeconds(15));
            assertThat(echo.runs()).as("Gate ALLOW 应执行工具").isEqualTo(1);
        }
    }

    /**
     * onActing 包在 Gate（actingStream.evaluatePermissions）之外：进入中间件时还不是 ASKING。
     */
    @Test
    void onActing_runsBeforeGate_marksAsking() {
        CountingEcho echo = new CountingEcho();
        AtomicReference<ToolCallState> beforeNext = new AtomicReference<>();
        List<AgentEvent> events = new ArrayList<>();
        MiddlewareBase probe = new MiddlewareBase() {
            @Override
            public Flux<AgentEvent> onActing(
                    io.agentscope.core.agent.Agent agent,
                    RuntimeContext context,
                    ActingInput input,
                    Function<ActingInput, Flux<AgentEvent>> next) {
                beforeNext.set(input.toolCalls().get(0).getState());
                return next.apply(input);
            }
        };
        try (HarnessAgent agent = agent(unique("order"), PermissionBehavior.ASK, echo, probe)) {
            agent.streamEvents("order", context("order-user", unique("s")))
                    .doOnNext(events::add)
                    .onErrorResume(ex -> Mono.empty())
                    .blockLast(Duration.ofSeconds(15));
            assertThat(beforeNext.get())
                    .as("Middleware onActing 先于 Gate，此时尚未标 ASKING")
                    .isNotEqualTo(ToolCallState.ASKING);
            assertThat(requireConfirm(events).getToolCalls()).isNotEmpty();
            assertThat(echo.runs()).isZero();
        }
    }

    /**
     * 续跑面：先 ASK 暂停；再发带 METADATA_CONFIRM_RESULTS 的用户消息后工具执行。
     */
    @Test
    void confirmResults_resumeRunsPreviouslyAskingTool() {
        CountingEcho echo = new CountingEcho();
        List<AgentEvent> events = new ArrayList<>();
        String agentId = unique("resume");
        String sessionId = unique("s");
        try (HarnessAgent agent = agent(agentId, PermissionBehavior.ASK, echo, null)) {
            RuntimeContext ctx = context("resume-user", sessionId);
            agent.streamEvents("need confirm", ctx)
                    .doOnNext(events::add)
                    .onErrorResume(ex -> Mono.empty())
                    .blockLast(Duration.ofSeconds(15));
            assertThat(echo.runs()).isZero();
            ToolUseBlock asking = requireConfirm(events).getToolCalls().get(0);

            ConfirmResult confirmed = new ConfirmResult(true, asking);
            Msg confirm = Msg.builder()
                    .role(MsgRole.USER)
                    .textContent("approved")
                    .metadata(Map.of(Msg.METADATA_CONFIRM_RESULTS, List.of(confirmed)))
                    .build();
            agent.call(List.of(confirm), ctx).block(Duration.ofSeconds(15));
            assertThat(echo.runs())
                    .as("带 agentscope_confirm_results 续跑后应执行工具")
                    .isEqualTo(1);
        }
    }

    /** 按行为注入无条件规则；agentId 必须唯一，避免 ASKING 状态串会话。 */
    private HarnessAgent agent(
            String agentId,
            PermissionBehavior behavior,
            CountingEcho echo,
            MiddlewareBase probe) {
        PermissionRule rule = new PermissionRule(TOOL, null, behavior, "gate-spike");
        PermissionContextState.Builder perms = PermissionContextState.builder()
                .mode(PermissionMode.BYPASS);
        switch (behavior) {
            case ALLOW -> perms.addAllowRule(TOOL, rule);
            case ASK -> perms.addAskRule(TOOL, rule);
            case DENY -> perms.addDenyRule(TOOL, rule);
            default -> throw new IllegalStateException("spike only ALLOW/ASK/DENY");
        }
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name(agentId)
                .agentId(agentId)
                .model(toolCallingModel())
                .sysPrompt("spike")
                .maxIters(2)
                .workspace(workspace.toString())
                .permissionContext(perms.build());
        builder.disableWebTools();
        builder.disableShellTool();
        builder.disableCompaction();
        builder.disableToolResultEviction();
        builder.disableDynamicSkills();
        builder.disableMemoryTools();
        if (probe != null) {
            builder.middleware(probe);
        }
        // build 会装默认 Toolkit；业务工具必须 build 后再挂，否则 echo_text 未注册会变 ERROR。
        HarnessAgent agent = builder.build();
        agent.getToolkit().registerTool(echo);
        return agent;
    }

    private static RuntimeContext context(String userId, String sessionId) {
        return RuntimeContext.builder()
                .userId(userId)
                .sessionId(sessionId)
                .build();
    }

    private static String unique(String prefix) {
        return prefix + "-" + UUID.randomUUID();
    }

    private static RequireUserConfirmEvent requireConfirm(List<AgentEvent> events) {
        return events.stream()
                .filter(RequireUserConfirmEvent.class::isInstance)
                .map(RequireUserConfirmEvent.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("缺少 RequireUserConfirmEvent"));
    }

    /** 第一次模型调用返回 echo_text；其后返回短文本结束。 */
    private static Model toolCallingModel() {
        AtomicInteger turns = new AtomicInteger();
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                if (turns.getAndIncrement() == 0) {
                    // content JSON 与 input 一并给出：仅 input 时参数校验会报「未找到所需属性 text」。
                    ToolUseBlock use = ToolUseBlock.builder()
                            .id("call-" + UUID.randomUUID())
                            .name(TOOL)
                            .input(Map.of("text", "ping"))
                            .content("{\"text\":\"ping\"}")
                            .build();
                    return Flux.just(ChatResponse.builder()
                            .id("gate-tool")
                            .content(List.of(use))
                            .finishReason("tool_calls")
                            .build());
                }
                return Flux.just(ChatResponse.builder()
                        .id("gate-text")
                        .content(List.of(TextBlock.builder().text("done").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "gate-spike-model";
            }
        };
    }

    /**
     * 带调用计数的回声工具。须 public：Toolkit 反射调用私有嵌套类会 ERROR。
     */
    public static final class CountingEcho {

        private final AtomicInteger runs = new AtomicInteger();

        /** 测试观测用调用次数。 */
        int runs() {
            return runs.get();
        }

        @Tool(name = TOOL, description = "gate spike echo")
        public String echo(@ToolParam(name = "text", description = "原文") String text) {
            runs.incrementAndGet();
            return text == null ? "" : text;
        }
    }
}
