package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantUser;
import org.apache.ibatis.annotations.Param;

/**
 * tenant_user 的访问。登记用 BaseMapper 插入，查找在 XML 里。不在这里写角色。
 */
public interface TenantUserMapper extends BaseMapper<TenantUser> {

    /**
     * 按租户和用户取一行。没有时返回 null。
     */
    TenantUser findByTenantAndUser(@Param("tenantId") String tenantId, @Param("userId") String userId);
}
