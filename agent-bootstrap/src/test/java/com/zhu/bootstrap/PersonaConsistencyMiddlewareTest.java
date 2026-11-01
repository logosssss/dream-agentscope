package com.zhu.bootstrap;

import com.zhu.runtime.domain.middleware.PersonaConsistencyMiddleware;
import com.zhu.semantic.contract.RiskLevel;
import com.zhu.state.api.EpisodicMemoryItem;
import com.zhu.state.api.EpisodicMemoryRepository;
import com.zhu.state.api.UserProfileItem;
import com.zhu.state.api.UserProfileRepository;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 人设检查在满 20 轮、跨会话恢复或纠正身份时打开。危机档不打开。
 */
class PersonaConsistencyMiddlewareTest {

    /** 19 轮仍关闭。20 轮打开。 */
    @Test
    void enablesAfterTwentySucceeded() {
        RuntimeContext early = run(19, RiskLevel.NORMAL, "hi");
        assertThat(early.get(PersonaConsistencyMiddleware.Flag.class)).isNull();

        RuntimeContext ready = run(20, RiskLevel.NORMAL, "hi");
        assertThat(ready.get(PersonaConsistencyMiddleware.Flag.class)).isEqualTo(PersonaConsistencyMiddleware.Flag.ON);
    }

    /** 本会话还没成功过，但已有画像，算跨会话恢复。 */
    @Test
    void enablesOnRestoredProfile() {
        UserProfileRepository profiles = mock(UserProfileRepository.class);
        when(profiles.find("t", "u")).thenReturn(new UserProfileItem("t", "u", "ACQUAINTANCE", null));
        EpisodicMemoryRepository memories = mock(EpisodicMemoryRepository.class);
        when(memories.recent("t", "u", 1)).thenReturn(List.of());
        RuntimeContext context = runWith(0, RiskLevel.NORMAL, "hi", profiles, memories);
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isEqualTo(PersonaConsistencyMiddleware.Flag.ON);
    }

    /** 本会话还没成功过，但已有情景记忆，也算跨会话恢复。 */
    @Test
    void enablesOnRestoredMemory() {
        UserProfileRepository profiles = mock(UserProfileRepository.class);
        when(profiles.find("t", "u")).thenReturn(null);
        EpisodicMemoryRepository memories = mock(EpisodicMemoryRepository.class);
        when(memories.recent("t", "u", 1))
                .thenReturn(List.of(new EpisodicMemoryItem(1, "t", "u", "old", "面试", 3, null)));
        RuntimeContext context = runWith(0, RiskLevel.NORMAL, "hi", profiles, memories);
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isEqualTo(PersonaConsistencyMiddleware.Flag.ON);
    }

    /** 第一轮且没有跨会话记忆时保持关闭。闲聊不算纠正。 */
    @Test
    void firstTurnWithoutMemoryStaysOff() {
        RuntimeContext context = run(0, RiskLevel.NORMAL, "我更喜欢周末出门");
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 已经开过一轮后，有画像也不再当恢复。 */
    @Test
    void laterTurnDoesNotCountAsRestore() {
        UserProfileRepository profiles = mock(UserProfileRepository.class);
        when(profiles.find("t", "u")).thenReturn(new UserProfileItem("t", "u", "ACQUAINTANCE", null));
        RuntimeContext context = runWith(1, RiskLevel.NORMAL, "hi", profiles, mock(EpisodicMemoryRepository.class));
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 对准身份的纠正这一轮就打开，不看轮次。 */
    @Test
    void enablesOnIdentityCorrection() {
        RuntimeContext context = run(0, RiskLevel.NORMAL, "你不是助手");
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isEqualTo(PersonaConsistencyMiddleware.Flag.ON);
    }

    /** 危机档即使纠正或轮次够了也不打开，避免盖掉危机话术。 */
    @Test
    void criticalDoesNotEnable() {
        assertThat(run(30, RiskLevel.CRITICAL, "你不是助手").get(PersonaConsistencyMiddleware.Flag.class)).isNull();

        UserProfileRepository profiles = mock(UserProfileRepository.class);
        when(profiles.find("t", "u")).thenReturn(new UserProfileItem("t", "u", "ACQUAINTANCE", null));
        RuntimeContext context = runWith(0, RiskLevel.CRITICAL, "hi", profiles, mock(EpisodicMemoryRepository.class));
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 计数失败当没触发，不打开检查。 */
    @Test
    void countFailureKeepsCheckOff() {
        UserProfileRepository profiles = mock(UserProfileRepository.class);
        when(profiles.find("t", "u")).thenReturn(new UserProfileItem("t", "u", "ACQUAINTANCE", null));
        PersonaConsistencyMiddleware middleware = new PersonaConsistencyMiddleware(
                (tenant, user, session) -> {
                    throw new IllegalStateException("db");
                },
                profiles,
                mock(EpisodicMemoryRepository.class));
        RuntimeContext context = context(RiskLevel.NORMAL);
        middleware.onAgent(mock(Agent.class), context, input("hi"), ignored -> Flux.empty()).blockLast();
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 画像读失败且没有情景记忆时，恢复不开。 */
    @Test
    void profileFailureDoesNotRestore() {
        UserProfileRepository profiles = mock(UserProfileRepository.class);
        when(profiles.find("t", "u")).thenThrow(new IllegalStateException("db"));
        EpisodicMemoryRepository memories = mock(EpisodicMemoryRepository.class);
        when(memories.recent("t", "u", 1)).thenReturn(List.of());
        RuntimeContext context = runWith(0, RiskLevel.NORMAL, "hi", profiles, memories);
        assertThat(context.get(PersonaConsistencyMiddleware.Flag.class)).isNull();
    }

    /** 计数口看到同一份身份。 */
    @Test
    void countUsesCallIdentity() {
        AtomicInteger seen = new AtomicInteger();
        PersonaConsistencyMiddleware middleware = new PersonaConsistencyMiddleware(
                (tenant, user, session) -> {
                    assertThat(tenant).isEqualTo("t");
                    assertThat(user).isEqualTo("u");
                    assertThat(session).isEqualTo("s");
                    seen.incrementAndGet();
                    return 20;
                },
                null,
                null);
        middleware.onAgent(mock(Agent.class), context(RiskLevel.NORMAL), input("hi"), ignored -> Flux.empty()).blockLast();
        assertThat(seen).hasValue(1);
    }

    private static RuntimeContext run(int succeeded, RiskLevel level, String userText) {
        return runWith(succeeded, level, userText, null, null);
    }

    private static RuntimeContext runWith(
            int succeeded,
            RiskLevel level,
            String userText,
            UserProfileRepository profiles,
            EpisodicMemoryRepository memories) {
        PersonaConsistencyMiddleware middleware = new PersonaConsistencyMiddleware(
                (tenant, user, session) -> succeeded,
                profiles,
                memories);
        RuntimeContext context = context(level);
        middleware.onAgent(mock(Agent.class), context, input(userText), ignored -> Flux.empty()).blockLast();
        return context;
    }

    private static RuntimeContext context(RiskLevel level) {
        return RuntimeContext.builder()
                .userId("u")
                .sessionId("s")
                .put("tenantId", "t")
                .put(RiskLevel.class, level)
                .build();
    }

    private static AgentInput input(String userText) {
        return new AgentInput(List.of(Msg.builder().role(MsgRole.USER).textContent(userText).build()));
    }
}
