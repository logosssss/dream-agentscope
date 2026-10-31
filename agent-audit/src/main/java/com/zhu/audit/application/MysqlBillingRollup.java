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
}
