package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantQuotaRecord;
import org.apache.ibatis.annotations.Param;

/**
 * tenant_quota 的访问。登记用 BaseMapper 插入。查找和改上限在 XML 里。
 */
public interface TenantQuotaMapper extends BaseMapper<TenantQuotaRecord> {

    /**
     * 启用中的 REQUESTS / DAILY 上限。没有行时返回 null。
     */
    Long findDailyRequestLimit(@Param("tenantId") String tenantId);

    /**
     * 只改已有 REQUESTS / DAILY 的上限。返回实际更新行数。
     */
    int updateDailyRequestLimit(@Param("tenantId") String tenantId, @Param("limit") Long limit);
}
