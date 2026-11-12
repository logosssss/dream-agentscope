package com.zhu.audit.infrastructure.persistence.tenant;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhu.audit.infrastructure.persistence.entity.tenant.LoginCredential;
import org.apache.ibatis.annotations.Param;

/**
 * login_credential 的访问。插入用 BaseMapper。查找在 XML 里。不读管理员。
 */
public interface LoginCredentialMapper extends BaseMapper<LoginCredential> {

    /**
     * 按租户和登录名取密码账号。没有时返回 null。
     */
    LoginCredential findByTenantAndLoginName(@Param("tenantId") String tenantId, @Param("loginName") String loginName);

    /**
     * 按 openid 取微信账号。没有时返回 null。
     */
    LoginCredential findByOpenId(@Param("openId") String openId);
}
