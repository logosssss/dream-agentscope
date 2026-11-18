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
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * 搭子产品中间件链顺序测。类型序列须与图册「companion 一轮 Middleware」一致。
 */
class CompanionMiddlewareChainTest {

    /**
     * ordered() 固定为 Audit → FileWriteLimit → Approval → SafetyGate →
     * CrisisGatedEviction → CrisisGatedCompaction → Persona → CrisisGatedMemoryFlush。
     */
    @Test
    void ordered_matchesDiagramCompanionProductChain() {
        CompanionMiddlewareChain chain = new CompanionMiddlewareChain(
                mock(AuditMiddleware.class),
                mock(FileWriteLimitMiddleware.class),
                mock(ApprovalMiddleware.class),
                mock(SafetyGateMiddleware.class),
                mock(CrisisGatedToolResultEvictionMiddleware.class),
                mock(CrisisGatedCompactionMiddleware.class),
                mock(PersonaConsistencyMiddleware.class),
                mock(CrisisGatedMemoryFlushMiddleware.class));

        List<? extends MiddlewareBase> ordered = chain.ordered();

        // mock 是子类实例，按类型位次断言，不用 getClass 精确相等。
        assertThat(ordered).hasSize(8);
        assertThat(ordered.get(0)).isInstanceOf(AuditMiddleware.class);
        assertThat(ordered.get(1)).isInstanceOf(FileWriteLimitMiddleware.class);
        assertThat(ordered.get(2)).isInstanceOf(ApprovalMiddleware.class);
        assertThat(ordered.get(3)).isInstanceOf(SafetyGateMiddleware.class);
        assertThat(ordered.get(4)).isInstanceOf(CrisisGatedToolResultEvictionMiddleware.class);
        assertThat(ordered.get(5)).isInstanceOf(CrisisGatedCompactionMiddleware.class);
        assertThat(ordered.get(6)).isInstanceOf(PersonaConsistencyMiddleware.class);
        assertThat(ordered.get(7)).isInstanceOf(CrisisGatedMemoryFlushMiddleware.class);
        assertThat(ordered).isUnmodifiable();
    }
}
