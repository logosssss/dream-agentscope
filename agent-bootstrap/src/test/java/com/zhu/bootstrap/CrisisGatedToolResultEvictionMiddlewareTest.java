package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.CrisisGatedToolResultEvictionMiddleware;
import com.zhu.runtime.infrastructure.agent.HarnessAgentRegistry;
import com.zhu.semantic.contract.safety.RiskLevel;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.middleware.ToolResultEvictionMiddleware;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tool Result Eviction 落地 ②：CRITICAL 不进入框架 eviction；NORMAL/ELEVATED 会委托。
 */
class CrisisGatedToolResultEvictionMiddlewareTest {

    @TempDir
    Path workspace;

    @AfterEach
    void clearRegistry() {
        HarnessAgentRegistry.clear();
    }

    /** 危机档：跳过委托；链上亦无框架默认 eviction。 */
    @Test
    void critical_skipsEvictionDelegation() throws Exception {
        CrisisGatedToolResultEvictionMiddleware gate = new CrisisGatedToolResultEvictionMiddleware();
        HarnessAgent agent = agent(gate);
        RuntimeContext critical = RuntimeContext.builder()
                .userId("u-critical")
                .sessionId("s-critical")
                .put(RiskLevel.class, RiskLevel.CRITICAL)
                .build();
        try {
            agent.call("我想自杀", critical).block(Duration.ofSeconds(15));
            assertThat(gate.criticalSkips()).isEqualTo(1);
            assertThat(gate.evictionDelegations()).isZero();
            assertThat(hasFrameworkEviction(agent))
                    .as("disableToolResultEviction 后框架默认 eviction 不在链上")
                    .isFalse();
        } finally {
            agent.close();
        }
    }

    /** 普通档：至少委托一次框架 ToolResultEvictionMiddleware。 */
    @Test
    void normal_delegatesEviction() throws Exception {
        CrisisGatedToolResultEvictionMiddleware gate = new CrisisGatedToolResultEvictionMiddleware();
        HarnessAgent agent = agent(gate);
        RuntimeContext normal = RuntimeContext.builder()
                .userId("u-normal")
                .sessionId("s-normal")
                .put(RiskLevel.class, RiskLevel.NORMAL)
                .build();
        try {
            agent.call("今天去散步了", normal).block(Duration.ofSeconds(15));
            assertThat(gate.criticalSkips()).isZero();
            assertThat(gate.evictionDelegations()).isEqualTo(1);
        } finally {
            agent.close();
        }
    }

    /** 升高档与普通一样允许委托。 */
    @Test
    void elevated_delegatesEviction() throws Exception {
        CrisisGatedToolResultEvictionMiddleware gate = new CrisisGatedToolResultEvictionMiddleware();
        HarnessAgent agent = agent(gate);
        RuntimeContext elevated = RuntimeContext.builder()
                .userId("u-elevated")
                .sessionId("s-elevated")
                .put(RiskLevel.class, RiskLevel.ELEVATED)
                .build();
        try {
            agent.call("我有点难受", elevated).block(Duration.ofSeconds(15));
            assertThat(gate.criticalSkips()).isZero();
            assertThat(gate.evictionDelegations()).isEqualTo(1);
        } finally {
            agent.close();
        }
    }

    private HarnessAgent agent(CrisisGatedToolResultEvictionMiddleware gate) {
        Model model = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("evict-gate")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "evict-gate-model";
            }
        };
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("evict-gate")
                .agentId("evict-gate")
                .model(model)
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString());
        builder.disableWebTools();
        builder.disableShellTool();
        builder.disableCompaction();
        builder.disableToolResultEviction();
        builder.middleware(gate);
        HarnessAgent agent = builder.build();
        HarnessAgentRegistry.register(agent);
        return agent;
    }

    private static boolean hasFrameworkEviction(HarnessAgent agent) {
        List<MiddlewareBase> middlewares = agent.getDelegate().getMiddlewares();
        if (middlewares == null) {
            return false;
        }
        for (MiddlewareBase middleware : middlewares) {
            if (middleware instanceof ToolResultEvictionMiddleware) {
                return true;
            }
        }
        return false;
    }
}
