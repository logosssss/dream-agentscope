package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantRecord;
import org.apache.ibatis.annotations.Param;

/**
 * tenant 的访问。登记用 BaseMapper 插入。按标识查找在 XML 里。
 */
public interface TenantMapper extends BaseMapper<TenantRecord> {

    /**
     * 按业务标识取一行。没有时返回 null。
     */
    TenantRecord findByTenantId(@Param("tenantId") String tenantId);
}
