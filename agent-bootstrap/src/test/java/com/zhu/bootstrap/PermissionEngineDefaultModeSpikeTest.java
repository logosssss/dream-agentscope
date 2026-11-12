package com.zhu.bootstrap;

import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionDecision;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.permission.PermissionRule;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PermissionEngine 落地 ①：对照 spike，不接审批表、不改 Gateway。
 * 只验证非 trivial + {@link PermissionMode#DEFAULT}、无匹配规则时引擎对未知工具的行为。
 */
class PermissionEngineDefaultModeSpikeTest {

    private static final String UNKNOWN = "spike_unknown_tool";

    private static final String OTHER = "spike_other_tool";

    /**
     * 空 builder 仍可能是 trivial；先确认「只设 DEFAULT、零规则」是否非 trivial。
     * 若仍 trivial，本块结论要记进落地文档，后续不能靠「只设 mode」离开 trivial。
     */
    @Test
    void defaultModeAlone_mayStillBeTrivial() {
        PermissionContextState context = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)
                .build();
        // 观察项：仅 mode 往往不够；isTrivial 结果写入断言，防止以后框架改默认还以为已非 trivial。
        assertThat(context.getMode()).isEqualTo(PermissionMode.DEFAULT);
        assertThat(context.isTrivial())
                .as("DEFAULT-only context trivial?")
                .isTrue();
    }

    /**
     * 用另一工具的 ALLOW 规则把上下文撑成非 trivial，再问未知工具。
     * 锁定目标是未匹配 ALLOW；若框架给 ASK/DENY，断言钉住实测，供 ③ 换 mode。
     */
    @Test
    void defaultMode_unmatchedTool_recordsFrameworkBehavior() throws Exception {
        // ruleContent 非 null 时 ToolBase.matchRule 为 false，这条只用来撑非 trivial，不指望命中 OTHER。
        PermissionRule keepAlive = new PermissionRule(
                OTHER, "keep-non-trivial", PermissionBehavior.ALLOW, "spike");
        PermissionContextState context = PermissionContextState.builder()
                .mode(PermissionMode.DEFAULT)
                .addAllowRule(OTHER, keepAlive)
                .build();
        assertThat(context.isTrivial()).isFalse();

        PermissionEngine engine = new PermissionEngine(context);
        PermissionDecision decision = engine
                .checkPermission(new SpikeTool(UNKNOWN), Map.of())
                .toFuture()
                .get(5, TimeUnit.SECONDS);

        assertThat(decision).isNotNull();
        // 实测钉桩：2.0.4 DEFAULT + 非 trivial、无本工具规则时的行为。
        // 若将来升级框架导致失败，先改落地文档再改断言。
        assertThat(decision.getBehavior())
                .as("DEFAULT unmatched behavior (product wants ALLOW)")
                .isEqualTo(PermissionBehavior.ASK);
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
