package com.zhu.runtime.infrastructure.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.agent.AgentRequest;
import com.zhu.runtime.domain.TurnSignals;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * API 发送参数。不启动 Gateway。
 */
class ApiOutboundTest {

    /** 空标识走 main。已注册的标识原样带上，并写入租户暂存。 */
    @Test
    void blankMeansMainAndKnownIdPasses() {
        AtomicInteger checks = new AtomicInteger();
        ApiOutbound.Target main = ApiOutbound.open(request("  "), "  ", id -> {
            checks.incrementAndGet();
            return true;
        }, null, null);
        assertThat(main.agentId()).isNull();
        assertThat(main.text()).isEqualTo("hi");
        assertThat(checks.get()).isZero();

        ApiOutbound.Target known =
                ApiOutbound.open(request("docs-v1"), "docs-v1", "docs-v1"::equals, null, null);
        assertThat(known.agentId()).isEqualTo("docs-v1");
        assertThat(known.userId()).isEqualTo("u");
        assertThat(known.sessionId()).isEqualTo("s");
        Object tenant = known.context().get(AgentChannelRuntimeContextResolver.TENANT_ID);
        assertThat(tenant).isEqualTo("t");
    }

    /** 未注册的标识在发送前拒绝。 */
    @Test
    void unknownAgentIsRejected() {
        assertThatThrownBy(() -> ApiOutbound.open(request("missing"), "missing", id -> false, null, null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
    }

    /**
     * 运行号要进 caller 暂存：API 通道的审计行也只有读了框架上下文才挂得上这一轮。
     */
    @Test
    void carriesRunIdIntoCallerStaging() {
        ApiOutbound.Target target =
                ApiOutbound.open(request("docs-v1"), "docs-v1", "docs-v1"::equals, "run-9", null);
        assertThat(target.context().get(AgentChannelRuntimeContextResolver.RUN_ID, String.class))
                .isEqualTo("run-9");
    }

    /** 共享信号按同一个实例带下去，中间件才写得回收口读得到。 */
    @Test
    void carriesTheSameTurnSignalsInstance() {
        TurnSignals signals = new TurnSignals();
        ApiOutbound.Target target =
                ApiOutbound.open(request("docs-v1"), "docs-v1", "docs-v1"::equals, "run-9", signals);
        assertThat(target.context().get(TurnSignals.class)).isSameAs(signals);
    }

    private static AgentRequest request(String agentId) {
        return new AgentRequest(agentId, "t", "u", "s", "", "hi");
    }
}
