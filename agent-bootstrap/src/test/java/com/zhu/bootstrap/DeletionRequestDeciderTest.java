package com.zhu.bootstrap;

import com.zhu.audit.api.ApprovalDecision;
import com.zhu.audit.application.MysqlDeletionRequestDecider;
import com.zhu.audit.infrastructure.persistence.DataDeletionRequestMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataDeletionRequest;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除请求决定只改状态。不连接 MySQL，也不删数据。
 */
class DeletionRequestDeciderTest {

    /** 没有请求或已经决定过时拒绝。通过后状态是 APPROVED。 */
    @Test
    void decideUpdatesPendingOnly() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestDecider decider = new MysqlDeletionRequestDecider(requests);

        assertThatThrownBy(() -> decider.decide("req-a", ApprovalDecision.APPROVED))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(requests, never()).decidePending("req-a", "APPROVED");

        DataDeletionRequest pending = new DataDeletionRequest();
        pending.setRequestId("req-a");
        pending.setUserId("u");
        pending.setTenantId("");
        pending.setDataType("SESSION");
        pending.setStatus("PENDING");
        when(requests.findByRequestId("req-a")).thenReturn(pending);
        when(requests.decidePending("req-a", "APPROVED")).thenReturn(1);

        assertThat(decider.decide("req-a", ApprovalDecision.APPROVED).status()).isEqualTo("APPROVED");

        pending.setStatus("APPROVED");
        assertThatThrownBy(() -> decider.decide("req-a", ApprovalDecision.REJECTED))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
