package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantRecord;
import org.apache.ibatis.annotations.Param;

/**
 * tenant 的访问。登记用 BaseMapper 插入。查找、停用、关闭和恢复在 XML 里。
 */
public interface TenantMapper extends BaseMapper<TenantRecord> {

    /**
     * 按业务标识取一行。没有时返回 null。
     */
    TenantRecord findByTenantId(@Param("tenantId") String tenantId);

    /**
     * 只把仍为 ACTIVE 的租户改成 SUSPENDED。返回实际更新行数。
     */
    int suspendActive(@Param("tenantId") String tenantId);

    /**
     * 只把尚未关闭的租户改成 CLOSED。返回实际更新行数。
     */
    int closeNotClosed(@Param("tenantId") String tenantId);

    /**
     * 只把停用或关闭的租户改回 ACTIVE。返回实际更新行数。
     */
    int restoreInactive(@Param("tenantId") String tenantId);
}
