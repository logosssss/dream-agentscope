package com.zhu.bootstrap;

import com.zhu.runtime.infrastructure.agent.PermissionContextFactory;
import com.zhu.semantic.config.permission.ConfiguredUnconditionalPermissionCatalog;
import com.zhu.semantic.config.permission.PermissionProperties;
import com.zhu.semantic.contract.permission.PermissionDecision;
import com.zhu.semantic.contract.permission.UnconditionalPermissionCatalog;
import com.zhu.semantic.contract.permission.UnconditionalToolRule;
import io.agentscope.core.message.ToolResultBlock;
import io.agentscope.core.permission.PermissionBehavior;
import io.agentscope.core.permission.PermissionContextState;
import io.agentscope.core.permission.PermissionEngine;
import io.agentscope.core.permission.PermissionMode;
import io.agentscope.core.tool.ToolBase;
import io.agentscope.core.tool.ToolCallParam;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PermissionEngine 落地 ④：build 用的 PermissionContext 工厂。
 * 不接 Gateway，不改审批中间件。
 */
class PermissionContextFactoryTest {

    /** 空目录时仍是 BYPASS 且非 trivial，未匹配工具为 ALLOW。 */
    @Test
    void emptyCatalog_bypassAllowsUnmatched() throws Exception {
        PermissionContextFactory factory = new PermissionContextFactory(List::of);
        PermissionContextState context = factory.create();
        assertThat(context.getMode()).isEqualTo(PermissionMode.BYPASS);
        assertThat(context.isTrivial()).isFalse();
        assertThat(check(context, "any_tool")).isEqualTo(PermissionBehavior.ALLOW);
    }

    /** 无条件 ASK 进入 ask 桶，checkPermission 可读。 */
    @Test
    void unconditionalAsk_isReadable() throws Exception {
        UnconditionalPermissionCatalog catalog =
                () -> List.of(new UnconditionalToolRule("echo_text", PermissionDecision.ASK));
        PermissionContextState context = new PermissionContextFactory(catalog).create();
        assertThat(check(context, "echo_text")).isEqualTo(PermissionBehavior.ASK);
        assertThat(check(context, "other_tool")).isEqualTo(PermissionBehavior.ALLOW);
    }

    /** 配置目录只收下租户/角色均为 * 的启用行。 */
    @Test
    void configuredCatalog_filtersScopedRules() {
        PermissionProperties properties = new PermissionProperties();
        PermissionProperties.Rule globalAsk = new PermissionProperties.Rule();
        globalAsk.setToolId("echo_text");
        globalAsk.setTenantId("*");
        globalAsk.setRoleId("*");
        globalAsk.setDecision("ASK");
        PermissionProperties.Rule tenantOnly = new PermissionProperties.Rule();
        tenantOnly.setToolId("shell");
        tenantOnly.setTenantId("demo");
        tenantOnly.setRoleId("*");
        tenantOnly.setDecision("DENY");
        properties.setRules(List.of(globalAsk, tenantOnly));

        List<UnconditionalToolRule> listed = new ConfiguredUnconditionalPermissionCatalog(properties).list();
        assertThat(listed).containsExactly(new UnconditionalToolRule("echo_text", PermissionDecision.ASK));
    }

    private static PermissionBehavior check(PermissionContextState context, String tool) throws Exception {
        return new PermissionEngine(context)
                .checkPermission(new SpikeTool(tool), Map.of())
                .toFuture()
                .get(5, TimeUnit.SECONDS)
                .getBehavior();
    }

    /**
     * 测试用工具桩。
     */
    private static final class SpikeTool extends ToolBase {

        private SpikeTool(String name) {
            super(name, "permission context factory", Map.of(), true, true, false, null, false, false);
        }

        @Override
        public Mono<ToolResultBlock> callAsync(ToolCallParam param) {
            return Mono.empty();
        }
    }
}
