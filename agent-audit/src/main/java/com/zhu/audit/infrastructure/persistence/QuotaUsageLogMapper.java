package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.QuotaUsageLog;

/**
 * quota_usage_log 的访问。追加用 BaseMapper 插入，不在这里查询。
 */
public interface QuotaUsageLogMapper extends BaseMapper<QuotaUsageLog> {
}
