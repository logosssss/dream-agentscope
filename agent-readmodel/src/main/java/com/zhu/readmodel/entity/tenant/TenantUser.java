package com.zhu.readmodel.entity.tenant;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

/**
 * tenant_user 的一行。登记后默认可用。角色不在这次写入。
 */
@TableName("tenant_user")
public class TenantUser {

    /** 自增主键。对外不返回。 */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 已登记的租户。 */
    private String tenantId;

    /** 用户业务标识。 */
    private String userId;

    /** 显示名。可以空。 */
    private String displayName;

    /** ACTIVE 或 DISABLED。停用和恢复只改状态，不删行。 */
    private String status;

    /** 登记时间。对话鉴权不读。 */
    private LocalDateTime createdAt;

    /** 最近一次改动时间。对话鉴权不读。 */
    private LocalDateTime updatedAt;

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

    /** 显示名。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 写入显示名。 */
    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    /** 状态。 */
    public String getStatus() {
        return status;
    }

    /** 写入状态。 */
    public void setStatus(String status) {
        this.status = status;
    }

    /** 登记时间。 */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /** 写入登记时间。插入交给库默认值。 */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /** 最近一次改动时间。 */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /** 写入最近一次改动时间。更新交给库的 ON UPDATE。 */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
