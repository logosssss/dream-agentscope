package com.zhu.bootstrap;

import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.runtime.domain.CallIdentity;
import io.agentscope.core.ReActAgent;
import io.agentscope.core.agent.AgentBase;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.memory.LongTermMemory;
import io.agentscope.core.memory.LongTermMemoryMode;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.util.context.ContextView;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Memory 落地 ②：record/retrieve 瞬间身份可见性 spike。
 * 钉清 LTM 调用瞬间能否从 RuntimeContext（Reactor Context）、Msg 元数据、ThreadLocal 读到租户/用户/会话/agentId。
 * 不改 Companion 主路径，不连 Redis/MySQL/Milvus。
 */
class LongTermMemoryIdentityVisibilitySpikeTest {

    @AfterEach
    void clearThreadLocals() {
        TenantContext.clear();
        UserContext.clear();
    }

    /**
     * 产品 Gateway 已把身份写进 RuntimeContext；框架在 call 链路上把它放进 Reactor Context。
     * LTM 接口虽无 RuntimeContext 参数，retrieve 用 deferContextual 仍能读到 userId/sessionId/tenantId/agentId。
     */
    @Test
    void retrieve_seesRuntimeContextViaReactorContext() {
        ObservingLongTermMemory ltm = new ObservingLongTermMemory();
        ReActAgent agent = staticAgent(ltm);
        RuntimeContext runtime = RuntimeContext.builder()
                .userId("user-a")
                .sessionId("session-a")
                .put("tenantId", "tenant-a")
                .put("agentId", "companion-v1")
                .build();
        try {
            agent.call("hi", runtime).block(Duration.ofSeconds(10));

            assertThat(ltm.retrieveSnapshots).as("retrieve 至少一次").isNotEmpty();
            // 实测：带 userId/sessionId 的 RuntimeContext 时，PostCall 侧 Memory.getMessages 常为空，
            // StaticLongTermMemoryHook 会跳过 record。身份权威仍以 Reactor Context 为准，record 另测。
            IdentitySnapshot retrieve = ltm.retrieveSnapshots.get(0);
            assertThat(retrieve.reactorUserId).isEqualTo("user-a");
            assertThat(retrieve.reactorSessionId).isEqualTo("session-a");
            assertThat(retrieve.reactorTenantId).isEqualTo("tenant-a");
            assertThat(retrieve.reactorAgentId).isEqualTo("companion-v1");
            assertThat(CallIdentity.from(retrieve.runtimeContext))
                    .isEqualTo(new CallIdentity("tenant-a", "user-a", "session-a"));
        } finally {
            agent.close();
        }
    }

    /**
     * record 与 retrieve 同一套 Reactor Context 键；不依赖 Hook 是否跳过空记忆。
     * 模拟框架 contextWrite 后，适配器用 deferContextual + CallIdentity.from 即可取身份。
     */
    @Test
    void record_seesRuntimeContextWhenSubscribedWithContext() {
        ObservingLongTermMemory ltm = new ObservingLongTermMemory();
        RuntimeContext runtime = RuntimeContext.builder()
                .userId("user-r")
                .sessionId("session-r")
                .put("tenantId", "tenant-r")
                .put("agentId", "companion-v1")
                .build();
        Msg user = userMsg("remember me", Map.of());

        ltm.record(List.of(user))
                .contextWrite(ctx -> ctx.put(AgentBase.RUNTIME_CONTEXT_KEY, runtime))
                .block(Duration.ofSeconds(5));

        assertThat(ltm.recordEntered.get()).isEqualTo(1);
        assertThat(ltm.recordSnapshots).hasSize(1);
        IdentitySnapshot record = ltm.recordSnapshots.get(0);
        assertThat(record.reactorUserId).isEqualTo("user-r");
        assertThat(record.reactorSessionId).isEqualTo("session-r");
        assertThat(record.reactorTenantId).isEqualTo("tenant-r");
        assertThat(record.reactorAgentId).isEqualTo("companion-v1");
        assertThat(CallIdentity.from(record.runtimeContext))
                .isEqualTo(new CallIdentity("tenant-r", "user-r", "session-r"));
    }

    /**
     * 框架不会把 RuntimeContext 身份自动拷进用户 Msg 的 metadata。
     * 若只靠 Msg 元数据，Gateway 必须显式侧写；默认路径拿不到租户。
     */
    @Test
    void retrieveMsg_doesNotCarryRuntimeIdentityByDefault() {
        ObservingLongTermMemory ltm = new ObservingLongTermMemory();
        ReActAgent agent = staticAgent(ltm);
        RuntimeContext runtime = RuntimeContext.builder()
                .userId("user-b")
                .sessionId("session-b")
                .put("tenantId", "tenant-b")
                .build();
        try {
            agent.call(List.of(userMsg("plain", Map.of())), runtime).block(Duration.ofSeconds(10));

            IdentitySnapshot retrieve = ltm.retrieveSnapshots.get(0);
            assertThat(retrieve.msgMetadata).isEmpty();
            assertThat(retrieve.msgUserId).isNull();
            assertThat(retrieve.msgTenantId).isNull();
            // Reactor Context 仍可见，证明权威源是上下文而非 Msg。
            assertThat(retrieve.reactorUserId).isEqualTo("user-b");
            assertThat(retrieve.reactorTenantId).isEqualTo("tenant-b");
        } finally {
            agent.close();
        }
    }

    /**
     * 若事先把身份写进 Msg.metadata，retrieve 能读到；与 RuntimeContext 并存时两边都可见。
     * 本块不定产品是否要双写，只钉「元数据路径可行」。
     */
    @Test
    void retrieveMsg_seesExplicitMetadataWhenPresent() {
        ObservingLongTermMemory ltm = new ObservingLongTermMemory();
        ReActAgent agent = staticAgent(ltm);
        Map<String, Object> meta = Map.of(
                "tenantId", "tenant-meta",
                "userId", "user-meta",
                "sessionId", "session-meta");
        RuntimeContext runtime = RuntimeContext.builder()
                .userId("user-ctx")
                .sessionId("session-ctx")
                .put("tenantId", "tenant-ctx")
                .build();
        try {
            agent.call(List.of(userMsg("with-meta", meta)), runtime).block(Duration.ofSeconds(10));

            IdentitySnapshot retrieve = ltm.retrieveSnapshots.get(0);
            assertThat(retrieve.msgTenantId).isEqualTo("tenant-meta");
            assertThat(retrieve.msgUserId).isEqualTo("user-meta");
            assertThat(retrieve.reactorUserId).isEqualTo("user-ctx");
            assertThat(retrieve.reactorTenantId).isEqualTo("tenant-ctx");
        } finally {
            agent.close();
        }
    }

    /**
     * ThreadLocal 在同步 call 同线程上可能碰巧可见，但不能当权威：
     * CallIdentity 已明确不靠 ThreadLocal；asyncRecord 还会换线程。
     * 本断言只记录实测，防止后续适配器误读 ThreadLocal。
     */
    @Test
    void threadLocal_mayBeVisibleOnSyncCall_butIsNotAuthoritative() {
        ObservingLongTermMemory ltm = new ObservingLongTermMemory();
        ReActAgent agent = staticAgent(ltm);
        TenantContext.set("tenant-tl");
        UserContext.set("user-tl");
        RuntimeContext runtime = RuntimeContext.builder()
                .userId("user-ctx")
                .sessionId("session-ctx")
                .put("tenantId", "tenant-ctx")
                .build();
        try {
            agent.call(List.of(userMsg("tl", Map.of())), runtime).block(Duration.ofSeconds(10));

            IdentitySnapshot retrieve = ltm.retrieveSnapshots.get(0);
            // 实测钉桩：同步路径上 ThreadLocal 往往仍在，但不能替代 RuntimeContext。
            assertThat(retrieve.threadTenantId).isEqualTo("tenant-tl");
            assertThat(retrieve.threadUserId).isEqualTo("user-tl");
            assertThat(retrieve.reactorTenantId).isEqualTo("tenant-ctx");
            assertThat(retrieve.reactorUserId).isEqualTo("user-ctx");
        } finally {
            agent.close();
        }
    }

    /**
     * 构建 STATIC_CONTROL Agent；关闭异步 record，保证观察点在同一订阅链上。
     */
    private static ReActAgent staticAgent(LongTermMemory ltm) {
        return ReActAgent.builder()
                .name("ltm-identity-spike")
                .model(fixedReplyModel("ok"))
                .sysPrompt("spike")
                .maxIters(1)
                .longTermMemory(ltm)
                .longTermMemoryMode(LongTermMemoryMode.STATIC_CONTROL)
                .longTermMemoryAsyncRecord(false)
                .build();
    }

    /** 用户消息；metadata 可空 map，表示未侧写。 */
    private static Msg userMsg(String text, Map<String, Object> metadata) {
        return Msg.builder()
                .role(MsgRole.USER)
                .textContent(text)
                .metadata(metadata)
                .build();
    }

    /** 固定回复模型桩。 */
    private static Model fixedReplyModel(String text) {
        return new Model() {
            @Override
            public Flux<ChatResponse> stream(
                    List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
                return Flux.just(ChatResponse.builder()
                        .id("ltm-id-spike")
                        .content(List.of(TextBlock.builder().text(text).build()))
                        .finishReason("stop")
                        .build());
            }

            @Override
            public String getModelName() {
                return "ltm-identity-spike-model";
            }
        };
    }

    /**
     * 一次 LTM 调用瞬间看到的身份切片。
     */
    private record IdentitySnapshot(
            String phase,
            RuntimeContext runtimeContext,
            String reactorUserId,
            String reactorSessionId,
            String reactorTenantId,
            String reactorAgentId,
            Map<String, Object> msgMetadata,
            String msgUserId,
            String msgTenantId,
            String threadUserId,
            String threadTenantId) {
    }

    /**
     * 观察桩：用 deferContextual 读框架 RUNTIME_CONTEXT_KEY，并顺带记 Msg / ThreadLocal。
     */
    private static final class ObservingLongTermMemory implements LongTermMemory {

        private final List<IdentitySnapshot> retrieveSnapshots = new CopyOnWriteArrayList<>();

        private final List<IdentitySnapshot> recordSnapshots = new CopyOnWriteArrayList<>();

        private final AtomicInteger recordEntered = new AtomicInteger();

        @Override
        public Mono<Void> record(List<Msg> msgs) {
            recordEntered.incrementAndGet();
            return Mono.deferContextual(view -> {
                recordSnapshots.add(snapshot("record", view, firstUser(msgs)));
                return Mono.empty();
            });
        }

        @Override
        public Mono<String> retrieve(Msg msg) {
            return Mono.deferContextual(view -> {
                retrieveSnapshots.add(snapshot("retrieve", view, msg));
                return Mono.just("spike-memory");
            });
        }

        private static IdentitySnapshot snapshot(String phase, ContextView view, Msg msg) {
            RuntimeContext runtime = view.getOrDefault(AgentBase.RUNTIME_CONTEXT_KEY, null);
            Map<String, Object> meta = msg == null || msg.getMetadata() == null
                    ? Map.of()
                    : Map.copyOf(msg.getMetadata());
            return new IdentitySnapshot(
                    phase,
                    runtime,
                    runtime == null ? null : runtime.getUserId(),
                    runtime == null ? null : runtime.getSessionId(),
                    stringExtra(runtime, "tenantId"),
                    stringExtra(runtime, "agentId"),
                    meta,
                    stringMeta(meta, "userId"),
                    stringMeta(meta, "tenantId"),
                    UserContext.get(),
                    TenantContext.get());
        }

        private static Msg firstUser(List<Msg> msgs) {
            if (msgs == null) {
                return null;
            }
            for (Msg msg : msgs) {
                if (msg != null && msg.getRole() == MsgRole.USER) {
                    return msg;
                }
            }
            return msgs.isEmpty() ? null : msgs.get(0);
        }

        private static String stringExtra(RuntimeContext runtime, String key) {
            if (runtime == null) {
                return null;
            }
            Object value = runtime.get(key);
            return value == null ? null : String.valueOf(value);
        }

        private static String stringMeta(Map<String, Object> meta, String key) {
            Object value = meta.get(key);
            return value == null ? null : String.valueOf(value);
        }
    }
}
