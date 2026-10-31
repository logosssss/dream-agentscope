package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlOperationAuditCatalog;
import com.zhu.audit.infrastructure.persistence.OperationAuditMapper;
import com.zhu.audit.infrastructure.persistence.entity.OperationAudit;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 后台操作审计只登记和列出目录。不连接 MySQL，也不改业务表。
 */
class OperationAuditCatalogTest {

    /** 空白租户按空串。空白对象标识留空。缺操作人时拒绝。空列表原样返回。 */
    @Test
    void recordInsertsWithoutSnapshots() {
        OperationAuditMapper audits = mock(OperationAuditMapper.class);
        MysqlOperationAuditCatalog catalog = new MysqlOperationAuditCatalog(audits);

        assertThatThrownBy(() -> catalog.record(" ", null, "CREATE", "tenant", null))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);

        assertThat(catalog.record("op-a", " ", "CREATE", "tenant", " ")).satisfies(saved -> {
            assertThat(saved.tenantId()).isEmpty();
            assertThat(saved.objectId()).isNull();
            assertThat(saved.operation()).isEqualTo("CREATE");
        });
        ArgumentCaptor<OperationAudit> saved = ArgumentCaptor.forClass(OperationAudit.class);
        verify(audits).insert(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEmpty();
        assertThat(saved.getValue().getObjectId()).isNull();

        when(audits.recent(50)).thenReturn(List.of());
        assertThat(catalog.recent(50)).isEmpty();
    }
}
