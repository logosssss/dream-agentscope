package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.CompanionMemoryMiddleware;
import com.zhu.semantic.config.CompanionPersona;
import com.zhu.semantic.contract.RiskLevel;
import com.zhu.state.api.EpisodicMemoryRepository;
import com.zhu.state.api.UserProfileRepository;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.middleware.AgentInput;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 普通和升高写短摘要。危机不写。不连接 MySQL，也不调用模型。
 */
class CompanionMemoryMiddlewareTest {

    /** 闲聊写下摘要和相识阶段。摘要不含换行。 */
    @Test
    void normalTurnWritesShortMemory() {
        List<String> memories = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        CompanionMemoryMiddleware middleware = middleware(memories, profiles, false);

        complete(middleware, RiskLevel.NORMAL, "今天去散步了\n顺便买菜");

        assertThat(memories).containsExactly("t|u|s|今天去散步了 顺便买菜|2");
        assertThat(profiles).containsExactly("t|u|ACQUAINTANCE");
    }

    /** 升高档仍写，重要程度更高。 */
    @Test
    void elevatedTurnUsesHigherSalience() {
        List<String> memories = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        CompanionMemoryMiddleware middleware = middleware(memories, profiles, false);

        complete(middleware, RiskLevel.ELEVATED, "我有点难受");

        assertThat(memories).containsExactly("t|u|s|我有点难受|4");
        assertThat(profiles).containsExactly("t|u|ACQUAINTANCE");
    }

    /** 危机档不落记忆，也不改画像。 */
    @Test
    void criticalTurnSkipsMemory() {
        List<String> memories = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        CompanionMemoryMiddleware middleware = middleware(memories, profiles, false);

        complete(middleware, RiskLevel.CRITICAL, "我想自杀");

        assertThat(memories).isEmpty();
        assertThat(profiles).isEmpty();
    }

    /** 超长用户输入截成短句。写库失败不往外抛。 */
    @Test
    void longTextIsClippedAndWriteFailureIsSwallowed() {
        List<String> memories = new ArrayList<>();
        List<String> profiles = new ArrayList<>();
        CompanionMemoryMiddleware middleware = middleware(memories, profiles, false);

        complete(middleware, RiskLevel.NORMAL, "a".repeat(100));

        assertThat(memories).hasSize(1);
        assertThat(memories.get(0)).endsWith("|2");
        assertThat(memories.get(0).split("\\|", -1)[3]).hasSize(80);

        CompanionMemoryMiddleware failing = middleware(new ArrayList<>(), new ArrayList<>(), true);
        complete(failing, RiskLevel.NORMAL, "今天聊了散步");
    }

    private static void complete(CompanionMemoryMiddleware middleware, RiskLevel level, String userText) {
        RuntimeContext context = RuntimeContext.builder()
                .userId("u")
                .sessionId("s")
                .put("tenantId", "t")
                .put(RiskLevel.class, level)
                .build();
        AgentInput input = new AgentInput(List.of(
                Msg.builder().role(MsgRole.USER).textContent(userText).build()));
        middleware.onAgent(mock(Agent.class), context, input, ignored -> Flux.empty()).blockLast();
    }

    private static CompanionMemoryMiddleware middleware(
            List<String> memories,
            List<String> profiles,
            boolean failWrites) {
        EpisodicMemoryRepository memoryStore = (tenant, user, session, summary, salience) -> {
            if (failWrites) {
                throw new IllegalStateException("db");
            }
            memories.add(tenant + "|" + user + "|" + session + "|" + summary + "|" + salience);
        };
        UserProfileRepository profileStore = (tenant, user, stage, notes) -> {
            if (failWrites) {
                throw new IllegalStateException("db");
            }
            profiles.add(tenant + "|" + user + "|" + stage);
        };
        return new CompanionMemoryMiddleware(memoryStore, profileStore, new CompanionPersona());
    }
}
