package com.zhu.runtime.domain.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.filesystem.AbstractFilesystem;
import io.agentscope.harness.agent.memory.compaction.ToolResultEvictionConfig;
import io.agentscope.harness.agent.middleware.ToolResultEvictionMiddleware;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 危机档整段跳过 Harness Tool Result Eviction（落地 ②）。
 * 工厂对 Harness 使用 {@code disableToolResultEviction()}，由本中间件在非 CRITICAL 时再委托
 * {@link ToolResultEvictionMiddleware}（{@code defaults()}）。
 * 须挂在 {@link SafetyGateMiddleware} 之后、{@link CrisisGatedCompactionMiddleware} 之前。
 * 见 {@code docs/tool-result-eviction-落地.md}。
 */
@Component
public class CrisisGatedToolResultEvictionMiddleware implements MiddlewareBase {

    private final Logger log = Logger.getLogger(CrisisGatedToolResultEvictionMiddleware.class.getName());

    /** 固定框架 defaults；本专题不调参。 */
    private final ToolResultEvictionConfig config;

    /** 测试观察：因 CRITICAL 跳过的次数。 */
    private final AtomicInteger criticalSkips = new AtomicInteger();

    /** 测试观察：委托给框架 ToolResultEvictionMiddleware 的次数。 */
    private final AtomicInteger evictionDelegations = new AtomicInteger();

    /** 生产默认：{@link ToolResultEvictionConfig#defaults()}。 */
    public CrisisGatedToolResultEvictionMiddleware() {
        this(ToolResultEvictionConfig.defaults());
    }

    /**
     * 可注入配置，便于单测对照；生产仍用 defaults。
     */
    public CrisisGatedToolResultEvictionMiddleware(ToolResultEvictionConfig config) {
        this.config = config == null ? ToolResultEvictionConfig.defaults() : config;
    }

    /** 危机跳过次数（单测用）。 */
    public int criticalSkips() {
        return criticalSkips.get();
    }

    /** 已委托框架 eviction 次数（单测用；不表示一定落盘）。 */
    public int evictionDelegations() {
        return evictionDelegations.get();
    }

    /**
     * CRITICAL 直接放行 reasoning，不构造框架 eviction。
     * 非危机时委托；缺 Harness / filesystem 时安静放行，不挡回复。
     */
    @Override
    public Flux<AgentEvent> onReasoning(
            Agent agent,
            RuntimeContext context,
            ReasoningInput input,
            Function<ReasoningInput, Flux<AgentEvent>> next) {
        if (CrisisGatedMemoryFlushMiddleware.critical(context)) {
            criticalSkips.incrementAndGet();
            return next.apply(input);
        }
        HarnessAgent harness = CrisisGatedMemoryFlushMiddleware.resolveHarness(agent);
        if (harness == null) {
            return next.apply(input);
        }
        WorkspaceManager workspace = harness.getWorkspaceManager();
        if (workspace == null) {
            return next.apply(input);
        }
        AbstractFilesystem filesystem = workspace.getFilesystem();
        if (filesystem == null) {
            return next.apply(input);
        }
        try {
            evictionDelegations.incrementAndGet();
            ToolResultEvictionMiddleware eviction = new ToolResultEvictionMiddleware(filesystem, config);
            return eviction.onReasoning(agent, context, input, next);
        } catch (RuntimeException ex) {
            log.log(Level.WARNING, "crisis-gated tool-result eviction failed", ex);
            return next.apply(input);
        }
    }
}
