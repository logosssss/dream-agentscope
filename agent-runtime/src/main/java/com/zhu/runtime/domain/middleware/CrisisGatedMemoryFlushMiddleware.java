package com.zhu.runtime.domain.middleware;

import com.zhu.runtime.infrastructure.agent.HarnessAgentRegistry;
import com.zhu.semantic.contract.safety.RiskLevel;
import io.agentscope.core.agent.Agent;
import io.agentscope.core.agent.RuntimeContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.middleware.AgentInput;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.core.model.Model;
import io.agentscope.harness.agent.HarnessAgent;
import io.agentscope.harness.agent.IsolationScope;
import io.agentscope.harness.agent.memory.MemoryConfig;
import io.agentscope.harness.agent.memory.MemoryConsolidator;
import io.agentscope.harness.agent.middleware.MemoryFlushMiddleware;
import io.agentscope.harness.agent.middleware.MemoryMaintenanceMiddleware;
import io.agentscope.harness.agent.workspace.WorkspaceManager;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

/**
 * 危机档禁止入队 Harness Memory flush（迁入 ②）。
 * 工厂对 Harness 使用 {@code disableMemoryHooks}，由本中间件在非 CRITICAL 时再触发
 * {@link MemoryFlushMiddleware} / {@link MemoryMaintenanceMiddleware}，以保留 throttled 与 consolidation。
 * 须挂在 {@link SafetyGateMiddleware} 之后，才能读到本轮 {@link RiskLevel}。
 */
@Component
public class CrisisGatedMemoryFlushMiddleware implements MiddlewareBase {

    private static final Logger log = Logger.getLogger(CrisisGatedMemoryFlushMiddleware.class.getName());

    /** 与落地锁定一致：最小间隔 10 分钟。 */
    static final Duration DEFAULT_MIN_GAP = Duration.ofMinutes(10);

    private final MemoryConfig.FlushTrigger flushTrigger;

    /** 测试观察：因 CRITICAL 跳过的次数。 */
    private final AtomicInteger criticalSkips = new AtomicInteger();

    /** 测试观察：委托给框架 flush/maintenance 的次数。 */
    private final AtomicInteger flushDelegations = new AtomicInteger();

    /** 生产默认 throttled(10m)。 */
    public CrisisGatedMemoryFlushMiddleware() {
        this(MemoryConfig.FlushTrigger.throttled(DEFAULT_MIN_GAP));
    }

    /**
     * 可注入触发器，便于单测用 {@code always()}。
     */
    public CrisisGatedMemoryFlushMiddleware(MemoryConfig.FlushTrigger flushTrigger) {
        this.flushTrigger = flushTrigger == null
                ? MemoryConfig.FlushTrigger.throttled(DEFAULT_MIN_GAP)
                : flushTrigger;
    }

    /** 危机跳过次数（单测用）。 */
    public int criticalSkips() {
        return criticalSkips.get();
    }

    /** 已委托框架 flush 次数（单测用；不表示模型一定被调用）。 */
    public int flushDelegations() {
        return flushDelegations.get();
    }

    /**
     * 先跑完本轮对话；结束后若非危机再委托框架 flush/maintenance。
     * CRITICAL 直接结束，不构造、不调度框架 MemoryFlush。
     */
    @Override
    public Flux<AgentEvent> onAgent(
            Agent agent,
            RuntimeContext context,
            AgentInput input,
            Function<AgentInput, Flux<AgentEvent>> next) {
        return next.apply(input).concatWith(Flux.defer(() -> afterTurn(agent, context, input)));
    }

    /**
     * 危机跳过。非 Harness 或缺少工作区/模型时安静跳过，不挡回复。
     */
    private Flux<AgentEvent> afterTurn(Agent agent, RuntimeContext context, AgentInput input) {
        if (critical(context)) {
            criticalSkips.incrementAndGet();
            return Flux.empty();
        }
        HarnessAgent harness = resolveHarness(agent);
        if (harness == null) {
            return Flux.empty();
        }
        WorkspaceManager workspace = harness.getWorkspaceManager();
        Model model = harness.getModel();
        if (workspace == null || model == null) {
            return Flux.empty();
        }
        try {
            flushDelegations.incrementAndGet();
            MemoryFlushMiddleware flush = new MemoryFlushMiddleware(
                    workspace, model, null, flushTrigger, IsolationScope.USER);
            MemoryMaintenanceMiddleware maintenance = new MemoryMaintenanceMiddleware(
                    workspace, new MemoryConsolidator(workspace, model));
            // 空 next：只触发框架在 doOnComplete 上的调度，不再跑一轮对话。
            // 调度仍挂在委托 Agent 上，与框架 hooks 一致。
            Function<AgentInput, Flux<AgentEvent>> noop = ignored -> Flux.empty();
            return flush.onAgent(agent, context, input, noop)
                    .concatWith(maintenance.onAgent(agent, context, input, noop));
        } catch (RuntimeException ex) {
            log.log(Level.WARNING, "crisis-gated memory flush failed", ex);
            return Flux.empty();
        }
    }

    /**
     * Middleware 入参常是 ReAct 委托；先认 Harness，再按 agentId 查登记表。
     */
    static HarnessAgent resolveHarness(Agent agent) {
        if (agent instanceof HarnessAgent harness) {
            return harness;
        }
        if (agent == null) {
            return null;
        }
        return HarnessAgentRegistry.get(agent.getAgentId());
    }

    /** 与 Companion / Safety 同一判定：上下文上的 CRITICAL。 */
    public static boolean critical(RuntimeContext context) {
        return context != null && context.get(RiskLevel.class) == RiskLevel.CRITICAL;
    }
}
