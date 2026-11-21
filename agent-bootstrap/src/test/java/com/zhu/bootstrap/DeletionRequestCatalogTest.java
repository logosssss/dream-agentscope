package com.zhu.bootstrap;

import com.zhu.ops.deletion.MysqlDeletionRequestCatalog;
import com.zhu.ops.deletion.DeletionRequestCatalog;
import com.zhu.readmodel.persistence.deletion.DataDeletionRequestMapper;
import com.zhu.readmodel.entity.deletion.DataDeletionRequest;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 删除请求只登记待处理，并读回执行结果。不连接 MySQL，也不删数据。
 */
class DeletionRequestCatalogTest {

    /** 空白租户按空串。空白原因留空。状态是 PENDING。 */
    @Test
    void submitRecordsPendingWithoutDeleting() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);

        assertThatThrownBy(() -> catalog.submit("u", null, "CHAT", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThat(catalog.submit("u", " ", "SESSION", " ")).satisfies(saved -> {
            assertThat(saved.status()).isEqualTo("PENDING");
            assertThat(saved.tenantId()).isEmpty();
            assertThat(saved.reason()).isNull();
            assertThat(saved.requestId()).isNotBlank();
            assertThat(saved.executedAt()).isEmpty();
            assertThat(saved.executedNote()).isEmpty();
        });
        ArgumentCaptor<DataDeletionRequest> saved = ArgumentCaptor.forClass(DataDeletionRequest.class);
        verify(requests).insert(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("PENDING");
        assertThat(saved.getValue().getTenantId()).isEmpty();
    }

    /** 只列出本人本租户的行。空白租户按空串。已决定的也返回。 */
    @Test
    void ownedListsRowsForTenant() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);
        DataDeletionRequest row = new DataDeletionRequest();
        row.setRequestId("req-1");
        row.setUserId("u");
        row.setTenantId("t");
        row.setDataType("SESSION");
        row.setStatus("APPROVED");
        row.setReason("done");
        when(requests.listOwned("u", "t", 50)).thenReturn(List.of(row));
        assertThat(catalog.owned("u", "t", 0)).satisfies(list -> {
            assertThat(list).hasSize(1);
            assertThat(list.get(0).requestId()).isEqualTo("req-1");
            assertThat(list.get(0).status()).isEqualTo("APPROVED");
            assertThat(list.get(0).dataType()).isEqualTo("SESSION");
        });
        verify(requests).listOwned("u", "t", 50);
        assertThat(catalog.owned("u", " ", 200)).isEmpty();
        verify(requests).listOwned("u", "", 100);
    }

    /** 空用户按请求错误，不查库。 */
    @Test
    void ownedRejectsBlankUser() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);
        assertThatThrownBy(() -> catalog.owned(" ", "t", 10))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(requests, never()).listOwned(any(), any(), anyInt());
    }

    /** 已决定的也返回。提出时间为空时用空串。仍不删数据。 */
    @Test
    void ownedOneReturnsStatusAndRequestedAt() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);
        DataDeletionRequest approved = new DataDeletionRequest();
        approved.setRequestId("req-1");
        approved.setTenantId("t");
        approved.setDataType("SESSION");
        approved.setStatus("APPROVED");
        approved.setReason("done");
        approved.setRequestedAt(LocalDateTime.of(2026, 11, 2, 1, 2, 3));
        when(requests.findOwned("req-1", "u", "t")).thenReturn(approved);
        assertThat(catalog.ownedOne("u", "t", "req-1")).satisfies(detail -> {
            assertThat(detail.requestId()).isEqualTo("req-1");
            assertThat(detail.dataType()).isEqualTo("SESSION");
            assertThat(detail.status()).isEqualTo("APPROVED");
            assertThat(detail.reason()).isEqualTo("done");
            assertThat(detail.requestedAt()).isEqualTo("2026-11-02T01:02:03");
        });
        DataDeletionRequest pending = new DataDeletionRequest();
        pending.setRequestId("req-2");
        pending.setTenantId("");
        pending.setDataType("ALL");
        pending.setStatus("PENDING");
        when(requests.findOwned("req-2", "u", "")).thenReturn(pending);
        assertThat(catalog.ownedOne("u", " ", "req-2").requestedAt()).isEqualTo("");
        assertThat(catalog.ownedOne("u", " ", "req-2").executedAt()).isEmpty();
        assertThat(catalog.ownedOne("u", " ", "req-2").executedNote()).isEmpty();
    }

    /** 执行结果原样读出：时间按 ISO 文本，摘要有则给，没执行给空串。 */
    @Test
    void ownedOneReturnsExecutionResult() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);
        DataDeletionRequest done = new DataDeletionRequest();
        done.setRequestId("req-3");
        done.setTenantId("t");
        done.setDataType("ALL");
        done.setStatus("DONE");
        done.setRequestedAt(LocalDateTime.of(2026, 11, 21, 1, 0, 0));
        done.setExecutedAt(LocalDateTime.of(2026, 11, 21, 2, 30, 0));
        done.setExecutedNote("deleted=7 skipped=[]");
        when(requests.findOwned("req-3", "u", "t")).thenReturn(done);
        assertThat(catalog.ownedOne("u", "t", "req-3")).satisfies(detail -> {
            assertThat(detail.status()).isEqualTo("DONE");
            assertThat(detail.requestedAt()).isEqualTo("2026-11-21T01:00:00");
            assertThat(detail.executedAt()).isEqualTo("2026-11-21T02:30:00");
            assertThat(detail.executedNote()).isEqualTo("deleted=7 skipped=[]");
        });
    }

    /** 别人的或没有的按找不到。空标识不查库。 */
    @Test
    void ownedOneRejectsMissingOrBlank() {
        DataDeletionRequestMapper requests = mock(DataDeletionRequestMapper.class);
        MysqlDeletionRequestCatalog catalog = new MysqlDeletionRequestCatalog(requests);
        when(requests.findOwned("req-x", "u", "t")).thenReturn(null);
        assertThatThrownBy(() -> catalog.ownedOne("u", "t", "req-x"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        assertThatThrownBy(() -> catalog.ownedOne("u", "t", " "))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        assertThatThrownBy(() -> catalog.ownedOne(" ", "t", "req-1"))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(requests).findOwned("req-x", "u", "t");
    }
}
