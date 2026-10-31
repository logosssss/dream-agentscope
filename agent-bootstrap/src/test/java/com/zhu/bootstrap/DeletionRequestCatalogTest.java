package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlDeletionRequestCatalog;
import com.zhu.audit.infrastructure.persistence.DataDeletionRequestMapper;
import com.zhu.audit.infrastructure.persistence.entity.DataDeletionRequest;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
}
