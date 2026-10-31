package com.zhu.audit.application;

import com.zhu.audit.api.BillingRollup;
import com.zhu.audit.api.BillingSummary;
import com.zhu.audit.infrastructure.persistence.BillingRecordMapper;
import com.zhu.audit.infrastructure.persistence.TokenUsageMapper;
import com.zhu.audit.infrastructure.persistence.UsagePeriodTotal;
import com.zhu.audit.infrastructure.persistence.entity.BillingRecord;
import com.zhu.common.exception.BusinessException;
import com.zhu.common.exception.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * 从 token_usage 生成一张草稿账单。只在 MySQL 打开时创建。
 */
@Service
@ConditionalOnProperty(name = "agentscope.distributed.mysql.enabled", havingValue = "true")
public class MysqlBillingRollup implements BillingRollup {

    private static final String DRAFT = "DRAFT";

    private static final String CONFIRMED = "CONFIRMED";

    private static final String INVOICED = "INVOICED";

    private final TokenUsageMapper usages;

    private final BillingRecordMapper bills;

    /** 合计和插入分成两个 Mapper。重复区间由唯一键拒绝。 */
    public MysqlBillingRollup(TokenUsageMapper usages, BillingRecordMapper bills) {
        this.usages = usages;
        this.bills = bills;
    }

    /**
     * 租户为空、终点不晚于起点、或跨度超过 366 天时拒绝。
     * 没有用量也写一张 0 金额的草稿。同一区间再生成一次按冲突处理。
     */
    @Override
    public BillingSummary draft(String tenantId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        if (tenantId == null || tenantId.isBlank() || periodStart == null || periodEnd == null
                || !periodEnd.isAfter(periodStart) || periodEnd.isAfter(periodStart.plusDays(366))) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "billing period is invalid");
        }
        UsagePeriodTotal total = usages.sumForPeriod(tenantId, periodStart, periodEnd);
        int currencies = total == null || total.getCurrencyCount() == null ? 0 : total.getCurrencyCount();
        if (currencies > 1) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "billing period has mixed currencies");
        }
        long tokens = total == null || total.getTokenTotal() == null ? 0L : total.getTokenTotal();
        BigDecimal cost = total == null || total.getCostAmount() == null
                ? BigDecimal.ZERO
                : total.getCostAmount().setScale(6, RoundingMode.HALF_UP);
        String currency = currencies == 0 || total.getCurrency() == null || total.getCurrency().isBlank()
                ? "CNY"
                : total.getCurrency();
        BillingRecord row = new BillingRecord();
        row.setTenantId(tenantId);
        row.setPeriodStart(periodStart);
        row.setPeriodEnd(periodEnd);
        row.setTokenTotal(tokens);
        row.setCostAmount(cost);
        row.setCurrency(currency);
        row.setStatus(DRAFT);
        try {
            bills.insert(row);
        } catch (DataIntegrityViolationException ex) {
            throw new BusinessException(ErrorCode.CONFLICT, "billing period already exists");
        }
        return new BillingSummary(tenantId, periodStart, periodEnd, tokens, cost, currency, DRAFT);
    }

    /**
     * 租户或区间为空时拒绝。确认只改状态，金额保持草稿当时的合计。
     * 读到草稿但更新没抢到行，按冲突处理，避免覆盖别人刚改过的状态。
     */
    @Override
    public BillingSummary confirm(String tenantId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        if (tenantId == null || tenantId.isBlank() || periodStart == null || periodEnd == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "billing period is invalid");
        }
        BillingRecord existing = bills.findByPeriod(tenantId, periodStart, periodEnd);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing record not found");
        }
        if (!DRAFT.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "billing record is not a draft");
        }
        int updated = bills.confirmDraft(tenantId, periodStart, periodEnd);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "billing record is not a draft");
        }
        return summary(tenantId, periodStart, periodEnd, existing, CONFIRMED);
    }

    /**
     * 租户或区间为空时拒绝。只有已确认的账单能开票，草稿要先确认。
     * 不重算金额。更新没抢到行时按冲突处理。
     */
    @Override
    public BillingSummary invoice(String tenantId, LocalDateTime periodStart, LocalDateTime periodEnd) {
        if (tenantId == null || tenantId.isBlank() || periodStart == null || periodEnd == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "billing period is invalid");
        }
        BillingRecord existing = bills.findByPeriod(tenantId, periodStart, periodEnd);
        if (existing == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "billing record not found");
        }
        if (!CONFIRMED.equals(existing.getStatus())) {
            throw new BusinessException(ErrorCode.CONFLICT, "billing record is not confirmed");
        }
        int updated = bills.invoiceConfirmed(tenantId, periodStart, periodEnd);
        if (updated != 1) {
            throw new BusinessException(ErrorCode.CONFLICT, "billing record is not confirmed");
        }
        return summary(tenantId, periodStart, periodEnd, existing, INVOICED);
    }

    /** 金额保持原行。空金额记 0，空币种记 CNY。 */
    private static BillingSummary summary(
            String tenantId,
            LocalDateTime periodStart,
            LocalDateTime periodEnd,
            BillingRecord existing,
            String status) {
        long tokens = existing.getTokenTotal() == null ? 0L : existing.getTokenTotal();
        BigDecimal cost = existing.getCostAmount() == null ? BigDecimal.ZERO : existing.getCostAmount();
        String currency = existing.getCurrency() == null || existing.getCurrency().isBlank() ? "CNY" : existing.getCurrency();
        return new BillingSummary(tenantId, periodStart, periodEnd, tokens, cost, currency, status);
    }
}
