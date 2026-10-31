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
import static org.mockito.ArgumentMatchers.eq;
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

    /** 确认不重算，金额保持草稿上的数。 */
    @Test
    void confirmKeepsDraftAmounts() {
        TokenUsageMapper usages = mock(TokenUsageMapper.class);
        BillingRecordMapper bills = mock(BillingRecordMapper.class);
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-02T00:00:00");
        BillingRecord draft = new BillingRecord();
        draft.setStatus("DRAFT");
        draft.setTokenTotal(8L);
        draft.setCostAmount(new BigDecimal("1.250000"));
        draft.setCurrency("USD");
        when(bills.findByPeriod("t", start, end)).thenReturn(draft);
        when(bills.confirmDraft("t", start, end)).thenReturn(1);

        BillingSummary summary = new MysqlBillingRollup(usages, bills).confirm("t", start, end);

        assertThat(summary.status()).isEqualTo("CONFIRMED");
        assertThat(summary.tokenTotal()).isEqualTo(8L);
        assertThat(summary.costAmount()).isEqualByComparingTo("1.250000");
        assertThat(summary.currency()).isEqualTo("USD");
        verify(usages, never()).sumForPeriod(eq("t"), eq(start), eq(end));
    }

    /** 没有账单时不更新。 */
    @Test
    void missingBillIsNotFound() {
        BillingRecordMapper bills = mock(BillingRecordMapper.class);
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-02T00:00:00");

        assertThatThrownBy(() -> new MysqlBillingRollup(mock(TokenUsageMapper.class), bills).confirm("t", start, end))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.NOT_FOUND);
        verify(bills, never()).confirmDraft(any(), any(), any());
    }

    /** 已确认的账单开票后金额不变。 */
    @Test
    void invoiceKeepsConfirmedAmounts() {
        BillingRecordMapper bills = mock(BillingRecordMapper.class);
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-02T00:00:00");
        BillingRecord confirmed = new BillingRecord();
        confirmed.setStatus("CONFIRMED");
        confirmed.setTokenTotal(8L);
        confirmed.setCostAmount(new BigDecimal("1.250000"));
        confirmed.setCurrency("USD");
        when(bills.findByPeriod("t", start, end)).thenReturn(confirmed);
        when(bills.invoiceConfirmed("t", start, end)).thenReturn(1);

        BillingSummary summary = new MysqlBillingRollup(mock(TokenUsageMapper.class), bills).invoice("t", start, end);

        assertThat(summary.status()).isEqualTo("INVOICED");
        assertThat(summary.costAmount()).isEqualByComparingTo("1.250000");
        verify(bills, never()).confirmDraft(any(), any(), any());
    }

    /** 草稿不能直接开票。 */
    @Test
    void draftCannotBeInvoiced() {
        BillingRecordMapper bills = mock(BillingRecordMapper.class);
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-02T00:00:00");
        BillingRecord draft = new BillingRecord();
        draft.setStatus("DRAFT");
        when(bills.findByPeriod("t", start, end)).thenReturn(draft);

        assertThatThrownBy(() -> new MysqlBillingRollup(mock(TokenUsageMapper.class), bills).invoice("t", start, end))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(bills, never()).invoiceConfirmed(any(), any(), any());
    }

    /** 已开票的账单付款后金额不变。 */
    @Test
    void payKeepsInvoicedAmounts() {
        BillingRecordMapper bills = mock(BillingRecordMapper.class);
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-02T00:00:00");
        BillingRecord invoiced = new BillingRecord();
        invoiced.setStatus("INVOICED");
        invoiced.setTokenTotal(8L);
        invoiced.setCostAmount(new BigDecimal("1.250000"));
        invoiced.setCurrency("USD");
        when(bills.findByPeriod("t", start, end)).thenReturn(invoiced);
        when(bills.payInvoiced("t", start, end)).thenReturn(1);

        BillingSummary summary = new MysqlBillingRollup(mock(TokenUsageMapper.class), bills).pay("t", start, end);

        assertThat(summary.status()).isEqualTo("PAID");
        assertThat(summary.costAmount()).isEqualByComparingTo("1.250000");
        verify(bills, never()).invoiceConfirmed(any(), any(), any());
    }

    /** 已确认但还没开票的账单不能付款。 */
    @Test
    void confirmedCannotBePaid() {
        BillingRecordMapper bills = mock(BillingRecordMapper.class);
        LocalDateTime start = LocalDateTime.parse("2026-10-01T00:00:00");
        LocalDateTime end = LocalDateTime.parse("2026-10-02T00:00:00");
        BillingRecord confirmed = new BillingRecord();
        confirmed.setStatus("CONFIRMED");
        when(bills.findByPeriod("t", start, end)).thenReturn(confirmed);

        assertThatThrownBy(() -> new MysqlBillingRollup(mock(TokenUsageMapper.class), bills).pay("t", start, end))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).errorCode())
                .isEqualTo(ErrorCode.CONFLICT);
        verify(bills, never()).payInvoiced(any(), any(), any());
    }
}
