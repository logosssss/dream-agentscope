package com.zhu.bootstrap;

import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PermissionEngine 落地 ③：对照 spike，不接审批表、不改 Gateway。
 * 评估 {@link PermissionMode#DONT_ASK} 能否兑现「未匹配 ALLOW」，并对照 {@link PermissionMode#BYPASS}。
 */
class PermissionEngineDontAskModeSpikeTest {

    private static final String UNKNOWN = "spike_dontask_unknown";

    private static final String OTHER = "spike_dontask_other";

    private static final String DENIED = "spike_dontask_denied";

    private static final String ASKED = "spike_dontask_asked";

    /**
     * DONT_ASK 未匹配不是 ALLOW，而是 DENY（无法回答询问时拒绝）。
     * 因此不能满足产品锁定的「未匹配 ALLOW」。
     */
    @Test
    void dontAsk_unmatched_isDenyNotAllow() throws Exception {
        PermissionDecision decision = check(PermissionMode.DONT_ASK, UNKNOWN, context -> context);
        assertThat(decision.getBehavior())
                .as("DONT_ASK unmatched (product wanted ALLOW)")
                .isEqualTo(PermissionBehavior.DENY);
    }

    /** DONT_ASK 下显式 DENY（null content）仍先生效。 */
    @Test
    void dontAsk_explicitDeny_stillWorks() throws Exception {
        PermissionDecision decision = check(PermissionMode.DONT_ASK, DENIED, builder -> builder.addDenyRule(
                DENIED, new PermissionRule(DENIED, null, PermissionBehavior.DENY, "spike")));
        assertThat(decision.getBehavior()).isEqualTo(PermissionBehavior.DENY);
    }

    /**
     * BYPASS 未匹配为 ALLOW，且在 checkDeny/checkAsk 之后才走到旁路默认。
     * 原「主路径避免 BYPASS」担心削弱 DENY；本测与下一测一起钉清。
     */
    @Test
    void bypass_unmatched_isAllow() throws Exception {
        PermissionDecision decision = check(PermissionMode.BYPASS, UNKNOWN, context -> context);
        assertThat(decision.getBehavior()).isEqualTo(PermissionBehavior.ALLOW);
    }

    /** BYPASS 不能击穿显式 DENY。 */
    @Test
    void bypass_explicitDeny_stillWorks() throws Exception {
        PermissionDecision decision = check(PermissionMode.BYPASS, DENIED, builder -> builder.addDenyRule(
                DENIED, new PermissionRule(DENIED, null, PermissionBehavior.DENY, "spike")));
        assertThat(decision.getBehavior()).isEqualTo(PermissionBehavior.DENY);
    }

    /** BYPASS 不能跳过显式 ASK。 */
    @Test
    void bypass_explicitAsk_stillWorks() throws Exception {
        PermissionDecision decision = check(PermissionMode.BYPASS, ASKED, builder -> builder.addAskRule(
                ASKED, new PermissionRule(ASKED, null, PermissionBehavior.ASK, "spike")));
        assertThat(decision.getBehavior()).isEqualTo(PermissionBehavior.ASK);
    }

    /**
     * 用另一工具的非匹配 ALLOW 规则撑非 trivial，再对目标工具名判定。
     */
    private static PermissionDecision check(
            PermissionMode mode,
            String toolName,
            java.util.function.UnaryOperator<PermissionContextState.Builder> customize)
            throws Exception {
        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(mode)
                .addAllowRule(
                        OTHER,
                        new PermissionRule(OTHER, "keep-non-trivial", PermissionBehavior.ALLOW, "spike"));
        PermissionContextState context = customize.apply(builder).build();
        assertThat(context.isTrivial()).isFalse();
        assertThat(context.getMode()).isEqualTo(mode);

        PermissionDecision decision = new PermissionEngine(context)
                .checkPermission(new SpikeTool(toolName), Map.of())
                .toFuture()
                .get(5, TimeUnit.SECONDS);
        assertThat(decision).isNotNull();
        return decision;
    }

    /**
     * 测试用工具桩。只提供名字给 PermissionEngine，不执行。
     */
    private static final class SpikeTool extends ToolBase {

        private SpikeTool(String name) {
            super(name, "permission spike", Map.of(), true, true, false, null, false, false);
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.empty();
        }
    }
}
