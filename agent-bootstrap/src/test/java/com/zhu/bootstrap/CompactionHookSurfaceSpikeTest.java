package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.CrisisGatedCompactionMiddleware;
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
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.middleware.CompactionMiddleware;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CompactionHook 落地 ①：对照 spike。
 * 钉清 compaction(config) 装 hook、是否同时挂链、与 CrisisGated 叠放时的双压风险、hook final。
 * 不改工厂主路径，不连 Redis/MySQL/Milvus。
 * 见 docs/compaction-hook-落地.md。
 */
class CompactionHookSurfaceSpikeTest {

    @TempDir
    Path workspace;

    @AfterEach
    void clearRegistry() {
        HarnessAgentRegistry.clear();
    }

    /** companion 拟装配：compaction(默认 config) 后紧急 hook 非空。 */
    @Test
    void compactionConfig_installsEmergencyHook() throws Exception {
        try (HarnessAgent agent = build(CompactionConfig.builder().build(), null)) {
            assertThat(agent.getCompactionHook())
                    .as("compaction(config) 安装紧急溢出 hook")
                    .isNotNull()
                    .isInstanceOf(CompactionMiddleware.class);
        }
    }

    /**
     * 关键发现：compaction(config) 会把同一份 CompactionMiddleware 挂进 middleware 链。
     * 公开 API 无法「只装 hook、不挂链」。
     */
    @Test
    void compactionConfig_alsoAddsCompactionMiddlewareToChain_sameInstanceAsHook() throws Exception {
        try (HarnessAgent agent = build(CompactionConfig.builder().build(), null)) {
            CompactionMiddleware hook = agent.getCompactionHook();
            List<CompactionMiddleware> onChain = instancesOnChain(agent, CompactionMiddleware.class);
            assertThat(onChain)
                    .as("compaction(config) 会在链上挂未闸 CompactionMiddleware")
                    .hasSize(1);
            assertThat(onChain.get(0))
                    .as("链上实例与 getCompactionHook() 为同一对象")
                    .isSameAs(hook);
        }
    }

    /**
     * 叠放 CrisisGated 时：链上同时有未闸 CompactionMiddleware 与 CrisisGated。
     * 未闸那份先于/并存于危机闸 → CRITICAL 常规路径可能被打穿（双压 / 闸失效风险）。
     */
    @Test
    void compactionPlusCrisisGate_ungatedCompactionRemainsOnChain() throws Exception {
        CrisisGatedCompactionMiddleware gate = new CrisisGatedCompactionMiddleware(
                CompactionConfig.builder().triggerMessages(3).keepMessages(1).build());
        try (HarnessAgent agent = build(CompactionConfig.builder().build(), gate)) {
            HarnessAgentRegistry.register(agent);
            assertThat(agent.getCompactionHook()).isNotNull();
            assertThat(instancesOnChain(agent, CompactionMiddleware.class)).hasSize(1);
            assertThat(instancesOnChain(agent, CrisisGatedCompactionMiddleware.class)).hasSize(1);

            List<String> order = middlewareTypeNames(agent);
            int ungated = order.indexOf(CompactionMiddleware.class.getName());
            int gated = order.indexOf(CrisisGatedCompactionMiddleware.class.getName());
            assertThat(ungated).isGreaterThanOrEqualTo(0);
            assertThat(gated).isGreaterThanOrEqualTo(0);
            // 记录相对顺序，供落地文引用（框架把 compaction 挂在用户 middleware 之后或之前）。
            assertThat(order)
                    .as("链上类型序（spike 记录）：" + order)
                    .contains(
                            CompactionMiddleware.class.getName(),
                            CrisisGatedCompactionMiddleware.class.getName());
        }
    }

    /**
     * 叠放后 CRITICAL 调用：CrisisGated 仍会计 criticalSkip，
     * 但不能证明未闸 CompactionMiddleware 未先执行（本 spike 只钉闸自身计数）。
     */
    @Test
    void compactionPlusCrisisGate_criticalGateStillCountsSkip() throws Exception {
        CrisisGatedCompactionMiddleware gate = new CrisisGatedCompactionMiddleware();
        try (HarnessAgent agent = build(CompactionConfig.builder().build(), gate)) {
            HarnessAgentRegistry.register(agent);
            RuntimeContext critical = RuntimeContext.builder()
                    .userId("u-hook-critical")
                    .sessionId("s-hook-critical")
                    .put(RiskLevel.class, RiskLevel.CRITICAL)
                    .build();
            agent.call("我想自杀", critical).block(Duration.ofSeconds(15));
            assertThat(gate.criticalSkips()).isEqualTo(1);
            assertThat(agent.getCompactionHook()).isNotNull();
        }
    }

    /**
     * 结构约束：compactionHook 字段 final → 不能按轮次按 CRITICAL 摘装。
     * 紧急路径不读 RiskLevel：对照 HarnessAgent.forceCompactAndRetry（见落地文）。
     */
    @Test
    void compactionHookField_isFinal() throws Exception {
        Field hook = HarnessAgent.class.getDeclaredField("compactionHook");
        assertThat(Modifier.isFinal(hook.getModifiers()))
                .as("hook 构建期 final，无法按 CRITICAL 动态摘装")
                .isTrue();
    }

    private HarnessAgent build(CompactionConfig config, MiddlewareBase gate) {
        Model model = new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("hook-spike")
                        .content(List.of(TextBlock.builder().text("ok").build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "hook-spike-model";
            }
        };
        HarnessAgent.Builder builder = HarnessAgent.builder()
                .name("hook-spike")
                .agentId("hook-spike")
                .model(model)
                .sysPrompt("test")
                .maxIters(1)
                .workspace(workspace.toString())
                .disableWebTools()
                .disableShellTool()
                .compaction(config);
        if (gate != null) {
            builder.middleware(gate);
        }
        return builder.build();
    }

    private static <T> List<T> instancesOnChain(HarnessAgent agent, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (MiddlewareBase middleware : agent.getDelegate().getMiddlewares()) {
            if (type.isInstance(middleware)) {
                found.add(type.cast(middleware));
            }
        }
        return found;
    }

    private static List<String> middlewareTypeNames(HarnessAgent agent) {
        List<String> names = new ArrayList<>();
        for (MiddlewareBase middleware : agent.getDelegate().getMiddlewares()) {
            names.add(middleware.getClass().getName());
        }
        return names;
    }
}
