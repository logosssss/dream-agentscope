package com.zhu.audit.infrastructure.persistence.entity.tenant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * login_credential 的一行。密码账号和微信账号不同时填写。
 */
@TableName("login_credential")
public class LoginCredential {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 已登记的租户。 */
    private String tenantId;

    /** 对应 tenant_user 的业务标识。 */
    private String userId;

    /** 密码登录名。微信账号为空。 */
    private String loginName;

    /** 十六进制盐。微信账号为空。 */
    private String passwordSalt;

    /** 密码哈希。微信账号为空。 */
    private String passwordHash;

    /** 微信 openid。密码账号为空。 */
    private String openId;

    /** 主键。 */
    public Long getId() {
        return id;
    }

    /** 写入主键。 */
    public void setId(Long id) {
        this.id = id;
    }

    /** 租户标识。 */
    public String getTenantId() {
        return tenantId;
    }

    /** 写入租户标识。 */
    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    /** 用户标识。 */
    public String getUserId() {
        return userId;
    }

    /** 写入用户标识。 */
    public void setUserId(String userId) {
        this.userId = userId;
    }

    /** 登录名。 */
    public String getLoginName() {
        return loginName;
    }

    /** 写入登录名。 */
    public void setLoginName(String loginName) {
        this.loginName = loginName;
    }

    /** 盐。 */
    public String getPasswordSalt() {
        return passwordSalt;
    }

    /** 写入盐。 */
    public void setPasswordSalt(String passwordSalt) {
        this.passwordSalt = passwordSalt;
    }

    /** 密码哈希。 */
    public String getPasswordHash() {
        return passwordHash;
    }

    /** 写入密码哈希。 */
    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** openid。 */
    public String getOpenId() {
        return openId;
    }

    /** 写入 openid。 */
    public void setOpenId(String openId) {
        this.openId = openId;
    }
}
