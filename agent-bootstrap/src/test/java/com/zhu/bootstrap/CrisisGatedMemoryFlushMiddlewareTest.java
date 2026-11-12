package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.CrisisGatedMemoryFlushMiddleware;
import com.zhu.semantic.contract.safety.RiskLevel;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import com.zhu.runtime.infrastructure.agent.HarnessAgentRegistry;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.MemoryBackgroundTasks;
import io.agentscope.harness.agent.memory.MemoryConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory 迁入 ②：CRITICAL 不入队 flush；NORMAL 会委托框架 MemoryFlush。
 * 不依赖「AgentState 非空才会真正调模型」——那是框架 doFlush 内部细节。
 */
class CrisisGatedMemoryFlushMiddlewareTest {

    @TempDir
    Path workspace;

    @AfterEach
    void clearRegistry() {
        HarnessAgentRegistry.clear();
    }

    /**
     * 危机档：跳过委托，不构造框架 flush 调度。
     */
    @Test
    void critical_skipsFlushDelegation() throws Exception {
        CrisisGatedMemoryFlushMiddleware gate =
                new CrisisGatedMemoryFlushMiddleware(MemoryConfig.FlushTrigger.always());
        HarnessAgent agent = agent(gate);
        RuntimeContext critical = RuntimeContext.builder()
                .userId("u-critical")
                .sessionId("s-critical")
                .put(RiskLevel.class, RiskLevel.CRITICAL)
                .build();
        try {
            agent.call("我想自杀", critical).block(Duration.ofSeconds(15));
            assertThat(MemoryBackgroundTasks.awaitQuiescence(5, TimeUnit.SECONDS)).isTrue();
            assertThat(gate.criticalSkips()).isEqualTo(1);
            assertThat(gate.flushDelegations()).isZero();
        } finally {
            agent.close();
        }
    }

    /**
     * 普通档：至少委托一次框架 flush/maintenance。
     */
    @Test
    void normal_delegatesFlush() throws Exception {
        CrisisGatedMemoryFlushMiddleware gate =
                new CrisisGatedMemoryFlushMiddleware(MemoryConfig.FlushTrigger.always());
        HarnessAgent agent = agent(gate);
        RuntimeContext normal = RuntimeContext.builder()
                .userId("u-normal")
                .sessionId("s-normal")
                .put(RiskLevel.class, RiskLevel.NORMAL)
                .build();
        try {
            agent.call("今天去散步了", normal).block(Duration.ofSeconds(15));
            assertThat(MemoryBackgroundTasks.awaitQuiescence(5, TimeUnit.SECONDS)).isTrue();
            assertThat(gate.criticalSkips()).isZero();
            assertThat(gate.flushDelegations()).isEqualTo(1);
        } finally {
            agent.close();
        }
    }

    /** 升高档与普通一样允许委托。 */
    @Test
    void elevated_delegatesFlush() throws Exception {
        CrisisGatedMemoryFlushMiddleware gate =
                new CrisisGatedMemoryFlushMiddleware(MemoryConfig.FlushTrigger.always());
        HarnessAgent agent = agent(gate);
        RuntimeContext elevated = RuntimeContext.builder()
                .userId("u-elevated")
                .sessionId("s-elevated")
                .put(RiskLevel.class, RiskLevel.ELEVATED)
                .build();
        try {
            agent.call("我有点难受", elevated).block(Duration.ofSeconds(15));
            assertThat(MemoryBackgroundTasks.awaitQuiescence(5, TimeUnit.SECONDS)).isTrue();
            assertThat(gate.flushDelegations()).isEqualTo(1);
            assertThat(gate.criticalSkips()).isZero();
        } finally {
            agent.close();
        }
    }

    /** 断言辅助：与中间件静态判定一致。 */
    @Test
    void criticalHelper_readsRiskLevel() {
        assertThat(CrisisGatedMemoryFlushMiddleware.critical(null)).isFalse();
        assertThat(CrisisGatedMemoryFlushMiddleware.critical(RuntimeContext.builder().build())).isFalse();
        assertThat(CrisisGatedMemoryFlushMiddleware.critical(
                RuntimeContext.builder().put(RiskLevel.class, RiskLevel.CRITICAL).build()))
                .isTrue();
        assertThat(CrisisGatedMemoryFlushMiddleware.critical(
                RuntimeContext.builder().put(RiskLevel.class, RiskLevel.ELEVATED).build()))
                .isFalse();
    }

    private HarnessAgent agent(CrisisGatedMemoryFlushMiddleware gate) {
        Model model = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("crisis-gate")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "crisis-gate-model";
            }
        };
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("crisis-gate")
                .agentId("crisis-gate")
                .model(model)
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .memory(MemoryConfig.builder()
                        .flushTrigger(MemoryConfig.FlushTrigger.always())
                        .build());
        builder.disableWebTools();
        builder.disableShellTool();
        builder.disableMemoryHooks();
        builder.middleware(gate);
        HarnessAgent agent = builder.build();
        // 与 Gateway 一致：Middleware 入参常是 ReAct 委托，需登记 Harness 才能取 WorkspaceManager。
        HarnessAgentRegistry.register(agent);
        return agent;
    }
}
