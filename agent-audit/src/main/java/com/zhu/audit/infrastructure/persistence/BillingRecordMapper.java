package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.BillingRecord;
import java.time.LocalDateTime;
import org.apache.ibatis.annotations.Param;

/**
 * billing_record 的访问。草稿用 BaseMapper 插入。按区间查找和确认在 XML 里。
 */
public interface BillingRecordMapper extends BaseMapper<BillingRecord> {

    /**
     * 按租户和区间取一张账单。没有时返回 null。
     */
    BillingRecord findByPeriod(
            @Param("tenantId") String tenantId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    /**
     * 只把仍为 DRAFT 的账单改成 CONFIRMED。返回实际更新行数。
     */
    int confirmDraft(
            @Param("tenantId") String tenantId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);

    /**
     * 只把仍为 CONFIRMED 的账单改成 INVOICED。返回实际更新行数。
     */
    int invoiceConfirmed(
            @Param("tenantId") String tenantId,
            @Param("periodStart") LocalDateTime periodStart,
            @Param("periodEnd") LocalDateTime periodEnd);
}
