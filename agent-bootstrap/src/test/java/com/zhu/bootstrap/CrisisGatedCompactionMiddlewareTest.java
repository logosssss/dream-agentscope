package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.CrisisGatedCompactionMiddleware;
import com.zhu.runtime.infrastructure.agent.HarnessAgentRegistry;
import com.zhu.semantic.contract.safety.RiskLevel;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Compaction 落地 ②：CRITICAL 不进入 CompactionMiddleware；NORMAL/ELEVATED 会委托。
 * 工厂侧 disableCompaction + 产品闸；本测直接挂闸验证行为。
 */
class CrisisGatedCompactionMiddlewareTest {

    @TempDir
    Path workspace;

    @AfterEach
    void clearRegistry() {
        HarnessAgentRegistry.clear();
    }

    /** 危机档：跳过委托，不构造框架 CompactionMiddleware。 */
    @Test
    void critical_skipsCompactDelegation() throws Exception {
        CrisisGatedCompactionMiddleware gate = new CrisisGatedCompactionMiddleware();
        HarnessAgent agent = agent(gate);
        RuntimeContext critical = RuntimeContext.builder()
                .userId("u-critical")
                .sessionId("s-critical")
                .put(RiskLevel.class, RiskLevel.CRITICAL)
                .build();
        try {
            agent.call("我想自杀", critical).block(Duration.ofSeconds(15));
            assertThat(gate.criticalSkips()).isEqualTo(1);
            assertThat(gate.compactDelegations()).isZero();
            assertThat(agent.getCompactionHook())
                    .as("disableCompaction 后紧急 hook 仍为空")
                    .isNull();
        } finally {
            agent.close();
        }
    }

    /** 普通档：至少委托一次框架 CompactionMiddleware（未必触发摘要）。 */
    @Test
    void normal_delegatesCompaction() throws Exception {
        CrisisGatedCompactionMiddleware gate = new CrisisGatedCompactionMiddleware(
                CompactionConfig.builder().triggerMessages(3).keepMessages(1).build());
        HarnessAgent agent = agent(gate);
        RuntimeContext normal = RuntimeContext.builder()
                .userId("u-normal")
                .sessionId("s-normal")
                .put(RiskLevel.class, RiskLevel.NORMAL)
                .build();
        try {
            agent.call("今天去散步了", normal).block(Duration.ofSeconds(15));
            assertThat(gate.criticalSkips()).isZero();
            assertThat(gate.compactDelegations()).isEqualTo(1);
        } finally {
            agent.close();
        }
    }

    /** 升高档与普通一样允许委托。 */
    @Test
    void elevated_delegatesCompaction() throws Exception {
        CrisisGatedCompactionMiddleware gate = new CrisisGatedCompactionMiddleware();
        HarnessAgent agent = agent(gate);
        RuntimeContext elevated = RuntimeContext.builder()
                .userId("u-elevated")
                .sessionId("s-elevated")
                .put(RiskLevel.class, RiskLevel.ELEVATED)
                .build();
        try {
            agent.call("我有点难受", elevated).block(Duration.ofSeconds(15));
            assertThat(gate.criticalSkips()).isZero();
            assertThat(gate.compactDelegations()).isEqualTo(1);
        } finally {
            agent.close();
        }
    }

    private HarnessAgent agent(CrisisGatedCompactionMiddleware gate) {
        Model model = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("compact-gate")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "compact-gate-model";
            }
        };
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("compact-gate")
                .agentId("compact-gate")
                .model(model)
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString());
        builder.disableWebTools();
        builder.disableShellTool();
        builder.disableCompaction();
        builder.middleware(gate);
        HarnessAgent agent = builder.build();
        HarnessAgentRegistry.register(agent);
        return agent;
    }
}
