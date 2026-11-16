package com.zhu.bootstrap;

import com.zhu.access.api.controller.chat.ApprovalController;
import com.zhu.access.api.dto.chat.ApprovalDecisionRequest;
import com.zhu.access.api.dto.chat.ApprovalDecisionView;
import com.zhu.access.api.dto.chat.ApprovalDetail;
import com.zhu.access.api.dto.chat.PendingApproval;
import com.zhu.access.application.auth.gate.AccessAccountGate;
import com.zhu.access.application.chat.approval.ApprovalDecisionAppService;
import com.zhu.audit.api.approval.ApprovalDecision;
import com.zhu.audit.api.approval.ApprovalDecider;
import com.zhu.audit.api.approval.ApprovalRow;
import com.zhu.audit.api.event.AuditQuery;
import com.zhu.common.context.TenantContext;
import com.zhu.common.context.UserContext;
import com.zhu.common.dto.ApiResult;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import com.zhu.runtime.api.PendingAskFacade;
import com.zhu.runtime.infrastructure.approval.PendingAskFacadeService;
import com.zhu.runtime.infrastructure.approval.PendingAskStore;
import com.zhu.runtime.infrastructure.gateway.ChannelGateway;
import io.agentscope.core.message.ToolUseBlock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Permission Gate 落地 ③：待确认来自运行层 Gate ASK；决定经 Facade 续跑，不再只改库。
 */
class ApprovalNotifyTest {

    /** 线程身份用完就清，避免串到下一条。 */
    @AfterEach
    void clearIdentity() {
        UserContext.clear();
        TenantContext.clear();
    }

    /** 列表只读进程内登记，带会话。 */
    @Test
    void pendingReadsLiveAsks() {
        PendingAskStore store = new PendingAskStore();
        store.remember("tenant-a", "user-a", "session-a", "companion-v1", List.of(use("a-1", "shell")));
        ApprovalController controller = controller(store, mock(AuditQuery.class), mock(ApprovalDecider.class),
                openAccounts(), mock(ChannelGateway.class));
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        ApiResult<List<PendingApproval>> result = controller.pending();

        assertThat(result.data()).containsExactly(new PendingApproval("a-1", "shell", "session-a"));
    }

    /** 没有用户时不查。 */
    @Test
    void missingUserIsRejected() {
        ApprovalController controller = controller(
                new PendingAskStore(), mock(AuditQuery.class), mock(ApprovalDecider.class), openAccounts(),
                mock(ChannelGateway.class));
        assertThatThrownBy(controller::pending).isInstanceOf(BusinessException.class);
    }

    /** 账号不可用时不查。 */
    @Test
    void inactiveAccountIsRejected() {
        AuditQuery audits = mock(AuditQuery.class);
        AccessAccountGate accounts = new AccessAccountGate(
                tenantId -> {
                },
                (tenantId, userId) -> {
                    throw new BusinessException(ErrorCode.NOT_FOUND, "tenant user not found");
                });
        ApprovalController controller = controller(
                new PendingAskStore(), audits, mock(ApprovalDecider.class), accounts,
                mock(ChannelGateway.class));
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        assertThatThrownBy(controller::pending)
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(audits, never()).pendingFor(any(), any(), anyInt());
    }

    /** 本人本租户决定后必须续跑；通道固定 WEB。 */
    @Test
    void decideResumesAndReturnsView() {
        PendingAskStore store = new PendingAskStore();
        store.remember("tenant-a", "user-a", "session-a", "companion-v1", List.of(use("a-1", "shell")));
        AtomicBoolean resumed = new AtomicBoolean();
        AtomicReference<Boolean> approvedFlag = new AtomicReference<>();
        ChannelGateway gateway = mock(ChannelGateway.class);
        doAnswer(invocation -> {
            resumed.set(true);
            approvedFlag.set(invocation.getArgument(5));
            assertThat((String) invocation.getArgument(6)).isEqualTo("ok");
            return null;
        }).when(gateway).resumeConfirm(anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any());
        ApprovalDecider legacy = mock(ApprovalDecider.class);
        when(legacy.decide(any(), any(), any(), any(), any()))
                .thenThrow(new BusinessException(ErrorCode.NOT_FOUND, "legacy gone"));
        ApprovalController controller = controller(store, mock(AuditQuery.class), legacy, openAccounts(), gateway);
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        ApiResult<ApprovalDecisionView> result =
                controller.decide("a-1", new ApprovalDecisionRequest("APPROVED", "ok"));

        assertThat(result.data()).isEqualTo(new ApprovalDecisionView("a-1", "shell", "session-a", "APPROVED"));
        assertThat(resumed.get()).isTrue();
        assertThat(approvedFlag.get()).isTrue();
        assertThat(store.ownedOne("tenant-a", "user-a", "a-1")).isNull();
        verify(legacy).decide("a-1", ApprovalDecision.APPROVED, "user-a", "WEB", "ok");
    }

    /** 别人的或未登记的单号按找不到，不续跑。 */
    @Test
    void decideRejectsMissingLiveAsk() {
        ChannelGateway gateway = mock(ChannelGateway.class);
        ApprovalDecider legacy = mock(ApprovalDecider.class);
        ApprovalController controller = controller(
                new PendingAskStore(), mock(AuditQuery.class), legacy, openAccounts(), gateway);
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        assertThatThrownBy(() -> controller.decide("a-2", new ApprovalDecisionRequest("APPROVED", null)))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(gateway, never()).resumeConfirm(
                anyString(), anyString(), anyString(), anyString(), any(), anyBoolean(), any());
        verify(legacy, never()).decide(any(), any(), any(), any(), any());
    }

    /** 进程内待确认优先；没有再读旧表已决定行。 */
    @Test
    void getPrefersLiveThenLegacy() {
        PendingAskStore store = new PendingAskStore();
        store.remember("tenant-a", "user-a", "session-a", "companion-v1", List.of(use("live-1", "shell")));
        AuditQuery audits = mock(AuditQuery.class);
        LocalDateTime requested = LocalDateTime.of(2026, 11, 2, 1, 2, 3);
        LocalDateTime decidedAt = LocalDateTime.of(2026, 11, 2, 4, 5, 6);
        when(audits.ownedOne("tenant-a", "user-a", "a-1")).thenReturn(new ApprovalRow(
                "a-1", "user-a", "session-a", "shell", "APPROVED",
                requested, "user-a", "WEB", "ok", decidedAt));
        ApprovalController controller = controller(store, audits, mock(ApprovalDecider.class), openAccounts(),
                mock(ChannelGateway.class));
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        assertThat(controller.get("live-1").data()).isEqualTo(new ApprovalDetail(
                "live-1", "shell", "session-a", "PENDING", "", "", null));
        assertThat(controller.get("a-1").data()).isEqualTo(new ApprovalDetail(
                "a-1", "shell", "session-a", "APPROVED", "2026-11-02T01:02:03", "2026-11-02T04:05:06", "ok"));
    }

    /** 别人的或空单号不续跑。 */
    @Test
    void getRejectsMissingOrBlank() {
        AuditQuery audits = mock(AuditQuery.class);
        when(audits.ownedOne("tenant-a", "user-a", "a-2")).thenReturn(null);
        ApprovalController controller = controller(
                new PendingAskStore(), audits, mock(ApprovalDecider.class), openAccounts(),
                mock(ChannelGateway.class));
        TenantContext.set("tenant-a");
        UserContext.set("user-a");

        assertThatThrownBy(() -> controller.get("a-2"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> controller.get(" "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(audits, never()).ownedOne("tenant-a", "user-a", " ");
    }

    private static ApprovalController controller(
            PendingAskStore store,
            AuditQuery audits,
            ApprovalDecider legacy,
            AccessAccountGate accounts,
            ChannelGateway gateway) {
        PendingAskFacade facade = new PendingAskFacadeService(store, gateway);
        return new ApprovalController(
                audits,
                facade,
                new ApprovalDecisionAppService(facade, legacy),
                accounts);
    }

    /** 测试不查租户表。 */
    private static AccessAccountGate openAccounts() {
        return new AccessAccountGate(tenantId -> {
        }, (tenantId, userId) -> {
        });
    }

    private static ToolUseBlock use(String id, String name) {
        return new ToolUseBlock.Builder().id(id).name(name).input(Map.of()).build();
    }
}
