package com.zhu.bootstrap;

import com.zhu.audit.api.BillingSummary;
import com.zhu.audit.application.MysqlBillingRollup;
import com.zhu.audit.infrastructure.persistence.BillingRecordMapper;
import com.zhu.audit.infrastructure.persistence.TokenUsageMapper;
import com.zhu.audit.infrastructure.persistence.UsagePeriodTotal;
import com.zhu.audit.infrastructure.persistence.entity.BillingRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 草稿账单按区间合计。多种币种或重复区间不入库。不连接 MySQL。
 */
class BillingRollupTest {

    /** 一种币种时金额和 token 原样进入草稿。 */
    @Test
    void draftsOneCurrency() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        BillingRecordMapper bills = mock(BillingRecordMapper.class);
        UsagePeriodTotal total = new UsagePeriodTotal();
        total.setTokenTotal(8L);
        total.setCostAmount(new BigDecimal("1.250000"));
        total.setCurrencyCount(1);
        total.setCurrency("USD");
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-02T00:00:00");
        when(usages.sumForPeriod("t", start, end)).thenReturn(total);

        BillingSummary summary = new MysqlBillingRollup(usages, bills).draft("t", start, end);

        ArgumentCaptor<BillingRecord> saved = ArgumentCaptor.forClass(BillingRecord.class);
        verify(bills).insert(saved.capture());
        assertThat(saved.getValue().getStatus()).isEqualTo("DRAFT");
        assertThat(summary.tokenTotal()).isEqualTo(8L);
        assertThat(summary.costAmount()).isEqualByComparingTo("1.250000");
        assertThat(summary.currency()).isEqualTo("USD");
    }

    /** 两种币种时不插入。 */
    @Test
    void mixedCurrenciesAreRejected() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        BillingRecordMapper bills = mock(BillingRecordMapper.class);
        UsagePeriodTotal total = new UsagePeriodTotal();
        total.setCurrencyCount(2);
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-02T00:00:00");
        when(usages.sumForPeriod("t", start, end)).thenReturn(total);

        assertThatThrownBy(() -> new MysqlBillingRollup(usages, bills).draft("t", start, end))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.BAD_REQUEST);
        verify(bills, never()).insert(any(BillingRecord.class));
    }

    /** 同一区间再生成一次按冲突处理。 */
    @Test
    void duplicatePeriodConflicts() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        BillingRecordMapper bills = mock(BillingRecordMapper.class);
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-02T00:00:00");
        when(usages.sumForPeriod("t", start, end)).thenReturn(new UsagePeriodTotal());
        doThrow(new DuplicateKeyException("duplicate")).when(bills).insert(any(BillingRecord.class));

        assertThatThrownBy(() -> new MysqlBillingRollup(usages, bills).draft("t", start, end))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }
}
