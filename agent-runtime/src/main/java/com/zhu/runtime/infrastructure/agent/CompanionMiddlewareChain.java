package com.zhu.runtime.infrastructure.agent;

import com.zhu.runtime.domain.middleware.ApprovalMiddleware;
import com.zhu.runtime.domain.middleware.AuditMiddleware;
import com.zhu.runtime.domain.middleware.CrisisGatedCompactionMiddleware;
import com.zhu.runtime.domain.middleware.CrisisGatedMemoryFlushMiddleware;
import com.zhu.runtime.domain.middleware.CrisisGatedToolResultEvictionMiddleware;
import com.zhu.runtime.domain.middleware.FileWriteLimitMiddleware;
import com.zhu.runtime.domain.middleware.PersonaConsistencyMiddleware;
import com.zhu.runtime.domain.middleware.SafetyGateMiddleware;
import io.agentscope.core.middleware.MiddlewareBase;
import io.agentscope.harness.agent.HarnessAgent;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 搭子产品中间件固定顺序链。基础设施装配层：只负责挂载顺序，不改变各闸行为。
 * 顺序与图册「companion 一轮 Middleware」一致；tracing / shutdown 仍由 {@link HarnessAgentFactory#configureCommon} 挂。
 * 不用 {@code @Order} 自动收集，避免误挂或顺序漂移。
 */
@Component
public class CompanionMiddlewareChain {

    /** 不可变有序列表；单测用 {@link #ordered()} 断言类型序。 */
    private final List<MiddlewareBase> ordered;

    /**
     * 构造时锁死八件套顺序。参数名与图册节点一一对应，禁止按类型乱序注入。
     */
    public CompanionMiddlewareChain(
            AuditMiddleware audit,
            FileWriteLimitMiddleware fileWriteLimit,
            ApprovalMiddleware approval,
            SafetyGateMiddleware safetyGate,
            CrisisGatedToolResultEvictionMiddleware crisisGatedToolResultEviction,
            CrisisGatedCompactionMiddleware crisisGatedCompaction,
            PersonaConsistencyMiddleware personaConsistency,
            CrisisGatedMemoryFlushMiddleware crisisGatedMemoryFlush) {
        this.ordered = List.of(
                Objects.requireNonNull(audit, "audit"),
                Objects.requireNonNull(fileWriteLimit, "fileWriteLimit"),
                Objects.requireNonNull(approval, "approval"),
                Objects.requireNonNull(safetyGate, "safetyGate"),
                Objects.requireNonNull(crisisGatedToolResultEviction, "crisisGatedToolResultEviction"),
                Objects.requireNonNull(crisisGatedCompaction, "crisisGatedCompaction"),
                Objects.requireNonNull(personaConsistency, "personaConsistency"),
                Objects.requireNonNull(crisisGatedMemoryFlush, "crisisGatedMemoryFlush"));
    }

    /**
     * 按固定顺序挂到 Builder。只给 companion 用；echo 不调。
     */
    public void apply(HarnessAgent.Builder builder) {
        if (builder == null) {
            return;
        }
        for (MiddlewareBase middleware : ordered) {
            builder.middleware(middleware);
        }
    }

    /**
     * 返回不可变顺序副本。供单测断言类型序列；生产路径只走 {@link #apply}。
     */
    public List<? extends MiddlewareBase> ordered() {
        return ordered;
    }
}
