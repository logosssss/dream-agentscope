package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantQuotaRecord;
import org.apache.ibatis.annotations.Param;

/**
 * tenant_quota 的访问。登记用 BaseMapper 插入。按租户取每日请求上限在 XML 里。
 */
public interface TenantQuotaMapper extends BaseMapper<TenantQuotaRecord> {

    /**
     * 启用中的 REQUESTS / DAILY 上限。没有行时返回 null。
     */
    Long findDailyRequestLimit(@Param("tenantId") String tenantId);
}
