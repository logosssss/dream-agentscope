package com.zhu.admin;

import com.zhu.admin.application.event.MysqlOperationAuditCatalog;
import com.zhu.admin.infrastructure.persistence.event.OperationAuditMapper;
import com.zhu.admin.infrastructure.persistence.entity.event.OperationAudit;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
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
            assertThat(saved.createdAt()).isEmpty();
        });
        ArgumentCaptor<OperationAudit> saved = ArgumentCaptor.forClass(OperationAudit.class);
        verify(audits).insert(saved.capture());
        assertThat(saved.getValue().getTenantId()).isEmpty();
        assertThat(saved.getValue().getObjectId()).isNull();

        when(audits.recent(50)).thenReturn(List.of());
        assertThat(catalog.recent(50)).isEmpty();

        OperationAudit row = new OperationAudit();
        row.setOperatorId("op-a");
        row.setTenantId("t");
        row.setOperation("UPDATE");
        row.setObjectType("tenant");
        row.setObjectId("demo");
        row.setCreatedAt(LocalDateTime.of(2026, 11, 25, 12, 0, 1, 250_000_000));
        when(audits.recent(50)).thenReturn(List.of(row));
        assertThat(catalog.recent(50)).singleElement().satisfies(listed -> {
            assertThat(listed.createdAt()).isEqualTo("2026-11-25T12:00:01.25");
            assertThat(listed.operatorId()).isEqualTo("op-a");
        });
    }
}
