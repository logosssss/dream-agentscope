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
 * PermissionEngine 落地 ②：对照 spike，不接审批表、不改 Gateway。
 * 验证工具级显式 ALLOW / ASK / DENY 注入后 {@code checkPermission} 可读。
 */
class PermissionEngineExplicitRuleSpikeTest {

    private static final String TOOL = "spike_explicit_tool";

    /** 本工具挂 ALLOW 时，判定为允许。 */
    @Test
    void explicitAllow_isReadable() throws Exception {
        assertThat(decide(PermissionBehavior.ALLOW)).isEqualTo(PermissionBehavior.ALLOW);
    }

    /** 本工具挂 ASK 时，判定为询问。 */
    @Test
    void explicitAsk_isReadable() throws Exception {
        assertThat(decide(PermissionBehavior.ASK)).isEqualTo(PermissionBehavior.ASK);
    }

    /** 本工具挂 DENY 时，判定为拒绝。 */
    @Test
    void explicitDeny_isReadable() throws Exception {
        assertThat(decide(PermissionBehavior.DENY)).isEqualTo(PermissionBehavior.DENY);
    }

    /**
     * 按行为把规则放进对应桶。
     * {@link ToolBase#matchRule} 默认只在 ruleContent 为 null 时返回 true；传 {@code *} 永远不命中，会落到未匹配 ASK。
     */
    private static PermissionBehavior decide(PermissionBehavior behavior) throws Exception {
        PermissionRule rule = new PermissionRule(TOOL, null, behavior, "spike");
        PermissionContextState.Builder builder = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT);
        switch (behavior) {
            case ALLOW -> builder.addAllowRule(TOOL, rule);
            case ASK -> builder.addAskRule(TOOL, rule);
            case DENY -> builder.addDenyRule(TOOL, rule);
            default -> throw new IllegalStateException("spike only covers ALLOW/ASK/DENY");
        }
        PermissionContextState context = builder.build();
        assertThat(context.isTrivial()).isFalse();

        PermissionDecision decision = new PermissionEngine(context)
                .checkPermission(new SpikeTool(TOOL), Map.of())
                .toFuture()
                .get(5, TimeUnit.SECONDS);
        assertThat(decision).isNotNull();
        return decision.getBehavior();
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
