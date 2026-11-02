package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantQuotaRecord;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * tenant_quota 的访问。登记用 BaseMapper 插入。查找、列表和改上限在 XML 里。
 */
public interface TenantQuotaMapper extends BaseMapper<TenantQuotaRecord> {

    /**
     * 启用中的 REQUESTS / DAILY 上限。没有行时返回 null。
     */
    Long findDailyRequestLimit(@Param("tenantId") String tenantId);

    /**
     * 启用中的 TOKENS / DAILY 上限。没有行时返回 null。
     */
    Long findDailyTokenLimit(@Param("tenantId") String tenantId);

    /**
     * 启用中的 COST / DAILY 上限（微单位）。没有行时返回 null。
     */
    Long findDailyCostLimit(@Param("tenantId") String tenantId);

    /**
     * 只改已有 REQUESTS / DAILY 的上限。返回实际更新行数。
     */
    int updateDailyRequestLimit(@Param("tenantId") String tenantId, @Param("limit") Long limit);

    /**
     * 只改已有 TOKENS / DAILY 的上限。返回实际更新行数。
     */
    int updateDailyTokenLimit(@Param("tenantId") String tenantId, @Param("limit") Long limit);

    /**
     * 只改已有 COST / DAILY 的上限。返回实际更新行数。
     */
    int updateDailyCostLimit(@Param("tenantId") String tenantId, @Param("limit") Long limit);

    /**
     * 最近登记的每日请求上限。调用方已经把条数收进 1 到 100。不读已用量。
     */
    List<TenantQuotaRecord> recent(@Param("limit") int limit);

    /**
     * 最近登记的每日 token 上限。
     */
    List<TenantQuotaRecord> recentTokens(@Param("limit") int limit);

    /**
     * 最近登记的每日费用上限。
     */
    List<TenantQuotaRecord> recentCosts(@Param("limit") int limit);
}
