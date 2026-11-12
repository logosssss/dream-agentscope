package com.zhu.audit.infrastructure.persistence.quota;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.quota.QuotaUsageLog;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * quota_usage_log 的访问。追加用 BaseMapper 插入。按租户取最近几行在 XML 里。
 */
public interface QuotaUsageLogMapper extends BaseMapper<QuotaUsageLog> {

    /**
     * 某一租户最近的流水。调用方已经把条数收进 1 到 100。
     */
    List<QuotaUsageLog> recent(@Param("tenantId") String tenantId, @Param("limit") int limit);
}
