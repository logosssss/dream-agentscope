package com.zhu.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.zhu.runtime.api.PendingAskSummary;
import com.zhu.runtime.infrastructure.approval.PendingAskFacadeService;
import com.zhu.runtime.infrastructure.approval.PendingAskStore;
import com.zhu.runtime.infrastructure.gateway.ChannelGateway;
import io.agentscope.core.message.ToolUseBlock;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/**
 * 防腐③：PendingAskFacade 摘要读写与原子 take+续跑。
 */
class PendingAskFacadeTest {

    /** remember 后 pendingFor / ownedOne 只露摘要。 */
    @Test
    void pendingAndOwnedReturnSummary() {
        PendingAskStore store = new PendingAskStore();
        store.remember("t1", "u1", "s1", "companion-v1", List.of(use("id-1", "shell")));
        PendingAskFacadeService facade = new PendingAskFacadeService(store, mock(ChannelGateway.class));

        assertThat(facade.pendingFor("t1", "u1", 10))
                .containsExactly(new PendingAskSummary("id-1", "shell", "s1"));
        assertThat(facade.ownedOne("t1", "u1", "id-1"))
                .isEqualTo(new PendingAskSummary("id-1", "shell", "s1"));
        assertThat(facade.ownedOne("t1", "other", "id-1")).isNull();
    }

    /** decideAndResume 成功则续跑一次且仓中清空；第二次同一 id 返回 null。 */
    @Test
    void decideAndResumeTakesOnce() {
        PendingAskStore store = new PendingAskStore();
        store.remember("t1", "u1", "s1", "companion-v1", List.of(use("id-1", "shell")));
        ChannelGateway gateway = mock(ChannelGateway.class);
        AtomicInteger resumes = new AtomicInteger();
        org.mockito.Mockito.doAnswer(inv -> {
            resumes.incrementAndGet();
            return null;
        }).when(gateway).resumeConfirm(anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any());
        PendingAskFacadeService facade = new PendingAskFacadeService(store, gateway);

        PendingAskSummary first = facade.decideAndResume("t1", "u1", "id-1", true, "ok");
        assertThat(first).isEqualTo(new PendingAskSummary("id-1", "shell", "s1"));
        assertThat(resumes.get()).isEqualTo(1);
        assertThat(store.ownedOne("t1", "u1", "id-1")).isNull();

        assertThat(facade.decideAndResume("t1", "u1", "id-1", true, "again")).isNull();
        verify(gateway, times(1)).resumeConfirm(
                eq("t1"), eq("u1"), eq("s1"), eq("companion-v1"), any(), eq(true), eq("ok"));
    }

    /** 别人的单不续跑。 */
    @Test
    void decideAndResumeRejectsForeignOwner() {
        PendingAskStore store = new PendingAskStore();
        store.remember("t1", "u1", "s1", "companion-v1", List.of(use("id-1", "shell")));
        ChannelGateway gateway = mock(ChannelGateway.class);
        PendingAskFacadeService facade = new PendingAskFacadeService(store, gateway);

        assertThat(facade.decideAndResume("t1", "u2", "id-1", false, null)).isNull();
        verify(gateway, never()).resumeConfirm(
                anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any());
        assertThat(store.ownedOne("t1", "u1", "id-1")).isNotNull();
    }

    private static ToolUseBlock use(String id, String name) {
        return new ToolUseBlock.Builder().id(id).name(name).input(Map.of()).build();
    }
}
