package com.zhu.runtime.domain.middleware;

import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.middleware.ReasoningInput;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.memory.compaction.CompactionConfig;
import io.agentscope.harness.agent.middleware.CompactionMiddleware;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 危机档整段跳过 Harness Compaction（落地 ②）。
 * 工厂对 Harness 使用 {@code disableCompaction()}，由本中间件在非 CRITICAL 时再委托
 * {@link CompactionMiddleware}（含默认 {@code flushBeforeCompact}）。
 * 须挂在 {@link SafetyGateMiddleware} 之后，才能在 {@code onReasoning} 读到本轮 RiskLevel。
 * 不与框架 {@code compaction(config)} 同挂：后者会把未闸中间件塞进链（见 docs/compaction-hook-落地.md）。
 * 见 {@code docs/compaction-落地.md}。
 */
@Component
public class CrisisGatedCompactionMiddleware implements MiddlewareBase {

    private final Logger log = Logger.getLogger(CrisisGatedCompactionMiddleware.class.getName());

    /** 框架默认触发/保留；不另配摘要模型。 */
    private final CompactionConfig config;

    /** 测试观察：因 CRITICAL 跳过的次数。 */
    private final AtomicInteger criticalSkips = new AtomicInteger();

    /** 测试观察：委托给框架 CompactionMiddleware 的次数。 */
    private final AtomicInteger compactDelegations = new AtomicInteger();

    /** 生产默认：框架 CompactionConfig 默认值。 */
    public CrisisGatedCompactionMiddleware() {
        this(CompactionConfig.builder().build());
    }

    /**
     * 可注入配置，便于单测降低 triggerMessages。
     */
    public CrisisGatedCompactionMiddleware(CompactionConfig config) {
        this.config = config == null ? CompactionConfig.builder().build() : config;
    }

    /** 危机跳过次数（单测用）。 */
    public int criticalSkips() {
        return criticalSkips.get();
    }

    /** 已委托框架 compact 次数（单测用；不表示一定触发了摘要）。 */
    public int compactDelegations() {
        return compactDelegations.get();
    }

    /**
     * CRITICAL 直接放行 reasoning，不构造 CompactionMiddleware。
     * 非危机时委托框架；缺 Harness/工作区/模型时安静放行，不挡回复。
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
        Model model = harness.getModel();
        if (workspace == null || model == null) {
            return next.apply(input);
        }
        try {
            compactDelegations.incrementAndGet();
            CompactionMiddleware compaction = new CompactionMiddleware(workspace, model, config);
            return compaction.onReasoning(agent, context, input, next);
        } catch (RuntimeException ex) {
            log.log(Level.WARNING, "crisis-gated compaction failed", ex);
            return next.apply(input);
        }
    }
}
