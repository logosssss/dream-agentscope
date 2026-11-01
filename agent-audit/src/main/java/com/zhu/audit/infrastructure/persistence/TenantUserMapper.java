package com.zhu.audit.infrastructure.persistence;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.TenantUser;

/**
 * tenant_user 的访问。登记用 BaseMapper 插入，不在这里写角色。
 */
public interface TenantUserMapper extends BaseMapper<TenantUser> {
}
