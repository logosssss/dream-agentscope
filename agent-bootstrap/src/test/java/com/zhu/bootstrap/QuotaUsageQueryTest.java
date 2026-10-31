package com.zhu.bootstrap;

import com.zhu.audit.application.MysqlQuotaUsageQuery;
import com.zhu.audit.infrastructure.persistence.QuotaUsageLogMapper;
import com.zhu.audit.infrastructure.persistence.entity.QuotaUsageLog;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 按租户读配额流水。不连接 MySQL，也不回补。
 */
class QuotaUsageQueryTest {

    /** 空标识不查库。有行时原样返回数量和写入时间。 */
    @Test
    void recentRequiresATenant() {
        QuotaUsageLogMapper logs = mock(QuotaUsageLogMapper.class);
        MysqlQuotaUsageQuery query = new MysqlQuotaUsageQuery(logs);
        assertThatThrownBy(() -> query.recent(" ", 50))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verifyNoInteractions(logs);

        QuotaUsageLog row = new QuotaUsageLog();
        row.setId(3L);
        row.setTenantId("t");
        row.setUserId("u");
        row.setQuotaType("REQUESTS");
        row.setAmount(1L);
        row.setReason("chat");
        row.setCreatedAt(LocalDateTime.parse("2026-10-31T03:00:00"));
        when(logs.recent("t", 50)).thenReturn(List.of(row));

        assertThat(query.recent("t", 50)).singleElement().satisfies(saved -> {
            assertThat(saved.id()).isEqualTo(3L);
            assertThat(saved.amount()).isEqualTo(1L);
            assertThat(saved.reason()).isEqualTo("chat");
            assertThat(saved.createdAt()).isEqualTo(LocalDateTime.parse("2026-10-31T03:00:00"));
        });
    }
}
