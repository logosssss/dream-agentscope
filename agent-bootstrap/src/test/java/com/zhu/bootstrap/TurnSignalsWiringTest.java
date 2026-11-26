package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.zhu.runtime.application.turn.AgentTurnSupport;
import com.zhu.runtime.domain.TurnSignals;
import com.zhu.semantic.contract.product.PersonaContract;
import com.zhu.semantic.contract.safety.RiskLevel;
import io.agentscope.core.agent.RuntimeContext;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 中间件写在共享信号上的档位与人设标记，收口必须读得到。
 *
 * <p>回归背景：中间件拿到的是 Gateway 合成的 {@code RuntimeContext}，
 * 收口拿的是调用线程上那份本地上下文，两者不是同一个对象。早先只在框架那份
 * {@code put(RiskLevel.class, …)}，收口读自己那份永远是 NORMAL / false，
 * 于是危机输出红线（换预置稿）与人设一致性检查在生产恒不触发。
 */
class TurnSignalsWiringTest {

    private static final String CRISIS = "危机话术（预置）";

    /** 危机档：模型回复踩到玩笑词时必须换成预置稿，不能让模型原文出去。 */
    @Test
    void criticalSignalsReplaceTheModelReply() {
        TurnSignals signals = new TurnSignals();
        signals.risk(RiskLevel.CRITICAL);

        String text = turns().finish("哈哈这有什么好难受的", contextWith(signals));

        assertThat(text).isEqualTo(CRISIS);
    }

    /** 普通档：同样的回复原样过，不误换。 */
    @Test
    void normalSignalsKeepTheModelReply() {
        String text = turns().finish("哈哈这有什么好难受的", contextWith(new TurnSignals()));

        assertThat(text).isEqualTo("哈哈这有什么好难受的");
    }

    /** 人设标记：命中漂移词时换成契约基线。 */
    @Test
    void personaSignalsReplaceDriftedReply() {
        TurnSignals signals = new TurnSignals();
        signals.personaCheck(true);

        String text = turns().finish("我是你的恋人呀", contextWith(signals));

        assertThat(text).isEqualTo(PERSONA.coreIdentity() + PERSONA.speechStyle());
    }

    /** 没有开标记时，漂移词也照原样过；检查只看标记，不猜。 */
    @Test
    void personaCheckOffKeepsReply() {
        String text = turns().finish("我是你的恋人呀", contextWith(new TurnSignals()));

        assertThat(text).isEqualTo("我是你的恋人呀");
    }

    /** 上下文里没有共享信号时按普通档收口，不抛。 */
    @Test
    void missingSignalsIsNormal() {
        RuntimeContext bare = RuntimeContext.builder().userId("u").sessionId("s").build();

        assertThat(turns().finish("原话", bare)).isEqualTo("原话");
    }

    /**
     * 危机档下报告为危机的调用方：共享信号由 {@code AgentTurnSupport.context(...)} 建，
     * 与中间件拿到的是同一个实例（这一条由 resolver 的复制保证，见 AgentChannelRuntimeContextResolverTest）。
     */
    @Test
    void contextCarriesTheSameSignalsInstance() {
        TurnSignals signals = new TurnSignals();
        RuntimeContext context = turns().context(
                new com.zhu.runtime.api.agent.AgentRequest("companion-v1", "t", "u", "s", "", "hi"),
                "run-1",
                signals);

        assertThat(context.get(TurnSignals.class)).isSameAs(signals);
    }

    private static RuntimeContext contextWith(TurnSignals signals) {
        return RuntimeContext.builder()
                .userId("u")
                .sessionId("s")
                .put(TurnSignals.class, signals)
                .build();
    }

    private static AgentTurnSupport turns() {
        // finish 只用到 crisis 与 persona，其余依赖不进这条路径。
        return new AgentTurnSupport(null, null, null, () -> CRISIS, PERSONA);
    }

    /** 与搭子同一句话的锁定人格，测试里只用到身份与语气。 */
    private static final PersonaContract PERSONA = new PersonaContract() {

        @Override
        public String agentId() {
            return "companion-v1";
        }

        @Override
        public String coreIdentity() {
            return "你是用户的智能搭子。";
        }

        @Override
        public String speechStyle() {
            return "自然、克制、不卖惨。";
        }

        @Override
        public List<String> boundaries() {
            return List.of();
        }

        @Override
        public String relationshipStage() {
            return "相识";
        }
    };
}
