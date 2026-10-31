package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.BillingRecord;

/**
 * billing_record 的访问。草稿用 BaseMapper 插入，Java 不写 SQL。
 */
public interface BillingRecordMapper extends BaseMapper<BillingRecord> {
}
