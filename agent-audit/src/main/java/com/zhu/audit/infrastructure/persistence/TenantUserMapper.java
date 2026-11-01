package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantUser;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * tenant_user 的访问。登记用 BaseMapper 插入，查找、列表、停用和恢复在 XML 里。不在这里写角色。
 */
public interface TenantUserMapper extends BaseMapper<TenantUser> {

    /**
     * 按租户和用户取一行。没有时返回 null。
     */
    TenantUser findByTenantAndUser(@Param("tenantId") String tenantId, @Param("userId") String userId);

    /**
     * 只把仍为 ACTIVE 的用户改成 DISABLED。返回实际更新行数。
     */
    int disableActive(@Param("tenantId") String tenantId, @Param("userId") String userId);

    /**
     * 只把停用用户改回 ACTIVE。返回实际更新行数。
     */
    int restoreDisabled(@Param("tenantId") String tenantId, @Param("userId") String userId);

    /**
     * 某个租户最近登记的用户。调用方已经把条数收进 1 到 100。含停用。
     */
    List<TenantUser> recent(@Param("tenantId") String tenantId, @Param("limit") int limit);
}
