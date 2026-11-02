package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlDeletionRequestCatalog;
import com.zhu.audit.infrastructure.persistence.DataDeletionRequestMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataDeletionRequest;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
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
 * 删除请求只登记待处理。不连接 MySQL，也不删数据。
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
}
